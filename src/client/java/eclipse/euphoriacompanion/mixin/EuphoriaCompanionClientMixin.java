// EuphoriaCompanionClientMixin.java
package eclipse.euphoriacompanion.mixin;

import eclipse.euphoriacompanion.EuphoriaCompanionClient;
import eclipse.euphoriacompanion.EuphoriaCompanion;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class EuphoriaCompanionClientMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void onClientTick(CallbackInfo ci) {
		if (EuphoriaCompanionClient.analyzeShaderKey.wasPressed()) {
			EuphoriaCompanionClient.LOGGER.info("Processing block.properties!");
			EuphoriaCompanion.processShaderPacks();
		}
	}
}