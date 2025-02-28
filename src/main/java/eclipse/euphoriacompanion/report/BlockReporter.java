package eclipse.euphoriacompanion.report;

import eclipse.euphoriacompanion.EuphoriaCompanion;

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

        writeComparisonFile(comparisonPath, shaderpackName, gameBlocks, shaderBlocks, missingFromShader, missingFromGame, blocksByMod);
    }

    private static void writeComparisonFile(Path outputPath, String shaderpackName, Set<String> gameBlocks, Set<String> shaderBlocks, Set<String> missingFromShader, Set<String> missingFromGame, Map<String, List<String>> blocksByMod) {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("=== Block Comparison Summary for " + shaderpackName + " ===\n");
            writer.write(String.format("Total blocks in game: %d\n", gameBlocks.size()));
            writer.write(String.format("Total blocks in shader: %d\n", shaderBlocks.size()));
            writer.write(String.format("Unused blocks from shader: %d\n", missingFromGame.size()));
            writer.write(String.format("Blocks missing from shader: %d\n\n", missingFromShader.size()));

            writeMissingBlocksByMod(writer, missingFromShader);
            writeFullBlockList(writer, blocksByMod);
            writeUnusedShaderBlocks(writer, missingFromGame);

            EuphoriaCompanion.LOGGER.info("Report written to {}", outputPath);
        } catch (IOException e) {
            EuphoriaCompanion.LOGGER.error("Failed to write report", e);
        }
    }

    private static void writeMissingBlocksByMod(BufferedWriter writer, Set<String> missingFromShader) throws IOException {
        Map<String, List<String>> missingByMod = new TreeMap<>();
        for (String block : missingFromShader) {
            String[] parts = block.split(":", 2);
            missingByMod.computeIfAbsent(parts[0], k -> new ArrayList<>()).add(parts[1]);
        }

        writer.write("=== Missing Blocks By Mod ===\n");
        for (Map.Entry<String, List<String>> entry : missingByMod.entrySet()) {
            writer.write("--- " + entry.getKey() + " (" + entry.getValue().size() + ") ---\n");
            Collections.sort(entry.getValue());
            for (String block : entry.getValue()) {
                writer.write(entry.getKey() + ":" + block + "\n");
            }
            writer.write("\n");
        }
        writer.write("\n");
    }

    private static void writeFullBlockList(BufferedWriter writer, Map<String, List<String>> blocksByMod) throws IOException {
        writer.write("=== All Blocks By Mod ===\n");
        for (Map.Entry<String, List<String>> entry : blocksByMod.entrySet()) {
            writer.write("=== " + entry.getKey() + " (" + entry.getValue().size() + ") ===\n");
            Collections.sort(entry.getValue());
            for (String block : entry.getValue()) {
                writer.write(entry.getKey() + ":" + block + "\n");
            }
            writer.write("\n");
        }
    }

    private static void writeUnusedShaderBlocks(BufferedWriter writer, Set<String> missingFromGame) throws IOException {
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

        writer.write("=== Unused Shader Blocks (Not Found In Game) ===\n");
        for (Map.Entry<String, List<String>> entry : unusedByNamespace.entrySet()) {
            writer.write("--- " + entry.getKey() + " (" + entry.getValue().size() + ") ---\n");
            Collections.sort(entry.getValue());
            for (String block : entry.getValue()) {
                writer.write(entry.getKey() + ":" + block + "\n");
            }
            writer.write("\n");
        }
        writer.write("\n");
    }
}