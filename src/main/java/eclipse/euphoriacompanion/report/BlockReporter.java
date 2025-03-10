package eclipse.euphoriacompanion.report;

import eclipse.euphoriacompanion.EuphoriaCompanion;
import eclipse.euphoriacompanion.util.BlockRenderCategory;
import eclipse.euphoriacompanion.util.BlockRenderHelper;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BlockReporter {
    public static void processShaderBlocks(String shaderpackName, Set<String> shaderBlocks, Set<String> gameBlocks, Path logsDir, Map<String, List<String>> blocksByMod) {
        Set<String> missingFromShader = new HashSet<>(gameBlocks);
        missingFromShader.removeAll(shaderBlocks);

        Set<String> missingFromGame = new HashSet<>(shaderBlocks);
        missingFromGame.removeAll(gameBlocks);

        String safeName = shaderpackName.replaceAll("[^a-zA-Z0-9.-]", "_");
        Path comparisonPath = logsDir.resolve("block_comparison_" + safeName + ".txt");

        // Create categorized blocks
        Map<BlockRenderCategory, Map<String, List<String>>> categorizedBlocksByMod = getCategorizedBlocksByMod(blocksByMod);

        // Create categorized missing blocks
        Map<BlockRenderCategory, Set<String>> categorizedMissingBlocks = categorizeMissingBlocks(missingFromShader);

        writeComparisonFile(comparisonPath, shaderpackName, gameBlocks, shaderBlocks, missingFromShader, missingFromGame, categorizedBlocksByMod, categorizedMissingBlocks);
    }

    private static void writeComparisonFile(Path outputPath, String shaderpackName, Set<String> gameBlocks, Set<String> shaderBlocks, Set<String> missingFromShader, Set<String> missingFromGame, Map<BlockRenderCategory, Map<String, List<String>>> categorizedBlocksByMod, Map<BlockRenderCategory, Set<String>> categorizedMissingBlocks) {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("=========================================\n");
            writer.write("== BLOCK COMPARISON SUMMARY FOR " + shaderpackName.toUpperCase() + " ==\n");
            writer.write("=========================================\n");
            writer.write(String.format("Total blocks in game: %d\n", gameBlocks.size()));
            writer.write(String.format("Total blocks in shader: %d\n", shaderBlocks.size()));
            writer.write(String.format("Unused blocks from shader: %d\n", missingFromGame.size()));
            writer.write(String.format("Blocks missing from shader: %d\n\n", missingFromShader.size()));

            // Write category counts
            writeCategoryCounts(writer, categorizedBlocksByMod);

            if (missingFromShader.isEmpty() && !gameBlocks.isEmpty()) {
                writeCongratulationMessage(writer);
            }

            writeMissingBlocksByCategoryAndMod(writer, categorizedMissingBlocks);
            writeFullBlockListByCategoryAndMod(writer, categorizedBlocksByMod);
            writeUnusedShaderBlocks(writer, missingFromGame);

            EuphoriaCompanion.LOGGER.info("Report written to {}", outputPath);
        } catch (IOException e) {
            EuphoriaCompanion.LOGGER.error("Failed to write report", e);
        }
    }

    private static void writeCategoryCounts(BufferedWriter writer, Map<BlockRenderCategory, Map<String, List<String>>> categorizedBlocks) throws IOException {
        writer.write("============ BLOCK COUNTS BY CATEGORY ============\n");
        for (BlockRenderCategory category : BlockRenderCategory.values()) {
            int count = 0;
            Map<String, List<String>> modBlocks = categorizedBlocks.get(category);
            if (modBlocks != null) {
                for (List<String> blocks : modBlocks.values()) {
                    count += blocks.size();
                }
            }
            // Show "non_full_blocks" instead of "solid" in the report
            String displayName = category == BlockRenderCategory.SOLID ? "non_full_blocks" : category.name();
            writer.write(String.format("%s: %d blocks\n", displayName, count));
        }
        writer.write("\n");
    }

    private static void writeCongratulationMessage(BufferedWriter writer) throws IOException {
        writer.write("\n");
        writer.write("Nice! All blocks are added!\n\n");
    }

    private static void writeMissingBlocksByCategoryAndMod(BufferedWriter writer, Map<BlockRenderCategory, Set<String>> categorizedMissingBlocks) throws IOException {
        writer.write("============ MISSING BLOCKS ============\n");

        // Group by mod, then subgroup by category
        Map<String, Map<BlockRenderCategory, List<String>>> missingByModAndCategory = new TreeMap<>();

        // First, organize blocks by mod and then by category
        for (BlockRenderCategory category : BlockRenderCategory.values()) {
            Set<String> missingBlocks = categorizedMissingBlocks.get(category);
            if (missingBlocks == null || missingBlocks.isEmpty()) {
                continue;
            }

            for (String block : missingBlocks) {
                String[] parts = block.split(":", 2);
                if (parts.length == 2) {
                    String modId = parts[0];
                    String blockName = parts[1];

                    // Create nested map structure: modId -> category -> block list
                    missingByModAndCategory.computeIfAbsent(modId, k -> new HashMap<>()).computeIfAbsent(category, k -> new ArrayList<>()).add(blockName);
                }
            }
        }

        // Now write it organized by mod first, then by category
        for (Map.Entry<String, Map<BlockRenderCategory, List<String>>> modEntry : missingByModAndCategory.entrySet()) {
            String modId = modEntry.getKey();
            Map<BlockRenderCategory, List<String>> categorizedBlocks = modEntry.getValue();

            // Count total blocks for this mod across all categories
            int totalModBlocks = 0;
            for (List<String> blockList : categorizedBlocks.values()) {
                totalModBlocks += blockList.size();
            }

            writer.write("--- " + modId + " (" + totalModBlocks + ") ---\n");

            // For each category in this mod
            for (BlockRenderCategory category : BlockRenderCategory.values()) {
                List<String> blocks = categorizedBlocks.get(category);
                if (blocks == null || blocks.isEmpty()) {
                    continue;
                }

                // Show "non_full_blocks" instead of "solid" in the report
                String displayName = category == BlockRenderCategory.SOLID ? "non_full_blocks" : category.name();
                writer.write("-- " + displayName + " (" + blocks.size() + ") --\n");
                Collections.sort(blocks);
                for (String block : blocks) {
                    writer.write(modId + ":" + block + "\n");
                }
                writer.write("\n");
            }
            writer.write("\n");
        }
    }

    private static void writeFullBlockListByCategoryAndMod(BufferedWriter writer, Map<BlockRenderCategory, Map<String, List<String>>> categorizedBlocksByMod) throws IOException {
        writer.write("============ ALL BLOCKS ============\n");

        // Organize by mod instead of category
        Map<String, List<String>> allBlocksByMod = new TreeMap<>();

        // Combine all blocks from all categories by mod
        for (BlockRenderCategory category : BlockRenderCategory.values()) {
            Map<String, List<String>> blocksByMod = categorizedBlocksByMod.get(category);
            if (blocksByMod == null || blocksByMod.isEmpty()) {
                continue;
            }

            // Merge into the combined map
            for (Map.Entry<String, List<String>> entry : blocksByMod.entrySet()) {
                allBlocksByMod.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
            }
        }

        // Write blocks by mod
        for (Map.Entry<String, List<String>> entry : allBlocksByMod.entrySet()) {
            writer.write("--- " + entry.getKey() + " (" + entry.getValue().size() + ") ---\n");
            Collections.sort(entry.getValue());
            for (String block : entry.getValue()) {
                writer.write(entry.getKey() + ":" + block + "\n");
            }
            writer.write("\n");
        }
    }

    private static void writeUnusedShaderBlocks(BufferedWriter writer, Set<String> missingFromGame) throws IOException {
        if (missingFromGame.isEmpty()) {
            return;
        }

        Map<String, List<String>> unusedByNamespace = new TreeMap<>();
        for (String block : missingFromGame) {
            String[] parts = block.split(":", 2);
            if (parts.length == 2) {
                unusedByNamespace.computeIfAbsent(parts[0], k -> new ArrayList<>()).add(parts[1]);
            } else {
                // Handle case where there's no namespace
                unusedByNamespace.computeIfAbsent("unknown", k -> new ArrayList<>()).add(block);
            }
        }

        writer.write("============ UNUSED SHADER BLOCKS ============\n");
        for (Map.Entry<String, List<String>> entry : unusedByNamespace.entrySet()) {
            writer.write("--- " + entry.getKey() + " (" + entry.getValue().size() + ") ---\n");
            Collections.sort(entry.getValue());
            for (String block : entry.getValue()) {
                writer.write(entry.getKey() + ":" + block + "\n");
            }
            writer.write("\n");
        }
    }

    /**
     * Categorizes blocks by their render layers and mod.
     *
     * @param blocksByMod Map of blocks organized by mod
     * @return Map of render categories to maps of mods to block lists
     */
    private static Map<BlockRenderCategory, Map<String, List<String>>> getCategorizedBlocksByMod(Map<String, List<String>> blocksByMod) {
        // Ensure blocks are categorized
        if (BlockRenderHelper.getBlocksInCategory(BlockRenderCategory.SOLID).isEmpty()) {
            BlockRenderHelper.categorizeAllBlocks();
        }

        Map<BlockRenderCategory, Map<String, List<String>>> result = new HashMap<>();

        // Initialize the maps for each category
        for (BlockRenderCategory category : BlockRenderCategory.values()) {
            result.put(category, new TreeMap<>());
        }

        // Process each mod's blocks
        for (Map.Entry<String, List<String>> entry : blocksByMod.entrySet()) {
            String modId = entry.getKey();

            for (String blockPath : entry.getValue()) {
                String fullId = modId + ":" + blockPath;
                // Split into namespace and path for 1.21.4 compatibility
                String[] parts = fullId.split(":", 2);
                if (parts.length == 2) {
                    // Use getOrEmpty for 1.21.4 compatibility
                    Optional<Block> blockOptional = Registries.BLOCK.getOrEmpty(Identifier.of(parts[0], parts[1]));
                    if (blockOptional.isEmpty()) {
                        continue;
                    }
                    Block block = blockOptional.get();

                    BlockRenderCategory category = BlockRenderHelper.getRenderCategory(block);
                    result.get(category).computeIfAbsent(modId, k -> new ArrayList<>()).add(blockPath);
                }
            }
        }

        return result;
    }

    /**
     * Categorizes missing blocks by their render layers.
     *
     * @param missingBlocks Set of missing block identifiers
     * @return Map of render categories to sets of missing block identifiers
     */
    private static Map<BlockRenderCategory, Set<String>> categorizeMissingBlocks(Set<String> missingBlocks) {
        // Ensure blocks are categorized
        if (BlockRenderHelper.getBlocksInCategory(BlockRenderCategory.SOLID).isEmpty()) {
            BlockRenderHelper.categorizeAllBlocks();
        }

        Map<BlockRenderCategory, Set<String>> result = new HashMap<>();

        // Initialize sets for each category
        for (BlockRenderCategory category : BlockRenderCategory.values()) {
            result.put(category, new HashSet<>());
        }

        // Categorize each missing block
        for (String blockId : missingBlocks) {
            // Split into namespace and path for 1.21.2 compatibility
            String[] parts = blockId.split(":", 2);
            if (parts.length != 2) {
                continue; // Skip invalid identifiers
            }
            // Use getOrEmpty for 1.21.2 compatibility
            Optional<Block> blockOptional = Registries.BLOCK.getOrEmpty(Identifier.of(parts[0], parts[1]));
            if (blockOptional.isEmpty()) {
                continue;
            }
            Block block = blockOptional.get();

            BlockRenderCategory category = BlockRenderHelper.getRenderCategory(block);
            result.get(category).add(blockId);
        }

        return result;
    }
}