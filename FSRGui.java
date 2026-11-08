package com.fsrforgeopti;

import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;

public class FSRGui extends GuiScreen {
    private GuiButton toggleBtn, scaleBtn;
    private Slider slider;

    @Override
    public void initGui() {
        int cx = width / 2, y = height / 2 - 40;
        buttonList.clear();
        buttonList.add(toggleBtn = new GuiButton(0, cx - 100, y, 200, 20, ""));
        buttonList.add(scaleBtn = new GuiButton(1, cx - 100, y + 24, 200, 20, ""));
        buttonList.add(slider = new Slider(2, cx - 100, y + 48, 200, 20));
        buttonList.add(new GuiButton(3, cx - 100, y + 80, 200, 20, "Conclu\u00eddo"));
        refresh();
    }

    private void refresh() {
        toggleBtn.displayString = "FSR: " + (FSRConfig.enabled ? "\u00a7aLIGADO" : "\u00a7cDESLIGADO");
        scaleBtn.displayString = "Escala: " + Math.round(FSRConfig.scale * 100f) + "%";
        slider.updateLabel();
    }

    @Override
    protected void actionPerformed(GuiButton b) throws IOException {
        switch (b.id) {
            case 0: FSRConfig.enabled = !FSRConfig.enabled; break;
            case 1: {
                int cur = 0; float best = 9f;
                for (int i = 0; i < FSRConfig.SCALES.length; i++) {
                    float d = Math.abs(FSRConfig.SCALES[i] - FSRConfig.scale);
                    if (d < best) { best = d; cur = i; }
                }
                FSRConfig.scale = FSRConfig.SCALES[(cur + 1) % FSRConfig.SCALES.length];
                break;
            }
            case 3: mc.displayGuiScreen(null); return;
        }
        refresh();
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "FSRForgeOpti - FSR 1.0", width / 2, height / 2 - 70, 0xFFFFFF);
        String of = OptiFineCompat.isPresent() ? "OptiFine: detectado" : "OptiFine: n\u00e3o detectado";
        drawCenteredString(fontRendererObj, of, width / 2, height / 2 - 58, 0xAAAAAA);
        drawCenteredString(fontRendererObj, "O FSR s\u00f3 aparece com a GUI fechada (RShift para abrir/fechar)",
                width / 2, height / 2 + 62, 0x888888);
        super.drawScreen(mx, my, pt);
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_RSHIFT) { mc.displayGuiScreen(null); return; }
        super.keyTyped(c, key);
    }

    @Override
    public void onGuiClosed() { FSRConfig.save(); }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private static class Slider extends GuiButton {
        private boolean dragging;

        Slider(int id, int x, int y, int w, int h) { super(id, x, y, w, h, ""); }

        void updateLabel() {
            displayString = "Nitidez RCAS: " + String.format("%.2f", FSRConfig.sharpness);
        }

        private void setFromMouse(int mx) {
            float v = (float) (mx - (xPosition + 4)) / (float) (width - 8);
            v = FSRConfig.clamp(v, 0f, 1f);
            FSRConfig.sharpness = Math.round(v * 20f) / 20f;   // passos de 0.05
            updateLabel();
        }

        @Override protected int getHoverState(boolean over) { return 0; }

        @Override
        protected void mouseDragged(Minecraft mc, int mx, int my) {
            if (!visible) return;
            if (dragging) setFromMouse(mx);
            GlStateManager.color(1f, 1f, 1f, 1f);
            int pos = xPosition + (int) (FSRConfig.sharpness * (width - 8));
            drawTexturedModalRect(pos, yPosition, 0, 66, 4, 20);
            drawTexturedModalRect(pos + 4, yPosition, 196, 66, 4, 20);
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mx, int my) {
            if (super.mousePressed(mc, mx, my)) { dragging = true; setFromMouse(mx); return true; }
            return false;
        }

        @Override public void mouseReleased(int mx, int my) { dragging = false; FSRConfig.save(); }
    }
}
