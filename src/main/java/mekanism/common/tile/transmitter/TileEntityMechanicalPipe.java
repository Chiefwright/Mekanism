package mekanism.common.tile.transmitter;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import mekanism.api.Action;
import mekanism.api.NBTConstants;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.api.inventory.AutomationType;
import mekanism.api.providers.IBlockProvider;
import mekanism.api.tier.AlloyTier;
import mekanism.api.tier.BaseTier;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.states.BlockStateHelper;
import mekanism.common.block.states.TransmitterType;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.proxy.ProxyFluidHandler;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tier.PipeTier;
import mekanism.common.transmitters.grid.FluidNetwork;
import mekanism.common.upgrade.transmitter.MechanicalPipeUpgradeData;
import mekanism.common.upgrade.transmitter.TransmitterUpgradeData;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.PipeUtils;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class TileEntityMechanicalPipe extends TileEntityTransmitter<IFluidHandler, FluidNetwork, FluidStack> implements IMekanismFluidHandler {

    public final PipeTier tier;

    @Nonnull
    public FluidStack lastWrite = FluidStack.EMPTY;
    private ProxyFluidHandler readOnlyHandler;
    private final Map<Direction, ProxyFluidHandler> fluidHandlers;
    private final List<IExtendedFluidTank> tanks;
    public BasicFluidTank buffer;

    public TileEntityMechanicalPipe(IBlockProvider blockProvider) {
        super(blockProvider);
        this.tier = Attribute.getTier(blockProvider.getBlock(), PipeTier.class);
        fluidHandlers = new EnumMap<>(Direction.class);
        buffer = BasicFluidTank.create(getCapacity(), BasicFluidTank.alwaysFalse, BasicFluidTank.alwaysTrue, this);
        tanks = Collections.singletonList(buffer);
    }

    /**
     * Lazily get and cache an IFluidHandler instance for the given side, and make it be read only if something else is trying to interact with us using the null side
     */
    private IFluidHandler getFluidHandler(@Nullable Direction side) {
        if (side == null) {
            if (readOnlyHandler == null) {
                readOnlyHandler = new ProxyFluidHandler(this, null, null);
            }
            return readOnlyHandler;
        }
        ProxyFluidHandler fluidHandler = fluidHandlers.get(side);
        if (fluidHandler == null) {
            fluidHandlers.put(side, fluidHandler = new ProxyFluidHandler(this, side, null));
        }
        return fluidHandler;
    }

    @Override
    public void tick() {
        if (!isRemote()) {
            Set<Direction> connections = getConnections(ConnectionType.PULL);
            if (!connections.isEmpty()) {
                for (IFluidHandler connectedAcceptor : PipeUtils.getConnectedAcceptors(getPos(), getWorld(), connections)) {
                    if (connectedAcceptor != null) {
                        FluidStack received;
                        //Note: We recheck the buffer each time in case we ended up accepting fluid somewhere
                        // and our buffer changed and is no longer empty
                        FluidStack bufferWithFallback = getBufferWithFallback();
                        if (bufferWithFallback.isEmpty()) {
                            //If we don't have a fluid stored try pulling as much as we are able to
                            received = connectedAcceptor.drain(getAvailablePull(), FluidAction.SIMULATE);
                        } else {
                            //Otherwise try draining the same type of fluid we have stored requesting up to as much as we are able to pull
                            // We do this to better support multiple tanks in case the fluid we have stored we could pull out of a block's
                            // second tank but just asking to drain a specific amount
                            received = connectedAcceptor.drain(new FluidStack(bufferWithFallback, getAvailablePull()), FluidAction.SIMULATE);
                        }
                        if (!received.isEmpty() && takeFluid(received, Action.SIMULATE).isEmpty()) {
                            FluidStack remainder = takeFluid(received, Action.EXECUTE);
                            connectedAcceptor.drain(new FluidStack(received, received.getAmount() - remainder.getAmount()), FluidAction.EXECUTE);
                        }
                    }
                }
            }
        }
        super.tick();
    }

    private int getAvailablePull() {
        if (getTransmitter().hasTransmitterNetwork()) {
            return Math.min(tier.getPipePullAmount(), getTransmitter().getTransmitterNetwork().fluidTank.getNeeded());
        }
        return Math.min(tier.getPipePullAmount(), buffer.getNeeded());
    }

    @Nonnull
    @Override
    public FluidStack insertFluid(int tank, @Nonnull FluidStack stack, @Nullable Direction side, @Nonnull Action action) {
        IExtendedFluidTank fluidTank = getFluidTank(tank, side);
        if (fluidTank == null) {
            return stack;
        } else if (side == null) {
            return fluidTank.insert(stack, action, AutomationType.INTERNAL);
        }
        //If we have a side only allow inserting if our connection allows it
        ConnectionType connectionType = getConnectionType(side);
        if (connectionType == ConnectionType.NORMAL || connectionType == ConnectionType.PULL) {
            return fluidTank.insert(stack, action, AutomationType.EXTERNAL);
        }
        return stack;
    }

    @Override
    public void read(CompoundNBT nbtTags) {
        super.read(nbtTags);
        if (nbtTags.contains(NBTConstants.FLUID_STORED, NBT.TAG_COMPOUND)) {
            lastWrite = FluidStack.loadFluidStackFromNBT(nbtTags.getCompound(NBTConstants.FLUID_STORED));
        } else {
            lastWrite = FluidStack.EMPTY;
        }
        buffer.setStack(lastWrite);
    }

    @Nonnull
    @Override
    public CompoundNBT write(CompoundNBT nbtTags) {
        super.write(nbtTags);
        if (lastWrite.isEmpty()) {
            nbtTags.remove(NBTConstants.FLUID_STORED);
        } else {
            nbtTags.put(NBTConstants.FLUID_STORED, lastWrite.writeToNBT(new CompoundNBT()));
        }
        return nbtTags;
    }

    @Override
    public TransmissionType getTransmissionType() {
        return TransmissionType.FLUID;
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.MECHANICAL_PIPE;
    }

    @Override
    public boolean isValidAcceptor(TileEntity acceptor, Direction side) {
        return PipeUtils.isValidAcceptorOnSide(acceptor, side);
    }

    @Override
    public boolean isValidTransmitter(TileEntity tile) {
        if (!super.isValidTransmitter(tile)) {
            return false;
        }
        if (!(tile instanceof TileEntityMechanicalPipe)) {
            return true;
        }
        FluidStack buffer = getBufferWithFallback();
        FluidStack otherBuffer = ((TileEntityMechanicalPipe) tile).getBufferWithFallback();
        return buffer.isEmpty() || otherBuffer.isEmpty() || buffer.isFluidEqual(otherBuffer);
    }

    @Override
    public FluidNetwork createNewNetwork() {
        return new FluidNetwork();
    }

    @Override
    public FluidNetwork createNetworkByMerging(Collection<FluidNetwork> networks) {
        return new FluidNetwork(networks);
    }

    @Override
    protected boolean canHaveIncompatibleNetworks() {
        return true;
    }

    @Override
    public int getCapacity() {
        return tier.getPipeCapacity();
    }

    @Nonnull
    @Override
    public FluidStack getBuffer() {
        return buffer.getFluid();
    }

    @Override
    public boolean noBufferOrFallback() {
        return getBufferWithFallback().isEmpty();
    }

    @Nonnull
    @Override
    public FluidStack getBufferWithFallback() {
        FluidStack buffer = getBuffer();
        //If we don't have a buffer try falling back to the network's buffer
        if (buffer.isEmpty() && getTransmitter().hasTransmitterNetwork()) {
            return getTransmitter().getTransmitterNetwork().getBuffer();
        }
        return buffer;
    }

    @Override
    public void takeShare() {
        if (getTransmitter().hasTransmitterNetwork()) {
            FluidNetwork network = getTransmitter().getTransmitterNetwork();
            if (!network.fluidTank.isEmpty() && !lastWrite.isEmpty()) {
                int amount = lastWrite.getAmount();
                if (network.fluidTank.shrinkStack(amount, Action.EXECUTE) != amount) {
                    //TODO: Print warning/error
                }
                buffer.setStack(lastWrite);
            }
        }
    }

    @Nonnull
    @Override
    public List<IExtendedFluidTank> getFluidTanks(@Nullable Direction side) {
        if (getTransmitter().hasTransmitterNetwork()) {
            //TODO: Do we want this to fallback to local if the one on the network is empty?
            return getTransmitter().getTransmitterNetwork().getFluidTanks(side);
        }
        return tanks;
    }

    @Override
    public void onContentsChanged() {
        markDirty();
    }

    @Override
    public IFluidHandler getCachedAcceptor(Direction side) {
        return MekanismUtils.toOptional(CapabilityUtils.getCapability(getCachedTile(side), CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite())).orElse(null);
    }

    /**
     * @return remainder
     */
    @Nonnull
    private FluidStack takeFluid(@Nonnull FluidStack fluid, Action action) {
        if (getTransmitter().hasTransmitterNetwork()) {
            return getTransmitter().getTransmitterNetwork().fluidTank.insert(fluid, action, AutomationType.INTERNAL);
        }
        return buffer.insert(fluid, action, AutomationType.INTERNAL);
    }

    @Override
    protected boolean canUpgrade(AlloyTier alloyTier) {
        return alloyTier.getBaseTier().ordinal() == tier.getBaseTier().ordinal() + 1;
    }

    @Nonnull
    @Override
    protected BlockState upgradeResult(@Nonnull BlockState current, @Nonnull BaseTier tier) {
        switch (tier) {
            case BASIC:
                return BlockStateHelper.copyStateData(current, MekanismBlocks.BASIC_MECHANICAL_PIPE.getBlock().getDefaultState());
            case ADVANCED:
                return BlockStateHelper.copyStateData(current, MekanismBlocks.ADVANCED_MECHANICAL_PIPE.getBlock().getDefaultState());
            case ELITE:
                return BlockStateHelper.copyStateData(current, MekanismBlocks.ELITE_MECHANICAL_PIPE.getBlock().getDefaultState());
            case ULTIMATE:
                return BlockStateHelper.copyStateData(current, MekanismBlocks.ULTIMATE_MECHANICAL_PIPE.getBlock().getDefaultState());
        }
        return current;
    }

    @Nullable
    @Override
    protected MechanicalPipeUpgradeData getUpgradeData() {
        return new MechanicalPipeUpgradeData(redstoneReactive, connectionTypes, getBuffer());
    }

    @Override
    protected void parseUpgradeData(@Nonnull TransmitterUpgradeData upgradeData) {
        if (upgradeData instanceof MechanicalPipeUpgradeData) {
            MechanicalPipeUpgradeData data = (MechanicalPipeUpgradeData) upgradeData;
            redstoneReactive = data.redstoneReactive;
            connectionTypes = data.connectionTypes;
            takeFluid(data.contents, Action.EXECUTE);
        } else {
            super.parseUpgradeData(upgradeData);
        }
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            List<IExtendedFluidTank> fluidTanks = getFluidTanks(side);
            //Don't return a fluid handler if we don't actually even have any tanks for that side
            //TODO: Should we actually return the fluid handler regardless??? And then just everything fails?
            LazyOptional<IFluidHandler> lazyFluidHandler = fluidTanks.isEmpty() ? LazyOptional.empty() : LazyOptional.of(() -> getFluidHandler(side));
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.orEmpty(capability, lazyFluidHandler);
        }
        return super.getCapability(capability, side);
    }
}