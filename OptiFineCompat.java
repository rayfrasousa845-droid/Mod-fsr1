package com.fsrforgeopti;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

/**
 * Toda a integracao com o OptiFine e feita por REFLEXAO: o mod compila sem o OptiFine
 * e nunca depende de nenhuma classe dele em tempo de carga.
 */
public final class OptiFineCompat {
    // A classe Config muda de pacote entre versoes do OptiFine. Testamos todas.
    private static final String[] CONFIG_CLASSES = {
        "Config", "optifine.Config", "net.optifine.Config", "net.minecraft.src.Config"
    };

    private static boolean inited;
    private static Class<?> configClass;
    private static Method mIsFastRender, mIsShaders, mGetVersion;
    private static Field fFastRender;   // GameSettings.ofFastRender

    private OptiFineCompat() {}

    public static synchronized void detect() {
        if (inited) return;
        inited = true;

        ClassLoader cl = OptiFineCompat.class.getClassLoader();
        for (String name : CONFIG_CLASSES) {
            try {
                Class<?> c = Class.forName(name, false, cl);
                Method probe = findStatic(c, "isFastRender");
                if (probe == null) probe = findStatic(c, "isShaders");
                if (probe != null) { configClass = c; break; }
            } catch (Throwable ignored) { }
        }

        if (configClass != null) {
            mIsFastRender = findStatic(configClass, "isFastRender");
            mIsShaders    = findStatic(configClass, "isShaders");
            mGetVersion   = findStatic(configClass, "getVersion");
        }

        try {
            fFastRender = GameSettings.class.getDeclaredField("ofFastRender");
            fFastRender.setAccessible(true);
        } catch (Throwable t) {
            fFastRender = null;
        }

        FSRForgeOpti.LOG.info("OptiFine: " + (configClass != null
            ? "detectado (" + configClass.getName() + ", " + getVersion() + ")"
            : "NAO detectado"));
    }

    private static Method findStatic(Class<?> c, String name) {
        try {
            Method m = c.getMethod(name);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isPresent() {
        detect();
        return configClass != null;
    }

    public static String getVersion() {
        detect();
        if (mGetVersion != null) {
            try { return String.valueOf(mGetVersion.invoke(null)); } catch (Throwable ignored) { }
        }
        return "versao desconhecida";
    }

    /** true se o OptiFine reporta Fast Render ligado. */
    public static boolean isFastRenderOn() {
        detect();
        if (configClass == null) return false;
        try {
            if (mIsFastRender != null) return (Boolean) mIsFastRender.invoke(null);
            if (fFastRender != null) return fFastRender.getBoolean(Minecraft.getMinecraft().gameSettings);
        } catch (Throwable ignored) { }
        return false;
    }

    /** true se um shader pack do OptiFine esta ativo (FSR fica pausado nesse caso). */
    public static boolean isShaderPackActive() {
        detect();
        if (mIsShaders == null) return false;
        try { return (Boolean) mIsShaders.invoke(null); } catch (Throwable t) { return false; }
    }

    /**
     * Desliga o Fast Render do OptiFine. Deve ser chamado na thread do cliente.
     * @return true se estava ligado e foi desligado com sucesso;
     *         false se ja estava desligado ou nao foi possivel (nesse caso avise o usuario).
     */
    public static boolean disableFastRender() {
        detect();
        if (configClass == null || !isFastRenderOn()) return false;
        if (fFastRender == null) return false;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            fFastRender.setBoolean(mc.gameSettings, false);
            mc.gameSettings.saveOptions();
            if (mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
            return !isFastRenderOn();
        } catch (Throwable t) {
            FSRForgeOpti.LOG.warn("Nao foi possivel desligar o Fast Render: " + t);
            return false;
        }
    }
}
