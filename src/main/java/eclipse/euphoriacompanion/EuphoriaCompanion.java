package eclipse.euphoriacompanion;

import eclipse.euphoriacompanion.client.ClientKeyHandler;
import eclipse.euphoriacompanion.shader.ShaderPackProcessor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = EuphoriaCompanion.MODID, name = "Euphoria Companion", version = "1.0.2")
public class EuphoriaCompanion {
    public static final String MODID = "euphoriacompanion";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public static void onServerStarting(FMLServerStartingEvent event) {
        LOGGER.info("Server starting, processing shader packs");
        ShaderPackProcessor.processShaderPacks(event.getServer().getDataDirectory().toPath());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            ClientKeyHandler.register();
        }
    }
}