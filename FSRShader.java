package com.fsrforgeopti;

import java.io.InputStream;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/** Compila e executa os passes FSR 1.0 (EASU e RCAS) em GLSL 120. */
public final class FSRShader {
    private static final String VERT =
        "#version 120\n" +
        "void main(){ gl_TexCoord[0] = gl_MultiTexCoord0; gl_Position = ftransform(); }\n";

    private static boolean ready;
    private static int easuProg, rcasProg;
    private static int eTex, eSize, rTex, rSize, rSharp;

    private FSRShader() {}

    public static boolean isReady() { return ready; }

    /** Chamar com o contexto GL ativo (thread de render). */
    public static boolean init() {
        if (ready) return true;
        if (!GLContext.getCapabilities().OpenGL20) {
            FSRForgeOpti.LOG.error("OpenGL 2.0 indisponivel - FSR desativado.");
            return false;
        }
        try {
            easuProg = build(load("fsr_easu.fsh"));
            rcasProg = build(load("fsr_rcas.fsh"));
            eTex  = GL20.glGetUniformLocation(easuProg, "uTex");
            eSize = GL20.glGetUniformLocation(easuProg, "uInputSize");
            rTex  = GL20.glGetUniformLocation(rcasProg, "uTex");
            rSize = GL20.glGetUniformLocation(rcasProg, "uInputSize");
            rSharp = GL20.glGetUniformLocation(rcasProg, "uSharp");
            ready = true;
        } catch (Throwable t) {
            FSRForgeOpti.LOG.error("Falha ao compilar shaders FSR", t);
            ready = false;
        }
        return ready;
    }

    private static String load(String name) throws Exception {
        InputStream in = FSRShader.class.getResourceAsStream("/assets/fsrforgeopti/shaders/" + name);
        if (in == null) throw new IllegalStateException("Shader nao encontrado: " + name);
        try { return IOUtils.toString(in, "UTF-8"); } finally { IOUtils.closeQuietly(in); }
    }

    private static int compile(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(s, 8192);
            GL20.glDeleteShader(s);
            throw new IllegalStateException("GLSL: " + log);
        }
        return s;
    }

    private static int build(String frag) {
        int vs = compile(GL20.GL_VERTEX_SHADER, VERT);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, frag);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("Link: " + GL20.glGetProgramInfoLog(p, 8192));
        }
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return p;
    }

    /** EASU: upscale de 'in' (res. interna) para 'out' (res. cheia). */
    public static void easu(Framebuffer in, Framebuffer out) {
        GL20.glUseProgram(easuProg);
        GL20.glUniform1i(eTex, 0);
        GL20.glUniform2f(eSize, in.framebufferTextureWidth, in.framebufferTextureHeight);
        draw(in, out);
        GL20.glUseProgram(0);
    }

    /** RCAS: nitidez, 'in' e 'out' em resolucao cheia. */
    public static void rcas(Framebuffer in, Framebuffer out, float con) {
        GL20.glUseProgram(rcasProg);
        GL20.glUniform1i(rTex, 0);
        GL20.glUniform2f(rSize, in.framebufferTextureWidth, in.framebufferTextureHeight);
        GL20.glUniform1f(rSharp, con);
        draw(in, out);
        GL20.glUseProgram(0);
    }

    /** Assume projecao/modelview ortograficas (0..1) ja configuradas pelo chamador. */
    private static void draw(Framebuffer in, Framebuffer out) {
        out.bindFramebuffer(true);   // tambem ajusta o viewport para o tamanho de 'out'
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(in.framebufferTexture);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(0f, 0f);
        GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(1f, 0f);
        GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(1f, 1f);
        GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(0f, 1f);
        GL11.glEnd();
    }
}
