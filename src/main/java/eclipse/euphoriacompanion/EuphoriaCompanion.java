package eclipse.euphoriacompanion;

import eclipse.euphoriacompanion.shader.ShaderPackProcessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class EuphoriaCompanion implements ModInitializer {
    public static final String MODID = "euphoriacompanion";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public static KeyBinding ANALYZE_KEY = new KeyBinding("key.euphoriacompanion.analyze", InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F6).getCode(), "category.euphoriacompanion.keys");

    public static void processShaderPacks() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        ShaderPackProcessor.processShaderPacks(gameDir);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Euphoria Companion");
        ANALYZE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.euphoriacompanion.analyze", InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F6).getCode(), "category.euphoriacompanion.keys"));
    }
}