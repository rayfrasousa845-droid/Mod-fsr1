package com.fsrforgeopti;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

@Mod(modid = "fsrforgeopti", name = "FSRForgeOpti", version = "1.0",
     acceptedMinecraftVersions = "[1.8.9]", clientSideOnly = true)
public class FSRForgeOpti {
    public static final Logger LOG = LogManager.getLogger("FSRForgeOpti");

    private final Minecraft mc = Minecraft.getMinecraft();
    private KeyBinding openKey;
    private World lastWorld;
    private int tick;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        FSRConfig.load(e.getSuggestedConfigurationFile());
        OptiFineCompat.detect();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        openKey = new KeyBinding("Menu FSR", Keyboard.KEY_RSHIFT, "FSRForgeOpti");
        ClientRegistry.registerKeyBinding(openKey);

        FSRRenderer renderer = new FSRRenderer();
        FMLCommonHandler.instance().bus().register(renderer);   // RenderTickEvent
        MinecraftForge.EVENT_BUS.register(renderer);            // RenderWorldLastEvent
        FMLCommonHandler.instance().bus().register(this);       // ClientTickEvent

        ClientCommandHandler.instance.registerCommand(new FSRCommand());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        while (openKey.isPressed()) {
            if (mc.currentScreen == null) mc.displayGuiScreen(new FSRGui());
        }

        if (mc.theWorld != lastWorld) {
            lastWorld = mc.theWorld;
            if (lastWorld != null && mc.thePlayer != null) onWorldJoin();
        }

        // O usuario pode religar o Fast Render depois; verifica a cada ~2s.
        if (mc.thePlayer != null && ++tick % 40 == 0) enforceFastRender(false);
    }

    private void onWorldJoin() {
        if (!OptiFineCompat.isPresent()) {
            chat("\u00a7eOptiFine n\u00e3o detectado - o FSR funciona, mas este mod foi feito para rodar junto com OptiFine HD U M5.");
            return;
        }
        chat("OptiFine detectado (" + OptiFineCompat.getVersion() + ").");
        enforceFastRender(true);
        if (OptiFineCompat.isShaderPackActive()) {
            chat("\u00a7eShader pack do OptiFine ativo: o FSR fica pausado enquanto ele estiver ligado.");
        }
    }

    private void enforceFastRender(boolean announceIfOff) {
        if (!FSRConfig.enabled || !OptiFineCompat.isPresent() || !OptiFineCompat.isFastRenderOn()) return;
        if (FSRConfig.autoDisableFastRender && OptiFineCompat.disableFastRender()) {
            chat("Fast Render do OptiFine foi desligado automaticamente.");
        } else if (announceIfOff || tick % 400 == 0) {
            chat("\u00a7eDesative o Fast Render manualmente: Op\u00e7\u00f5es > Qualidade de V\u00eddeo > Desempenho > Fast Render.");
        }
    }

    public static void chat(String msg) {
        Minecraft m = Minecraft.getMinecraft();
        if (m.thePlayer != null) m.thePlayer.addChatMessage(new ChatComponentText("\u00a7b[FSR] \u00a7f" + msg));
    }
}
