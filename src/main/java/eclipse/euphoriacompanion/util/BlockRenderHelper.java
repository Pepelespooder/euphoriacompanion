package eclipse.euphoriacompanion.util;

import eclipse.euphoriacompanion.EuphoriaCompanion;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper utility for working with block render layers and categories.
 */
public class BlockRenderHelper {
    // Categories we're tracking
    private static final BlockRenderCategory TRANSLUCENT = BlockRenderCategory.TRANSLUCENT;
    private static final BlockRenderCategory SOLID = BlockRenderCategory.SOLID;
    private static final BlockRenderCategory LIGHT_EMITTING = BlockRenderCategory.LIGHT_EMITTING;
    private static final BlockRenderCategory FULL_CUBE = BlockRenderCategory.FULL_CUBE;

    // Cache to avoid repeated lookups
    private static final Map<Block, Set<BlockRenderCategory>> blockCategoriesCache = new ConcurrentHashMap<>();

    // Lists to store blocks by category
    private static final List<Block> translucentBlocks = new ArrayList<>();
    private static final List<Block> solidBlocks = new ArrayList<>();
    private static final List<Block> lightEmittingBlocks = new ArrayList<>();
    private static final List<Block> fullCubeBlocks = new ArrayList<>();

    // For caching the model based full-cube checks
    private static final Map<Block, Boolean> fullCubeModelCache = new ConcurrentHashMap<>();

    private static boolean isFullCube(Block block, BlockState state) {
        // Check cache first
        if (fullCubeModelCache.containsKey(block)) {
            return fullCubeModelCache.get(block);
        }

        // Check if it's a block entity (never a full cube)
        try {
            if (state.hasBlockEntity()) {
                fullCubeModelCache.put(block, false);
                return false;
            }
        } catch (Exception ignored) {
            // Continue with other checks if this fails
        }

        // A full cube must be opaque
        if (!state.isOpaque()) {
            fullCubeModelCache.put(block, false);
            return false;
        }

        // Check if the block allows light to pass through it
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                int opacity; // Max opacity value
                boolean lightPasses = false;

                // First, use version-specific method to check opacity
                try {
                    if (MCVersionChecker.isMinecraft1212OrLater()) {
                        // In MC 1.21.2+, getOpacity() takes no parameters
                        opacity = (int) state.getClass().getMethod("getOpacity").invoke(state);
                    } else {
                        // In MC 1.21.1 and earlier, getOpacity() takes world and position
                        opacity = state.getOpacity(client.world, BlockPos.ORIGIN);
                    }

                    // If opacity is less than max, light passes through
                    if (opacity < 15) {
                        lightPasses = true;
                    }
                } catch (Exception e) {
                    // If opacity check fails, fall back to render layer check
                    RenderLayer renderLayer = RenderLayers.getBlockLayer(state);
                    if (renderLayer == RenderLayer.getTranslucent()) {
                        // Only consider fully translucent blocks (not cutout for grass blocks)
                        lightPasses = true;
                    }
                }

                // If light passes through the block, it's not a "solid full cube" for our purposes
                if (lightPasses) {
                    fullCubeModelCache.put(block, false);
                    return false;
                }
            }
        } catch (Exception ignored) {
            // Continue with other checks if this fails
        }

        boolean result = false;

        try {
            // Try multiple detection methods in order of reliability

            // Method 1: Use the isFullCube method if available
            try {
                java.lang.reflect.Method isFullCubeMethod = state.getClass().getMethod("isFullCube");
                Boolean methodResult = (Boolean) isFullCubeMethod.invoke(state);
                if (methodResult != null) {
                    result = methodResult;
                    fullCubeModelCache.put(block, result);
                    return result;
                }
            } catch (Exception ignored) {
            }

            // Method 2: Check the block model for faces in all directions
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getBakedModelManager() != null) {
                BakedModel model = client.getBakedModelManager().getBlockModels().getModel(state);
                if (model != null) {
                    // Check if the model has quads in all directions
                    boolean hasAllFaces = true;
                    for (Direction direction : Direction.values()) {
                        List<BakedQuad> quads = model.getQuads(state, direction, Objects.requireNonNull(client.world).getRandom());
                        if (quads == null || quads.isEmpty()) {
                            hasAllFaces = false;
                            break;
                        }
                    }

                    // Also check for quads with null direction - a true cube should not have any
                    List<BakedQuad> nullQuads = model.getQuads(state, null, Objects.requireNonNull(client.world).getRandom());
                    int nullQuadCount = nullQuads != null ? nullQuads.size() : 0;

                    // A true cube must have all directional faces AND no null quads
                    if (hasAllFaces && nullQuadCount == 0) {
                        result = true;
                        fullCubeModelCache.put(block, true);
                        return true;
                    }
                }
            }

            // Method 3: Check if it's a solid opaque block (good fallback)
            try {
                result = state.isOpaque() && state.isSolidBlock(Objects.requireNonNull(client).world, null);
                fullCubeModelCache.put(block, result);
                return result;
            } catch (Exception ignored) {
            }

        } catch (Exception e) {
            EuphoriaCompanion.LOGGER.debug("Error checking if block is full cube: {}", e.getMessage());
        }

        // Cache the result
        fullCubeModelCache.put(block, result);
        return result;
    }

    /**
     * Gets the primary render category of a block.
     * Used for backwards compatibility.
     *
     * @param block The block to categorize
     * @return The primary category (TRANSLUCENT takes precedence over others)
     */
    public static BlockRenderCategory getRenderCategory(Block block) {
        Set<BlockRenderCategory> categories = getCategories(block);
        if (categories.contains(TRANSLUCENT)) {
            return TRANSLUCENT;
        } else if (categories.contains(LIGHT_EMITTING)) {
            return LIGHT_EMITTING;
        } else if (categories.contains(FULL_CUBE)) {
            return FULL_CUBE;
        } else {
            return SOLID;
        }
    }

    /**
     * Gets all categories a block belongs to.
     * A block can be in multiple categories (e.g., both translucent and light-emitting).
     *
     * @param block The block to categorize
     * @return Set of categories the block belongs to
     */
    public static Set<BlockRenderCategory> getCategories(Block block) {
        // Check cache first
        if (blockCategoriesCache.containsKey(block)) {
            return blockCategoriesCache.get(block);
        }

        Set<BlockRenderCategory> categories = new HashSet<>();

        // Get the block's default state
        BlockState state = block.getDefaultState();

        // Check for translucency
        RenderLayer renderLayer = RenderLayers.getBlockLayer(state);
        boolean isTranslucent = renderLayer.toString().contains("translucent");
        if (isTranslucent) {
            categories.add(TRANSLUCENT);
        } else {
            categories.add(SOLID);
        }

        // Check for light emission
        if (state.getLuminance() > 0) {
            categories.add(LIGHT_EMITTING);
        }

        // Check if it's a full cube by examining its model
        if (isFullCube(block, state)) {
            categories.add(FULL_CUBE);
        }

        // Cache the result
        blockCategoriesCache.put(block, categories);
        return categories;
    }

    /**
     * Categorizes all registered blocks by their properties.
     * Should be called after the block registry is frozen.
     */
    public static void categorizeAllBlocks() {
        // No starting message - removed to reduce log spam

        // Clear all caches
        clearCaches();

        // Counters for each category
        Map<BlockRenderCategory, AtomicInteger> counts = new HashMap<>();
        for (BlockRenderCategory category : BlockRenderCategory.values()) {
            counts.put(category, new AtomicInteger());
        }

        // For special combinations
        AtomicInteger translucentAndLightCount = new AtomicInteger();
        AtomicInteger fullCubeAndLightCount = new AtomicInteger();

        // Process blocks using stream for better efficiency
        Registries.BLOCK.forEach(block -> {
            // Get all categories for the block
            Set<BlockRenderCategory> categories = getCategories(block);

            // Add to category lists and increment counters
            for (BlockRenderCategory category : categories) {
                counts.get(category).incrementAndGet();

                // Add to the corresponding list
                if (category == TRANSLUCENT) {
                    translucentBlocks.add(block);
                } else if (category == SOLID) {
                    solidBlocks.add(block);
                } else if (category == LIGHT_EMITTING) {
                    lightEmittingBlocks.add(block);
                } else if (category == FULL_CUBE) {
                    fullCubeBlocks.add(block);
                }
            }

            // Track special combinations
            if (categories.contains(TRANSLUCENT) && categories.contains(LIGHT_EMITTING)) {
                translucentAndLightCount.incrementAndGet();
            }

            if (categories.contains(FULL_CUBE) && categories.contains(LIGHT_EMITTING)) {
                fullCubeAndLightCount.incrementAndGet();
            }
        });

        // No summary logging - removed to reduce log spam
    }

    /**
     * Clears all internal caches.
     * Should be called when the block registry changes.
     */
    public static void clearCaches() {
        blockCategoriesCache.clear();
        fullCubeModelCache.clear();
        translucentBlocks.clear();
        solidBlocks.clear();
        lightEmittingBlocks.clear();
        fullCubeBlocks.clear();
    }

    /**
     * Gets all blocks in a specific render category.
     *
     * @param category The category to get blocks for
     * @return List of blocks in that category
     */
    public static List<Block> getBlocksInCategory(BlockRenderCategory category) {
        if (category == TRANSLUCENT) {
            return Collections.unmodifiableList(translucentBlocks);
        } else if (category == LIGHT_EMITTING) {
            return Collections.unmodifiableList(lightEmittingBlocks);
        } else if (category == FULL_CUBE) {
            return Collections.unmodifiableList(fullCubeBlocks);
        } else {
            return Collections.unmodifiableList(solidBlocks);
        }
    }

    /**
     * Gets a map of all blocks organized by render category.
     *
     * @return Map of categories to block lists
     */
    public static Map<BlockRenderCategory, List<Block>> getAllBlocksByCategory() {
        Map<BlockRenderCategory, List<Block>> result = new HashMap<>();
        result.put(TRANSLUCENT, Collections.unmodifiableList(translucentBlocks));
        result.put(SOLID, Collections.unmodifiableList(solidBlocks));
        result.put(LIGHT_EMITTING, Collections.unmodifiableList(lightEmittingBlocks));
        result.put(FULL_CUBE, Collections.unmodifiableList(fullCubeBlocks));
        return result;
    }

    /**
     * Gets a map of block identifiers organized by category.
     * Useful for serialization.
     *
     * @return Map of categories to block identifier lists
     */
    public static Map<String, List<String>> getAllBlockIdsByCategory() {
        Map<String, List<String>> result = new HashMap<>();

        // Use a more efficient approach with streams
        for (BlockRenderCategory category : BlockRenderCategory.values()) {
            List<String> blockIds = getBlocksInCategory(category).stream().map(block -> Registries.BLOCK.getId(block).toString()).collect(java.util.stream.Collectors.toList());

            result.put(category.name(), blockIds);
        }

        return result;
    }
}