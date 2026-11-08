package com.fsrforgeopti;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public final class FSRConfig {
    public static final float[] SCALES = {0.50f, 0.59f, 0.67f, 0.75f};

    public static boolean enabled = true;
    public static float scale = 0.67f;
    public static float sharpness = 0.8f;          // 0.0 (sem RCAS) .. 1.0 (maximo)
    public static boolean autoDisableFastRender = true;

    private static Configuration cfg;

    private FSRConfig() {}

    public static void load(File file) {
        cfg = new Configuration(file);
        enabled = cfg.get("general", "enabled", true).getBoolean(true);
        scale = clamp((float) cfg.get("general", "scale", 0.67).getDouble(0.67), 0.40f, 1.0f);
        sharpness = clamp((float) cfg.get("general", "sharpness", 0.8).getDouble(0.8), 0.0f, 1.0f);
        autoDisableFastRender = cfg.get("optifine", "autoDisableFastRender", true).getBoolean(true);
        if (cfg.hasChanged()) cfg.save();
    }

    public static void save() {
        if (cfg == null) return;
        cfg.get("general", "enabled", true).set(enabled);
        cfg.get("general", "scale", 0.67).set((double) scale);
        cfg.get("general", "sharpness", 0.8).set((double) sharpness);
        cfg.get("optifine", "autoDisableFastRender", true).set(autoDisableFastRender);
        cfg.save();
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Valor de "con" do RCAS: exp2(-stops). stops = 2*(1-sharpness). 0 = desligado. */
    public static float rcasConstant() {
        if (sharpness <= 0.001f) return 0f;
        return (float) Math.pow(2.0, -(2.0 * (1.0 - sharpness)));
    }
}
