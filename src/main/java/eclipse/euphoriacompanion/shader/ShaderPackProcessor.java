package eclipse.euphoriacompanion.shader;

import eclipse.euphoriacompanion.EuphoriaCompanion;
import eclipse.euphoriacompanion.report.BlockReporter;
import eclipse.euphoriacompanion.util.BlockRegistryHelper;
import eclipse.euphoriacompanion.util.MCVersionChecker;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ShaderPackProcessor {
    private static final Path DEBUG_LOG_FILE = Paths.get("logs", "shader_blocks_debug.log");
    private static PrintWriter debugWriter;

    // Initialize debug writer
    private static void initDebugWriter() {
        try {
            // Create logs directory if it doesn't exist
            Files.createDirectories(DEBUG_LOG_FILE.getParent());

            // Create or overwrite the debug log file with timestamp header
            debugWriter = new PrintWriter(new BufferedWriter(new FileWriter(DEBUG_LOG_FILE.toFile())));
            debugWriter.println("--- Shader Block Debug Log - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " ---");
            debugWriter.flush();
        } catch (IOException e) {
            EuphoriaCompanion.LOGGER.error("Failed to create debug log file", e);
        }
    }

    // Write to debug log file
    private static void writeDebug(String message) {
        if (debugWriter == null) {
            initDebugWriter();
        }

        if (debugWriter != null) {
            debugWriter.println(message);
            debugWriter.flush(); // Flush so we see output even if program crashes
        }

        EuphoriaCompanion.LOGGER.debug(message);
    }

    // Close debug writer
    private static void closeDebugWriter() {
        if (debugWriter != null) {
            debugWriter.close();
        }
    }

    public static void processShaderPacks(Path gameDir) {
        // Initialize debug writer
        initDebugWriter();
        writeDebug("Starting shader pack processing");

        try {
            Path shaderpacksDir = gameDir.resolve("shaderpacks");
            boolean anyValidShaderpack = false;

            if (!Files.exists(shaderpacksDir)) {
                try {
                    Files.createDirectories(shaderpacksDir);
                    EuphoriaCompanion.LOGGER.info("Created shaderpacks directory");
                    writeDebug("Created shaderpacks directory at " + shaderpacksDir);
                } catch (IOException e) {
                    EuphoriaCompanion.LOGGER.error("Failed to create shaderpacks directory", e);
                    writeDebug("ERROR: Failed to create shaderpacks directory: " + e.getMessage());
                    return;
                }
            }

            // Load all blocks by mod
            Map<String, List<String>> blocksByMod = new TreeMap<>();
            Set<String> gameBlocks = BlockRegistryHelper.getGameBlocks(blocksByMod);
            writeDebug("Loaded " + gameBlocks.size() + " game blocks from " + blocksByMod.size() + " mods");

            // Export block categories for shader developers
            EuphoriaCompanion.exportBlockCategories();
            writeDebug("Exported block render categories to JSON");

            Path logsDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("logs");
            if (!Files.exists(logsDir)) {
                try {
                    Files.createDirectories(logsDir);
                    writeDebug("Created logs directory at " + logsDir);
                } catch (IOException e) {
                    EuphoriaCompanion.LOGGER.error("Failed to create logs directory", e);
                    writeDebug("ERROR: Failed to create logs directory: " + e.getMessage());
                    return;
                }
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacksDir)) {
                for (Path shaderpackPath : stream) {
                    String shaderpackName = shaderpackPath.getFileName().toString();
                    Set<String> shaderBlocks;

                    if (Files.isDirectory(shaderpackPath)) {
                        EuphoriaCompanion.LOGGER.info("Processing shaderpack (Directory): {}", shaderpackName);
                        writeDebug("Processing shaderpack (Directory): " + shaderpackName);
                        shaderBlocks = readShaderBlockProperties(shaderpackPath);
                    } else if (Files.isRegularFile(shaderpackPath) && shaderpackName.toLowerCase().endsWith(".zip")) {
                        EuphoriaCompanion.LOGGER.info("Processing shaderpack (ZIP): {}", shaderpackName);
                        writeDebug("Processing shaderpack (ZIP): " + shaderpackName);
                        try (FileSystem zipFs = FileSystems.newFileSystem(shaderpackPath, (ClassLoader) null)) {
                            Path root = zipFs.getPath("/");
                            shaderBlocks = readShaderBlockProperties(root);
                        } catch (IOException e) {
                            EuphoriaCompanion.LOGGER.error("Failed to process zip file {}", shaderpackName, e);
                            writeDebug("ERROR: Failed to process zip file " + shaderpackName + ": " + e.getMessage());
                            continue;
                        }
                    } else {
                        EuphoriaCompanion.LOGGER.info("Skipping {} - not a directory or zip file", shaderpackName);
                        writeDebug("Skipping " + shaderpackName + " - not a directory or zip file");
                        continue;
                    }

                    if (!shaderBlocks.isEmpty()) {
                        anyValidShaderpack = true;
                        writeDebug("Found " + shaderBlocks.size() + " blocks in " + shaderpackName);
                    } else {
                        writeDebug("No blocks found in " + shaderpackName);
                    }
                    BlockReporter.processShaderBlocks(shaderpackName, shaderBlocks, gameBlocks, logsDir, blocksByMod);
                }

                if (!anyValidShaderpack) {
                    EuphoriaCompanion.LOGGER.error("No valid shaderpacks found!");
                    writeDebug("ERROR: No valid shaderpacks found!");
                }
            } catch (IOException e) {
                EuphoriaCompanion.LOGGER.error("Failed to process shaderpacks directory", e);
                writeDebug("ERROR: Failed to process shaderpacks directory: " + e.getMessage());
            }
        } finally {
            writeDebug("Completed shader pack processing");
            closeDebugWriter();
        }
    }

    private static Set<String> readShaderBlockProperties(Path shaderpackPath) {
        Set<String> shaderBlocks = new HashSet<>();
        Path blockPropertiesPath = shaderpackPath.resolve("shaders/block.properties");
        if (!Files.exists(blockPropertiesPath)) {
            EuphoriaCompanion.LOGGER.warn("No block.properties found in {}", shaderpackPath);
            writeDebug("No block.properties found in " + shaderpackPath);
            return shaderBlocks;
        }

        writeDebug("Reading block.properties from " + blockPropertiesPath);
        int mcVersion = MCVersionChecker.getMCVersion();
        Deque<Boolean> conditionStack = new ArrayDeque<>();

        try (BufferedReader reader = Files.newBufferedReader(blockPropertiesPath)) {
            StringBuilder currentLine = new StringBuilder();
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Handle preprocessor directives
                if (line.startsWith("#if ")) {
                    String condition = line.substring(4).trim();
                    boolean conditionMet = MCVersionChecker.evaluateCondition(condition, mcVersion);
                    boolean currentActive = isActive(conditionStack);
                    conditionStack.push(currentActive && conditionMet);
                    writeDebug("Line " + lineNumber + ": Preprocessor #if " + condition + " -> " + (currentActive && conditionMet));
                    continue;
                } else if (line.equals("#else")) {
                    if (!conditionStack.isEmpty()) {
                        boolean top = conditionStack.pop();
                        conditionStack.push(!top);
                        writeDebug("Line " + lineNumber + ": Preprocessor #else -> " + (!top));
                    }
                    continue;
                } else if (line.equals("#endif")) {
                    if (!conditionStack.isEmpty()) {
                        conditionStack.pop();
                        writeDebug("Line " + lineNumber + ": Preprocessor #endif");
                    }
                    continue;
                }

                boolean skipProcessing = !isActive(conditionStack);

                if (skipProcessing) {
                    currentLine.setLength(0); // Discard any accumulated line
                    writeDebug("Line " + lineNumber + ": Skipped due to preprocessor condition");
                    continue;
                }

                if (line.isEmpty() || line.startsWith("#")) {
                    currentLine.setLength(0);
                    continue;
                }

                if (line.endsWith("\\")) {
                    currentLine.append(line, 0, line.length() - 1).append(" ");
                    writeDebug("Line " + lineNumber + ": Continuation line detected");
                } else {
                    currentLine.append(line);
                    String fullLine = currentLine.toString().trim();
                    writeDebug("Line " + lineNumber + ": Processing complete line: " + fullLine);
                    processBlockLine(fullLine, shaderBlocks);
                    currentLine.setLength(0);
                }
            }
            EuphoriaCompanion.LOGGER.info("Total blocks read from shader properties: {}", shaderBlocks.size());
            writeDebug("Total blocks read from shader properties: " + shaderBlocks.size());
        } catch (IOException e) {
            EuphoriaCompanion.LOGGER.error("Failed to read block.properties file", e);
            writeDebug("ERROR: Failed to read block.properties file: " + e.getMessage());
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
        if (!fullLine.contains("=")) {
            writeDebug("  Skipping line - no '=' character");
            return;
        }

        String[] keyValue = fullLine.split("=", 2);
        if (keyValue.length < 2) {
            writeDebug("  Skipping line - malformed key-value pair");
            return;
        }

        String blockProperty = keyValue[0].trim();
        String blockValues = keyValue[1].trim();

        writeDebug("  Processing entry: Property '" + blockProperty + "' with values '" + blockValues + "'");

        for (String blockId : blockValues.split("\\s+")) {
            String trimmedId = blockId.trim();
            if (trimmedId.isEmpty() || trimmedId.startsWith("tags_")) {
                writeDebug("    Skipping block entry: '" + trimmedId + "' (empty or tags entry)");
                continue;
            }

            writeDebug("    Analyzing block ID: '" + trimmedId + "'");
            String blockToAdd = getString(trimmedId);

            if (blockToAdd != null) {
                boolean isNewBlock = shaderBlocks.add(blockToAdd);
                if (isNewBlock) {
                    writeDebug("    ADDED block '" + blockToAdd + "' from property '" + blockProperty + "' and value '" + trimmedId + "'");
                } else {
                    writeDebug("    Block '" + blockToAdd + "' already exists (from property '" + blockProperty + "' and value '" + trimmedId + "')");
                }
            } else {
                writeDebug("    Could not process block value: '" + trimmedId + "' from property '" + blockProperty + "'");
            }
        }
    }

    private static @Nullable String getString(String trimmedId) {
        String[] segments = trimmedId.split(":");
        List<String> validSegments = new ArrayList<>();
        for (String segment : segments) {
            if (segment.contains("=")) {
                writeDebug("      Found '=' in segment '" + segment + "', stopping segment processing");
                break;
            }
            validSegments.add(segment);
        }

        writeDebug("      Valid segments: " + String.join(", ", validSegments));
        String blockToAdd = null;

        if (validSegments.size() >= 2) {
            int lastIndex = validSegments.size() - 1;
            boolean hasMetadata = isNumeric(validSegments.get(lastIndex));
            writeDebug("      Segments >= 2, last segment '" + validSegments.get(lastIndex) + "' isNumeric: " + hasMetadata);

            if (validSegments.size() == 2 && hasMetadata) {
                blockToAdd = "minecraft:" + validSegments.get(0);
                writeDebug("      Interpreting as minecraft block with metadata: " + blockToAdd);
            } else if (hasMetadata) {
                blockToAdd = validSegments.get(0) + ":" + validSegments.get(1);
                writeDebug("      Interpreting as modded block with metadata: " + blockToAdd);
            } else {
                blockToAdd = validSegments.get(0) + ":" + validSegments.get(1);
                writeDebug("      Interpreting as modded block: " + blockToAdd);
            }
        } else if (validSegments.size() == 1) {
            blockToAdd = "minecraft:" + validSegments.get(0);
            writeDebug("      Interpreting as minecraft block: " + blockToAdd);
        } else {
            writeDebug("      Could not determine block ID from segments");
        }
        return blockToAdd;
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