package mekanism.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import mekanism.api.Coord4D;
import mekanism.client.render.GLSMHelper;
import mekanism.client.render.MekanismRenderHelper;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.util.Rectangle;

@SideOnly(Side.CLIENT)
public class GuiSeismicReader extends GuiScreen {

    private final ItemStack itemStack;
    private Coord4D pos;
    private int xSize = 137;
    private int ySize = 182;
    private World worldObj;
    private ArrayList<Pair<Integer, Block>> blockList = new ArrayList<>();
    private Rectangle upButton, downButton, tooltip;

    private int currentLayer;

    public GuiSeismicReader(World world, Coord4D coord, ItemStack stack) {
        pos = new Coord4D(coord.x, Math.min(255, coord.y), coord.z, world.provider.getDimension());
        worldObj = world;

        itemStack = stack;
        calculate();
        currentLayer = Math.max(0, blockList.size() - 1);
    }

    @Override
    public void initGui() {
        super.initGui();
        int guiLeft = (width - xSize) / 2;
        int guiTop = (height - ySize) / 2;
        upButton = new Rectangle(guiLeft + 70, guiTop + 75, 13, 13);
        downButton = new Rectangle(guiLeft + 70, guiTop + 92, 13, 13);
        tooltip = new Rectangle(guiLeft + 30, guiTop + 82, 16, 16);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        int guiLeft = (width - xSize) / 2;
        int guiTop = (height - ySize) / 2;
        mc.renderEngine.bindTexture(MekanismUtils.getResource(ResourceType.GUI, "GuiSeismicReader.png"));
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        // Draws the up button
        boolean upIntersects = upButton.intersects(new Rectangle(mouseX, mouseY, 1, 1));
        if (upIntersects) {
            GLSMHelper.INSTANCE.color3f(0.5F, 0.5F, 1);
        }
        drawTexturedModalRect(upButton.getX(), upButton.getY(), 137, 0, upButton.getWidth(), upButton.getHeight());
        if (upIntersects) {
            GLSMHelper.INSTANCE.resetColor();
        }

        // Draws the down button
        boolean downIntersects = downButton.intersects(new Rectangle(mouseX, mouseY, 1, 1));
        if (downIntersects) {
            GLSMHelper.INSTANCE.color3f(0.5F, 0.5F, 1);
        }
        drawTexturedModalRect(downButton.getX(), downButton.getY(), 150, 0, downButton.getWidth(), downButton.getHeight());
        if (downIntersects) {
            GLSMHelper.INSTANCE.resetColor();
        }

        // Fix the overlapping if > 100
        GlStateManager.pushMatrix();
        GLSMHelper.INSTANCE.translateXY(guiLeft + 48, guiTop + 87);

        if (currentLayer >= 100) {
            GLSMHelper.INSTANCE.translateY(1).scale(0.7F);
        }

        fontRenderer.drawString(String.format("%s", currentLayer), 0, 0, 0xAFAFAF);
        GlStateManager.popMatrix();

        // Render the item stacks
        for (int i = 0; i < 9; i++) {
            int centralX = guiLeft + 32, centralY = guiTop + 103;
            int layer = currentLayer + (i - 5);
            if (0 <= layer && layer < blockList.size()) {
                ItemStack stack = new ItemStack(blockList.get(layer).getRight(), 1, blockList.get(layer).getLeft());
                MekanismRenderHelper stackRenderHelper = new MekanismRenderHelper(true).translateXY(centralX - 2, centralY - i * 16 + (22 * 2));
                if (i < 4) {
                    stackRenderHelper.translateXY(0.2F, 2.5F);
                }
                if (i != 4) {
                    stackRenderHelper.translateX(1.5F).scale(0.8F);
                }
                stackRenderHelper.enableDepth().enableGUIStandardItemLighting();
                itemRender.renderItemAndEffectIntoGUI(stack, 0, 0);
                stackRenderHelper.cleanup();
            }
        }

        // Get the name from the stack and render it
        if (currentLayer - 1 >= 0) {
            ItemStack nameStack = new ItemStack(blockList.get(currentLayer - 1).getRight(), 1, blockList.get(currentLayer - 1).getLeft());
            String renderString = nameStack.getDisplayName();

            String capitalised = renderString.substring(0, 1).toUpperCase() + renderString.substring(1);
            int lengthX = fontRenderer.getStringWidth(capitalised);
            float renderScale = lengthX > 53 ? 53f / lengthX : 1.0f;

            GlStateManager.pushMatrix();
            GLSMHelper.INSTANCE.translateXY(guiLeft + 72, guiTop + 16).scale(renderScale);
            fontRenderer.drawString(capitalised, 0, 0, 0x919191);
            GlStateManager.popMatrix();

            if (tooltip.intersects(new Rectangle(mouseX, mouseY, 1, 1))) {
                mc.renderEngine.bindTexture(MekanismUtils.getResource(ResourceType.GUI_ELEMENT, "GuiTooltips.png"));
                int fontLengthX = fontRenderer.getStringWidth(capitalised) + 5;
                int renderX = mouseX + 10, renderY = mouseY - 5;
                GlStateManager.pushMatrix();
                drawTexturedModalRect(renderX, renderY, 0, 0, fontLengthX, 16);
                drawTexturedModalRect(renderX + fontLengthX, renderY, 0, 16, 2, 16);
                fontRenderer.drawString(capitalised, renderX + 4, renderY + 4, 0x919191);
                GlStateManager.popMatrix();
            }
        }

        int frequency = 0;

        for (Pair<Integer, Block> pair : blockList) {
            if (blockList.get(currentLayer - 1) != null) {
                Block block = blockList.get(currentLayer - 1).getRight();

                if (pair.getRight() == block && Objects.equals(pair.getLeft(), blockList.get(currentLayer - 1).getLeft())) {
                    frequency++;
                }
            }
        }

        GlStateManager.pushMatrix();
        GLSMHelper.INSTANCE.translateXY(guiLeft + 72, guiTop + 26).scale(0.70F);
        fontRenderer.drawString(LangUtils.localize("gui.abundancy") + ": " + frequency, 0, 0, 0x919191);
        GlStateManager.popMatrix();
        super.drawScreen(mouseX, mouseY, partialTick);
    }

    public String wrapString(String str, int index) {
        String string = str;
        for (int i = 0; i < string.length(); i++) {
            if (i == index) {
                string = string.substring(0, i) + "\n" + string.substring(i);
            }
        }
        return string;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        blockList.clear();
    }

    public void calculate() {
        for (BlockPos p = new BlockPos(pos.x, 0, pos.z); p.getY() < pos.y; p = p.up()) {
            IBlockState state = worldObj.getBlockState(p);
            Block block = state.getBlock();
            int metadata = block.getMetaFromState(state);
            blockList.add(Pair.of(metadata, block));
        }
    }

    @Override
    protected void mouseClicked(int xPos, int yPos, int buttonClicked) throws IOException {
        super.mouseClicked(xPos, yPos, buttonClicked);
        if (upButton.intersects(new Rectangle(xPos, yPos, 1, 1))) {
            if (currentLayer + 1 <= blockList.size() - 1) {
                currentLayer++;
            }
        }

        if (downButton.intersects(new Rectangle(xPos, yPos, 1, 1))) {
            if (currentLayer - 1 >= 1) {
                currentLayer--;
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}