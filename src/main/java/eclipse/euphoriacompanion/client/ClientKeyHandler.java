package eclipse.euphoriacompanion.client;

import eclipse.euphoriacompanion.shader.ShaderPackProcessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.nio.file.Path;

@SideOnly(Side.CLIENT)
public class ClientKeyHandler {
    private static final KeyBinding analyzeKey = new KeyBinding("Read Properties File", Keyboard.KEY_F6, "Euphoria Companion");

    public static void register() {
        ClientRegistry.registerKeyBinding(analyzeKey);
        MinecraftForge.EVENT_BUS.register(new ClientKeyHandler());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && analyzeKey.isPressed()) {
            ShaderPackProcessor.processShaderPacks(getClientGameDir());
        }
    }

    private Path getClientGameDir() {
        return Minecraft.getMinecraft().gameDir.toPath();
    }
}