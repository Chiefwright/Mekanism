package mekanism.client.render.transmitter;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.matrix.MatrixStack.Entry;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.ParametersAreNonnullByDefault;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.obj.ContentsModelConfiguration;
import mekanism.client.render.obj.VisibleModelConfiguration;
import mekanism.client.render.tileentity.MekanismTileEntityRenderer;
import mekanism.common.config.MekanismConfig;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemOverrideList;
import net.minecraft.client.renderer.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModelConfiguration;
import net.minecraftforge.client.model.ModelLoader;

@ParametersAreNonnullByDefault
public abstract class RenderTransmitterBase<T extends TileEntityTransmitter<?, ?, ?>> extends MekanismTileEntityRenderer<T> {

    public static final ResourceLocation MODEL_LOCATION = MekanismUtils.getResource(ResourceType.MODEL, "transmitter_contents.obj");
    private static final IModelConfiguration contentsConfiguration = new ContentsModelConfiguration();

    protected RenderTransmitterBase(TileEntityRendererDispatcher renderer) {
        super(renderer);
    }

    protected void renderModel(T transmitter, MatrixStack matrix, IVertexBuilder builder, int rgb, float alpha, int light, int overlayLight, TextureAtlasSprite icon) {
        renderModel(transmitter, matrix, builder, MekanismRenderer.getRed(rgb), MekanismRenderer.getGreen(rgb), MekanismRenderer.getBlue(rgb), alpha, light, overlayLight, icon,
              Arrays.stream(EnumUtils.DIRECTIONS).map(side -> side.getName() + transmitter.getConnectionType(side).getName().toUpperCase()).collect(Collectors.toList()));
    }

    protected void renderModel(T transmitter, MatrixStack matrix, IVertexBuilder builder, float red, float green, float blue, float alpha, int light, int overlayLight,
          TextureAtlasSprite icon, List<String> visible) {
        if (!visible.isEmpty()) {
            //TODO: Can we somehow cache any of this method
            IBakedModel bakedModel = MekanismRenderer.contentsModel.bake(new VisibleModelConfiguration(contentsConfiguration, visible), ModelLoader.instance(),
                  material -> icon, ModelRotation.X0_Y0, ItemOverrideList.EMPTY, MODEL_LOCATION);
            Entry entry = matrix.getLast();
            //Get all the sides
            for (BakedQuad quad : bakedModel.getQuads(transmitter.getBlockState(), null, Minecraft.getInstance().world.getRandom(), transmitter.getModelData())) {
                builder.addVertexData(entry, quad, red, green, blue, alpha, light, overlayLight);
            }
        }
    }

    @Override
    public void render(T transmitter, float partialTick, MatrixStack matrix, IRenderTypeBuffer renderer, int light, int overlayLight) {
        if (!MekanismConfig.client.opaqueTransmitters.get()) {
            super.render(transmitter, partialTick, matrix, renderer, light, overlayLight);
        }
    }
}