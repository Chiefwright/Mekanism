package mekanism.client.jei;

import mekanism.common.Mekanism;
import mekanism.common.MekanismBlock;
import mekanism.common.base.FactoryType;
import mekanism.common.integration.crafttweaker.handlers.EnergizedSmelter;
import mekanism.common.tier.FactoryTier;
import mezz.jei.api.constants.VanillaRecipeCategoryUid;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import net.minecraft.util.ResourceLocation;

public class CatalystRegistryHelper {

    public static boolean register(IRecipeCatalystRegistration registry, MekanismBlock mekanismBlock) {
        return register(registry, mekanismBlock, mekanismBlock.getJEICategory());
    }

    public static boolean register(IRecipeCatalystRegistration registry, MekanismBlock mekanismBlock, ResourceLocation resourceLocation) {
        if (!mekanismBlock.isEnabled()) {
            return false;
        }
        registerRecipeItem(registry, mekanismBlock, resourceLocation);
        return true;
    }

    public static void registerCondensentrator(IRecipeCatalystRegistration registry) {
        MekanismBlock mekanismBlock = MekanismBlock.ROTARY_CONDENSENTRATOR;
        if (!mekanismBlock.isEnabled()) {
            return;
        }
        ResourceLocation condensentrating = new ResourceLocation(Mekanism.MODID, "rotary_condensentrator_condensentrating");
        ResourceLocation decondensentrating = new ResourceLocation(Mekanism.MODID, "rotary_condensentrator_decondensentrating");
        registry.addRecipeCatalyst(mekanismBlock.getItemStack(), condensentrating, decondensentrating);
    }

    public static void registerSmelter(IRecipeCatalystRegistration registry) {
        if (!register(registry, MekanismBlock.ENERGIZED_SMELTER)) {
            return;
        }
        if (!Mekanism.hooks.CraftTweakerLoaded || !EnergizedSmelter.hasRemovedRecipe()) {
            //Vanilla catalyst
            registerRecipeItem(registry, MekanismBlock.ENERGIZED_SMELTER, VanillaRecipeCategoryUid.FURNACE);
        }
    }

    private static void registerRecipeItem(IRecipeCatalystRegistration registry, MekanismBlock mekanismBlock, ResourceLocation category) {
        registry.addRecipeCatalyst(mekanismBlock.getItemStack(), category);
        FactoryType factoryType = mekanismBlock.getFactoryType();
        if (factoryType != null) {
            FactoryTier.forEnabled(tier -> registry.addRecipeCatalyst(MekanismBlock.getFactory(tier, factoryType).getItemStack(), category));
        }
    }
}