package mekanism.client.gui.element;

import mekanism.client.gui.IGuiWrapper;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;

//TODO: Should this extend MekanismImageButton
public class GuiDumpButton<TILE extends TileEntityMekanism> extends GuiTexturedElement {

    private final Runnable onPress;
    protected final TILE tile;

    //TODO: Make it some tile that implements a dumping interface
    public GuiDumpButton(IGuiWrapper gui, TILE tile, int x, int y, Runnable onPress) {
        super(MekanismUtils.getResource(ResourceType.GUI, "dump.png"), gui, x, y, 21, 10);
        this.tile = tile;
        this.onPress = onPress;
        playClickSound = true;
    }

    @Override
    public void renderButton(int mouseX, int mouseY, float partialTicks) {
        minecraft.textureManager.bindTexture(getResource());
        blit(x, y, 0, 0, width, height, width, height);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        //TODO: Change this to using PacketGuiButtonPress or something with a specific dump button
        // That way we have even less trust of what the client is telling us and don't need the actions manually coded
        onPress.run();
    }
}