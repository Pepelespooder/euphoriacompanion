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
                    EuphoriaCompanion.LOGGER.info("Processing shaderpack (Directory): {}", shaderpackName);
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
        Deque<Boolean> conditionStack = new ArrayDeque<>();

        try (BufferedReader reader = Files.newBufferedReader(blockPropertiesPath)) {
            StringBuilder currentLine = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Handle preprocessor directives
                if (line.startsWith("#if ")) {
                    String condition = line.substring(4).trim();
                    boolean conditionMet = evaluateCondition(condition, mcVersion);
                    boolean currentActive = isActive(conditionStack);
                    conditionStack.push(currentActive && conditionMet);
                    continue;
                } else if (line.equals("#else")) {
                    if (!conditionStack.isEmpty()) {
                        boolean top = conditionStack.pop();
                        conditionStack.push(!top);
                    }
                    continue;
                } else if (line.equals("#endif")) {
                    if (!conditionStack.isEmpty()) {
                        conditionStack.pop();
                    }
                    continue;
                }

                boolean skipProcessing = !isActive(conditionStack);

                if (skipProcessing) {
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

    private static boolean isActive(Deque<Boolean> conditionStack) {
        for (Boolean condition : conditionStack) {
            if (!condition) {
                return false;
            }
        }
        return true;
    }

    private static void processBlockLine(String fullLine, Set<String> shaderBlocks) {
        if (!fullLine.contains("=")) return;

        String[] keyValue = fullLine.split("=", 2);
        if (keyValue.length < 2) return;

        for (String blockId : keyValue[1].trim().split("\\s+")) {
            String trimmedId = blockId.trim();
            if (trimmedId.isEmpty() || trimmedId.startsWith("tags_")) continue;

            String[] segments = trimmedId.split(":");
            List<String> validSegments = new ArrayList<>();
            for (String segment : segments) {
                if (segment.contains("=")) break;
                validSegments.add(segment);
            }

            if (validSegments.size() >= 2) {
                int lastIndex = validSegments.size() - 1;
                boolean hasMetadata = isNumeric(validSegments.get(lastIndex));

                if (validSegments.size() == 2 && hasMetadata) {
                    shaderBlocks.add("minecraft:" + validSegments.get(0));
                } else if (hasMetadata) {
                    shaderBlocks.add(validSegments.get(0) + ":" + validSegments.get(1));
                } else {
                    shaderBlocks.add(validSegments.get(0) + ":" + validSegments.get(1));
                }
            } else if (validSegments.size() == 1) {
                shaderBlocks.add("minecraft:" + validSegments.get(0));
            }
        }
    }

    private static boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

}