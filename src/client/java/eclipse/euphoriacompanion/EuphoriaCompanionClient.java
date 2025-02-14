package eclipse.euphoriacompanion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EuphoriaCompanionClient implements ClientModInitializer {
	public static final String MOD_ID = "Euphoria Companion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private KeyBinding analyzeShaderKey;

	@Override
	public void onInitializeClient() {
		// Register keybinding (default to F6)
		analyzeShaderKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.euphoriacompanion.analyze_shaders",  // Translation key
				InputUtil.Type.KEYSYM,                    // Key type
				GLFW.GLFW_KEY_F6,                        // Default key
				"category.euphoriacompanion.main"        // Category translation key
		));

		// Register the tick event to check for key presses
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (analyzeShaderKey.wasPressed()) {
				LOGGER.info("Processing block.properties!");
				EuphoriaCompanion.processShaderPacks();
			}
		});
	}
}