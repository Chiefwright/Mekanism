package mekanism.generators.common.tile;

import java.util.Arrays;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import mekanism.api.Action;
import mekanism.api.IHeatTransfer;
import mekanism.api.RelativeSide;
import mekanism.api.inventory.AutomationType;
import mekanism.api.math.FloatingLong;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableDouble;
import mekanism.common.inventory.container.sync.SyncableFloatingLong;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.HeatUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.common.config.MekanismGeneratorsConfig;
import mekanism.generators.common.registries.GeneratorsBlocks;
import mekanism.generators.common.slot.FluidFuelInventorySlot;
import net.minecraft.fluid.Fluids;
import net.minecraft.tags.FluidTags;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;

public class TileEntityHeatGenerator extends TileEntityGenerator implements IHeatTransfer {

    private static final int MAX_FLUID = 24_000;
    private static final int FLUID_RATE = 10;
    /**
     * The FluidTank for this generator.
     */
    public BasicFluidTank lavaTank;
    private double temperature = 0;
    private double thermalEfficiency = 0.5D;
    private double invHeatCapacity = 1;
    private double heatToAbsorb = 0;
    private FloatingLong producingEnergy = FloatingLong.ZERO;
    private double lastTransferLoss;
    private double lastEnvironmentLoss;

    private FluidFuelInventorySlot fuelSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityHeatGenerator() {
        super(GeneratorsBlocks.HEAT_GENERATOR, MekanismGeneratorsConfig.generators.heatGeneration.get().multiply(2));
    }

    @Nonnull
    @Override
    protected IFluidTankHolder getInitialFluidTanks() {
        FluidTankHelper builder = FluidTankHelper.forSide(this::getDirection);
        builder.addTank(lavaTank = BasicFluidTank.create(MAX_FLUID, fluidStack -> fluidStack.getFluid().isIn(FluidTags.LAVA), this), RelativeSide.LEFT,
              RelativeSide.RIGHT, RelativeSide.BACK, RelativeSide.TOP, RelativeSide.BOTTOM);
        return builder.build();
    }

    @Nonnull
    @Override
    protected IInventorySlotHolder getInitialInventory() {
        InventorySlotHelper builder = InventorySlotHelper.forSide(this::getDirection);
        //Divide the burn time by 20 as that is the ratio of how much a bucket of lava would burn for
        // Eventually we may want to grab the 20 dynamically in case some mod is changing the burn time of a lava bucket
        builder.addSlot(fuelSlot = FluidFuelInventorySlot.forFuel(lavaTank, stack -> ForgeHooks.getBurnTime(stack) / 20, size -> new FluidStack(Fluids.LAVA, size),
              this, 17, 35), RelativeSide.FRONT, RelativeSide.LEFT, RelativeSide.BACK, RelativeSide.TOP, RelativeSide.BOTTOM);
        builder.addSlot(energySlot = EnergyInventorySlot.drain(getEnergyContainer(), this, 143, 35), RelativeSide.RIGHT);
        return builder.build();
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        energySlot.drainContainer();
        fuelSlot.fillOrBurn();
        FloatingLong prev = getEnergyContainer().getEnergy();
        transferHeatTo(getBoost());
        if (MekanismUtils.canFunction(this) && !getEnergyContainer().getNeeded().isEmpty() &&
            lavaTank.extract(FLUID_RATE, Action.SIMULATE, AutomationType.INTERNAL).getAmount() == FLUID_RATE) {
            setActive(true);
            lavaTank.extract(FLUID_RATE, Action.EXECUTE, AutomationType.INTERNAL);
            transferHeatTo(MekanismGeneratorsConfig.generators.heatGeneration.get());
        } else {
            setActive(false);
        }
        double[] loss = simulateHeat();
        applyTemperatureChange();
        lastTransferLoss = loss[0];
        lastEnvironmentLoss = loss[1];
        producingEnergy = getEnergyContainer().getEnergy().subtract(prev);
    }

    private FloatingLong getBoost() {
        if (world == null) {
            return FloatingLong.ZERO;
        }
        //Lava boost
        FloatingLong boost = MekanismGeneratorsConfig.generators.heatGenerationLava.get().multiply(Arrays.stream(EnumUtils.DIRECTIONS)
              .filter(side -> world.getFluidState(pos.offset(side)).isTagged(FluidTags.LAVA)).count());
        if (world.getDimension().isNether()) {
            boost.plusEqual(MekanismGeneratorsConfig.generators.heatGenerationNether.get());
        }
        return boost;
    }

    @Override
    public double getTemp() {
        return temperature;
    }

    @Override
    public double getInverseConductionCoefficient() {
        return 1;
    }

    @Override
    public double getInsulationCoefficient(Direction side) {
        return side == Direction.DOWN ? 0 : 10_000;
    }

    private void transferHeatTo(FloatingLong heat) {
        transferHeatTo(heat.doubleValue());
    }

    @Override
    public void transferHeatTo(double heat) {
        heatToAbsorb += heat;
    }

    @Override
    public double[] simulateHeat() {
        double temp = getTemp();
        if (temp > 0) {
            double carnotEfficiency = temp / (temp + IHeatTransfer.AMBIENT_TEMP);
            double heatLost = thermalEfficiency * temp;
            double workDone = heatLost * carnotEfficiency;
            transferHeatTo(-heatLost);
            getEnergyContainer().insert(FloatingLong.create(workDone), Action.EXECUTE, AutomationType.INTERNAL);
        }
        return HeatUtils.simulate(this);
    }

    @Override
    public double applyTemperatureChange() {
        temperature += invHeatCapacity * heatToAbsorb;
        heatToAbsorb = 0;
        return temperature;
    }

    @Nullable
    @Override
    public IHeatTransfer getAdjacent(Direction side) {
        if (side == Direction.DOWN) {
            TileEntity adj = MekanismUtils.getTileEntity(getWorld(), pos.down());
            Optional<IHeatTransfer> capability = MekanismUtils.toOptional(CapabilityUtils.getCapability(adj, Capabilities.HEAT_TRANSFER_CAPABILITY, side.getOpposite()));
            if (capability.isPresent()) {
                return capability.get();
            }
        }
        return null;
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, @Nullable Direction side) {
        if (capability == Capabilities.HEAT_TRANSFER_CAPABILITY && side == Direction.DOWN) {
            return true;
        }
        return super.isCapabilityDisabled(capability, side);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapabilityIfEnabled(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (capability == Capabilities.HEAT_TRANSFER_CAPABILITY) {
            return Capabilities.HEAT_TRANSFER_CAPABILITY.orEmpty(capability, LazyOptional.of(() -> this));
        }
        return super.getCapabilityIfEnabled(capability, side);
    }

    public FloatingLong getProducingEnergy() {
        return producingEnergy;
    }

    public double getLastTransferLoss() {
        return lastTransferLoss;
    }

    public double getLastEnvironmentLoss() {
        return lastEnvironmentLoss;
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(lavaTank.getFluidAmount(), lavaTank.getCapacity());
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableFloatingLong.create(this::getProducingEnergy, value -> producingEnergy = value));
        container.track(SyncableDouble.create(this::getLastTransferLoss, value -> lastTransferLoss = value));
        container.track(SyncableDouble.create(this::getLastEnvironmentLoss, value -> lastEnvironmentLoss = value));
    }
}