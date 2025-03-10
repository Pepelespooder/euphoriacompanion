package eclipse.euphoriacompanion.client;

import eclipse.euphoriacompanion.EuphoriaCompanion;
import eclipse.euphoriacompanion.util.BlockRenderHelper;
import eclipse.euphoriacompanion.util.BlockRegistryCacheManager;
import eclipse.euphoriacompanion.util.RegistryUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;

/**
 * Client initializer for the Euphoria Companion mod.
 * Keybinding is handled through mixins to ensure compatibility across Fabric versions.
 */
public class EuphoriaCompanionClient implements ClientModInitializer {
    private static final int WORLD_CHECK_MAX_ATTEMPTS = 60; // Try for 60 seconds
    private static final int WORLD_CHECK_INTERVAL_MS = 1000; // Check every second
    private static final int WORLD_LOAD_DELAY_MS = 2000; // Wait extra time after world detection

    @Override
    public void onInitializeClient() {
        EuphoriaCompanion.LOGGER.info("Initializing Euphoria Companion Client");
        
        try {
            // Register client lifecycle events
            ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
                // Handle registry caching
                setupRegistryCache();
                
                // Start world detection for block categorization
                startWorldDetectionThread();
            });
        } catch (Exception e) {
            EuphoriaCompanion.LOGGER.error("Failed to register client lifecycle events", e);
        }
    }
    
    /**
     * Sets up the block registry cache if needed
     */
    private void setupRegistryCache() {
        MinecraftClient.getInstance().execute(() -> {
            // Only run if block registry is frozen
            if (!RegistryUtil.isBlockRegistryFrozen()) {
                return;
            }
            
            EuphoriaCompanion.LOGGER.info("Client started, checking block registry cache");
            if (!BlockRegistryCacheManager.cacheExists()) {
                EuphoriaCompanion.LOGGER.info("Creating initial block registry cache");
                BlockRegistryCacheManager.cacheBlockRegistry();
                
                // Skip block categorization here - we'll do it when a world is loaded
                EuphoriaCompanion.LOGGER.info("Block categorization will be performed when a world is loaded");
                
                // Clear any existing caches
                clearBlockRenderCaches();
            }
        });
    }
    
    /**
     * Clears the BlockRenderHelper caches
     */
    private void clearBlockRenderCaches() {
        BlockRenderHelper.clearCaches();
    }
    
    /**
     * Starts a background thread to wait for world availability before categorizing blocks
     */
    private void startWorldDetectionThread() {
        MinecraftClient.getInstance().execute(() -> {
            Thread worldCheckThread = new Thread(this::checkForWorldAvailability);
            worldCheckThread.setDaemon(true);
            worldCheckThread.setName("EuphoriaCompanion-WorldCheck");
            worldCheckThread.start();
        });
    }
    
    /**
     * Continuously checks for world availability
     */
    private void checkForWorldAvailability() {
        boolean worldFound = false;
        int attempts = 0;
        
        while (!worldFound && attempts < WORLD_CHECK_MAX_ATTEMPTS) {
            try {
                Thread.sleep(WORLD_CHECK_INTERVAL_MS);
                MinecraftClient mc = MinecraftClient.getInstance();
                
                if (mc != null && mc.world != null && mc.player != null) {
                    // Wait extra time to ensure the world is fully loaded
                    Thread.sleep(WORLD_LOAD_DELAY_MS);
                    
                    worldFound = true;
                    EuphoriaCompanion.LOGGER.info("World detected with player at position {}, now categorizing blocks", 
                                               mc.player.getBlockPos());
                    
                    performBlockCategorization(mc);
                }
                attempts++;
            } catch (InterruptedException e) {
                // Thread interrupted, just exit
                break;
            } catch (Exception e) {
                EuphoriaCompanion.LOGGER.error("Error checking for world availability", e);
                break;
            }
        }
        
        if (!worldFound) {
            EuphoriaCompanion.LOGGER.warn("World not detected after {} attempts, block categorization was not performed", 
                                      WORLD_CHECK_MAX_ATTEMPTS);
        }
    }
    
    /**
     * Performs block categorization on the main thread
     */
    private void performBlockCategorization(MinecraftClient mc) {
        mc.execute(() -> {
            // Double-check that world is still available
            if (mc.world != null) {
                BlockRenderHelper.categorizeAllBlocks();
            } else {
                EuphoriaCompanion.LOGGER.warn("World became null before categorization could start");
            }
        });
    }
}