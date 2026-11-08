package com.fsrforgeopti;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/**
 * Hook 100% por eventos Forge (sem ASM/Mixin, nao toca em EntityRenderer):
 *
 *  RenderTickEvent.START   -> reduz mc.displayWidth/Height e renderiza o mundo num FBO interno.
 *  RenderWorldLastEvent    -> (LOWEST) restaura o tamanho, aplica EASU+RCAS no FBO principal.
 *                             A mao, o HUD e as GUIs sao desenhados depois, em resolucao nativa.
 *  RenderTickEvent.END     -> rede de seguranca (restaura tudo se o evento acima nao disparou).
 *
 * Como o upscale roda DEPOIS de o OptiFine ter desenhado o mundo, o FSR sempre sobrepoe o resultado
 * dele, e nao o contrario.
 */
public class FSRRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();

    private Framebuffer lowFbo;   // render interno (ex: 67%)
    private Framebuffer outFbo;   // saida do EASU em resolucao cheia (so usado se RCAS > 0)
    private int origW, origH;
    private boolean hijacked;
    private boolean failed;

    public static volatile boolean active;   // true no frame em que o FSR foi aplicado
    public static volatile int renderW, renderH;

    // ------------------------------------------------------------ eventos

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) begin(); else end();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onWorldLast(RenderWorldLastEvent e) {
        if (!hijacked) return;
        hijacked = false;
        mc.displayWidth = origW;
        mc.displayHeight = origH;

        // Defesa: se algo (outro mod) trocou o FBO, o mundo nao foi parar no nosso -> aborta com seguranca.
        if (GL11.glGetInteger(36006 /* GL_FRAMEBUFFER_BINDING */) != lowFbo.framebufferObject) {
            fail("O FBO foi trocado por outro mod/OptiFine durante o render. FSR desativado nesta sessao.");
            mc.getFramebuffer().bindFramebuffer(true);
            return;
        }
        upscale();
    }

    // ------------------------------------------------------------ logica

    private void begin() {
        hijacked = false;
        active = false;

        if (!FSRConfig.enabled || failed) { release(); return; }
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (mc.currentScreen != null) return;               // evita coordenadas de mouse erradas em GUIs
        if (!OpenGlHelper.isFramebufferEnabled()) return;
        if (mc.gameSettings.anaglyph) return;
        if (mc.entityRenderer.isShaderActive()) return;     // Super Secret Settings
        if (OptiFineCompat.isShaderPackActive()) return;    // shader pack do OptiFine
        if (!FSRShader.isReady() && !FSRShader.init()) { failed = true; return; }

        int w = mc.displayWidth, h = mc.displayHeight;
        int lw = Math.max(64, Math.round(w * FSRConfig.scale));
        int lh = Math.max(64, Math.round(h * FSRConfig.scale));
        if (lw >= w || lh >= h || w < 128 || h < 128) return;

        try {
            lowFbo = ensure(lowFbo, lw, lh, true);
            if (FSRConfig.sharpness > 0.001f) outFbo = ensure(outFbo, w, h, false);
        } catch (Throwable t) {
            fail("Erro ao criar FBOs: " + t);
            return;
        }

        origW = w; origH = h;
        renderW = lw; renderH = lh;
        mc.displayWidth = lw;
        mc.displayHeight = lh;
        lowFbo.bindFramebuffer(true);
        hijacked = true;
    }

    private void end() {
        if (hijacked) {   // RenderWorldLastEvent nao disparou neste frame
            hijacked = false;
            mc.displayWidth = origW;
            mc.displayHeight = origH;
            mc.getFramebuffer().bindFramebuffer(true);
        }
    }

    private Framebuffer ensure(Framebuffer fb, int w, int h, boolean depth) {
        if (fb == null) return new Framebuffer(w, h, depth);
        if (fb.framebufferWidth != w || fb.framebufferHeight != h) fb.createBindFramebuffer(w, h);
        return fb;
    }

    private void release() {
        if (lowFbo != null) { lowFbo.deleteFramebuffer(); lowFbo = null; }
        if (outFbo != null) { outFbo.deleteFramebuffer(); outFbo = null; }
    }

    private void fail(String msg) {
        failed = true;
        hijacked = false;
        FSRForgeOpti.LOG.error(msg);
        FSRForgeOpti.chat("\u00a7c" + msg);
    }

    private void upscale() {
        GLState st = new GLState();
        st.save();
        try {
            mc.entityRenderer.disableLightmap();

            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.disableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);

            GlStateManager.matrixMode(5889);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
            GlStateManager.matrixMode(5888);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();

            float con = FSRConfig.rcasConstant();
            Framebuffer main = mc.getFramebuffer();
            if (con <= 0f || outFbo == null) {
                FSRShader.easu(lowFbo, main);
            } else {
                FSRShader.easu(lowFbo, outFbo);
                FSRShader.rcas(outFbo, main, con);
            }

            GlStateManager.matrixMode(5889);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5888);
            GlStateManager.popMatrix();
            active = true;
        } catch (Throwable t) {
            fail("Erro no upscale FSR: " + t);
        } finally {
            mc.getFramebuffer().bindFramebuffer(true);   // viewport cheio para mao/HUD
            st.restore();
        }
    }

    /** Salva/restaura estado GL pelo GlStateManager, mantendo o cache dele consistente. */
    private static final class GLState {
        boolean depth, cull, blend, alpha, lighting, fog, tex2d, depthMask;
        int activeTex, boundTex0, program, matrixMode;

        void save() {
            depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            blend = GL11.glIsEnabled(GL11.GL_BLEND);
            alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
            fog = GL11.glIsEnabled(GL11.GL_FOG);
            tex2d = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            activeTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            boundTex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }

        void restore() {
            GL20.glUseProgram(program);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.bindTexture(boundTex0);
            GlStateManager.setActiveTexture(activeTex);
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (fog) GlStateManager.enableFog(); else GlStateManager.disableFog();
            if (tex2d) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            GlStateManager.depthMask(depthMask);
            GlStateManager.matrixMode(matrixMode);
        }
    }
}
