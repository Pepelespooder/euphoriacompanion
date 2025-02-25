package eclipse.euphoriacompanion.shader;

import eclipse.euphoriacompanion.EuphoriaCompanion;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static eclipse.euphoriacompanion.report.BlockReporter.processShaderBlocks;
import static eclipse.euphoriacompanion.util.BlockRegistryHelper.getGameBlocks;
import static eclipse.euphoriacompanion.util.MCVersionChecker.evaluateCondition;
import static eclipse.euphoriacompanion.util.MCVersionChecker.getMCVersion;

public class ShaderPackProcessor {
    public static void processShaderPacks(Path gameDir) {
        Path shaderpacksDir = gameDir.resolve("shaderpacks");
        boolean anyValidShaderpack = false;

        if (!Files.exists(shaderpacksDir)) {
            try {
                Files.createDirectories(shaderpacksDir);
                EuphoriaCompanion.LOGGER.info("Created shaderpacks directory");
            } catch (IOException e) {
                EuphoriaCompanion.LOGGER.error("Failed to create shaderpacks directory", e);
                return;
            }
        }

        Map<String, List<String>> blocksByMod = new TreeMap<>();
        Set<String> gameBlocks = getGameBlocks(blocksByMod);

        Path logsDir = Minecraft.getMinecraft().gameDir.toPath().resolve("logs");
        if (!Files.exists(logsDir)) {
            try {
                Files.createDirectories(logsDir);
            } catch (IOException e) {
                EuphoriaCompanion.LOGGER.error("Failed to create logs directory", e);
                return;
            }
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacksDir)) {
            for (Path shaderpackPath : stream) {
                String shaderpackName = shaderpackPath.getFileName().toString();
                Set<String> shaderBlocks;

                if (Files.isDirectory(shaderpackPath)) {
                    EuphoriaCompanion.LOGGER.info("Processing shaderpack (directory): {}", shaderpackName);
                    shaderBlocks = readShaderBlockProperties(shaderpackPath);
                } else if (Files.isRegularFile(shaderpackPath) && shaderpackName.toLowerCase().endsWith(".zip")) {
                    EuphoriaCompanion.LOGGER.info("Processing shaderpack (ZIP): {}", shaderpackName);
                    try (FileSystem zipFs = FileSystems.newFileSystem(shaderpackPath, null)) {
                        Path root = zipFs.getPath("/");
                        shaderBlocks = readShaderBlockProperties(root);
                    } catch (IOException e) {
                        EuphoriaCompanion.LOGGER.error("Failed to process zip file {}", shaderpackName, e);
                        continue;
                    }
                } else {
                    EuphoriaCompanion.LOGGER.info("Skipping {} - not a directory or zip file", shaderpackName);
                    continue;
                }

                if (!shaderBlocks.isEmpty()) {
                    anyValidShaderpack = true;
                }
                processShaderBlocks(shaderpackName, shaderBlocks, gameBlocks, logsDir, blocksByMod);
            }

            if (!anyValidShaderpack) {
                EuphoriaCompanion.LOGGER.error("No valid shaderpacks found!");
            }
        } catch (IOException e) {
            EuphoriaCompanion.LOGGER.error("Failed to process shaderpacks directory", e);
        }
    }

    private static Set<String> readShaderBlockProperties(Path shaderpackPath) {
        Set<String> shaderBlocks = new HashSet<>();
        Path blockPropertiesPath = shaderpackPath.resolve("shaders/block.properties");
        if (!Files.exists(blockPropertiesPath)) {
            EuphoriaCompanion.LOGGER.warn("No block.properties found in {}", shaderpackPath);
            return shaderBlocks;
        }

        int mcVersion = getMCVersion();
        boolean skip = false;

        try (BufferedReader reader = Files.newBufferedReader(blockPropertiesPath)) {
            StringBuilder currentLine = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Handle preprocessor directives
                if (line.startsWith("#if ")) {
                    String condition = line.substring(4).trim();
                    boolean conditionMet = evaluateCondition(condition, mcVersion);
                    skip = !conditionMet;
                    continue;
                } else if (line.equals("#else")) {
                    skip = !skip;
                    continue;
                } else if (line.equals("#endif")) {
                    skip = false;
                    continue;
                }

                if (skip) {
                    currentLine.setLength(0); // Discard any accumulated line
                    continue;
                }

                if (line.isEmpty() || line.startsWith("#")) {
                    currentLine.setLength(0);
                    continue;
                }

                if (line.endsWith("\\")) {
                    currentLine.append(line, 0, line.length() - 1).append(" ");
                } else {
                    currentLine.append(line);
                    String fullLine = currentLine.toString().trim();
                    processBlockLine(fullLine, shaderBlocks);
                    currentLine.setLength(0);
                }
            }
            EuphoriaCompanion.LOGGER.info("Total blocks read from shader properties: {}", shaderBlocks.size());
        } catch (IOException e) {
            EuphoriaCompanion.LOGGER.error("Failed to read block.properties file", e);
        }
        return shaderBlocks;
    }

    private static void processBlockLine(String fullLine, Set<String> shaderBlocks) {
        if (fullLine.contains("=")) {
            String[] parts = fullLine.split("=", 2);
            if (parts.length > 1) {
                String[] blockIds = parts[1].trim().split("\\s+");
                for (String blockId : blockIds) {
                    blockId = blockId.trim();
                    if (!blockId.isEmpty() && !blockId.startsWith("tags_")) {
                        String[] allSegments = blockId.split(":");
                        List<String> cleanSegments = new ArrayList<>();
                        for (String segment : allSegments) {
                            if (!segment.contains("=")) {
                                cleanSegments.add(segment);
                            }
                        }
                        if (cleanSegments.size() >= 2) {
                            String baseBlockId = cleanSegments.get(0) + ":" + cleanSegments.get(1);
                            shaderBlocks.add(baseBlockId);
                        } else if (cleanSegments.size() == 1) {
                            shaderBlocks.add("minecraft:" + cleanSegments.get(0));
                        }
                    }
                }
            }
        }
    }

}