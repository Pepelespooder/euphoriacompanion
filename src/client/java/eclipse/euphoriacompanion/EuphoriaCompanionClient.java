// EuphoriaCompanionClient.java
package eclipse.euphoriacompanion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EuphoriaCompanionClient implements ClientModInitializer {
	public static final String MOD_ID = "Euphoria Companion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static KeyBinding analyzeShaderKey;

	@Override
	public void onInitializeClient() {
		// Only register keybinding here
		analyzeShaderKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.euphoriacompanion.analyze_shaders",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_F6,
				"category.euphoriacompanion.main"
		));
	}
}