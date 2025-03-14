package eclipse.euphoriacompanion.shader;

import eclipse.euphoriacompanion.EuphoriaCompanion;
import eclipse.euphoriacompanion.report.BlockReporter;
import eclipse.euphoriacompanion.util.BlockPropertyExtractor;
import eclipse.euphoriacompanion.util.BlockPropertyRegistry;
import eclipse.euphoriacompanion.util.BlockRegistryHelper;
import eclipse.euphoriacompanion.util.MCVersionChecker;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
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
        
        // Clear all caches to ensure we read fresh data
        BlockPropertyExtractor.clearCaches();
        
        // Reset the singleton instance of the registry completely to force a full reload
        BlockPropertyRegistry.resetInstance();
        BlockPropertyRegistry.getInstance(gameDir).clearAll();
        writeDebug("Cleared all property caches and reset registry instance");

        // Using our improved BlockPropertyRegistry for property comparison
        writeDebug("Using improved BlockPropertyRegistry for property detection");

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

            // Block categories will be exported after world ready

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

            // Create a combined set of all shader blocks
            Set<String> allShaderBlocks = new HashSet<>();
            
            // First scan for all shader blocks
            try (DirectoryStream<Path> scanStream = Files.newDirectoryStream(shaderpacksDir)) {
                for (Path shaderpackPath : scanStream) {
                    if (Files.isDirectory(shaderpackPath) || 
                        (Files.isRegularFile(shaderpackPath) && shaderpackPath.getFileName().toString().toLowerCase().endsWith(".zip"))) {
                        Set<String> packBlocks = readShaderBlockProperties(shaderpackPath);
                        allShaderBlocks.addAll(packBlocks);
                    }
                }
            } catch (IOException e) {
                EuphoriaCompanion.LOGGER.error("Failed to scan shaderpacks directory", e);
                writeDebug("ERROR: Failed to scan shaderpacks directory: " + e.getMessage());
            }
            
            // No longer using property registry - using direct comparison instead
            
            // Now process each shader pack
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacksDir)) {
                for (Path shaderpackPath : stream) {
                    String shaderpackName = shaderpackPath.getFileName().toString();
                    Set<String> shaderBlocks;
                    Map<String, Set<BlockPropertyExtractor.BlockStateProperty>> blockPropertiesMap = new HashMap<>();

                    if (Files.isDirectory(shaderpackPath)) {
                        EuphoriaCompanion.LOGGER.info("Processing shaderpack (Directory): {}", shaderpackName);
                        writeDebug("Processing shaderpack (Directory): " + shaderpackName);
                        shaderBlocks = readShaderBlockProperties(shaderpackPath);
                        
                        // Extract any block properties specified in the shader files
                        Path blockPropertiesPath = shaderpackPath.resolve("shaders/block.properties");
                        if (Files.exists(blockPropertiesPath)) {
                            blockPropertiesMap = BlockPropertyExtractor.parsePropertiesFile(blockPropertiesPath);
                            
                            // No need to register properties - using direct comparison
                        }
                    } else if (Files.isRegularFile(shaderpackPath) && shaderpackName.toLowerCase().endsWith(".zip")) {
                        EuphoriaCompanion.LOGGER.info("Processing shaderpack (ZIP): {}", shaderpackName);
                        writeDebug("Processing shaderpack (ZIP): " + shaderpackName);
                        try (FileSystem zipFs = FileSystems.newFileSystem(shaderpackPath, (ClassLoader) null)) {
                            Path root = zipFs.getPath("/");
                            shaderBlocks = readShaderBlockProperties(root);
                            
                            // Extract any block properties from ZIP
                            Path blockPropertiesPath = zipFs.getPath("/shaders/block.properties");
                            if (Files.exists(blockPropertiesPath)) {
                                blockPropertiesMap = BlockPropertyExtractor.parsePropertiesFile(blockPropertiesPath);
                                
                                // No need to register properties - using direct comparison
                            }
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

                    // Add all specific blockstates to shader blocks
                    Set<String> expandedShaderBlocks = new HashSet<>(shaderBlocks);
                    ensureAllBlockstatesAdded(expandedShaderBlocks, blockPropertiesMap);
                    
                    if (!expandedShaderBlocks.isEmpty()) {
                        anyValidShaderpack = true;
                        writeDebug("Found " + expandedShaderBlocks.size() + " blocks in " + shaderpackName + 
                                   " (including " + (expandedShaderBlocks.size() - shaderBlocks.size()) + " expanded blockstates)");
                    } else {
                        writeDebug("No blocks found in " + shaderpackName);
                    }
                    BlockReporter.processShaderBlocks(shaderpackName, expandedShaderBlocks, gameBlocks, logsDir, blocksByMod, blockPropertiesMap);
                }

                if (!anyValidShaderpack) {
                    EuphoriaCompanion.LOGGER.error("No valid shaderpacks found!");
                    writeDebug("ERROR: No valid shaderpacks found!");
                }
                
                // Use our improved BlockPropertyRegistry for finding missing properties
                BlockPropertyRegistry registry = BlockPropertyRegistry.getInstance(gameDir);
                
                // Process all shader blocks to build the used properties registry
                registry.processAllShaderBlocks(allShaderBlocks);
                
                // Find missing property states using our improved registry
                List<String> missingPropertyStates = registry.findAllMissingPropertyStates();
                writeDebug("Found " + missingPropertyStates.size() + " missing property states using the improved registry");
                
                // Write missing property states to a file
                // Re-use the logs directory already created earlier
                Path missingPropertiesPath = logsDir.resolve("missing_property_states.txt");
                try (BufferedWriter writer = Files.newBufferedWriter(missingPropertiesPath)) {
                    writer.write("============ MISSING PROPERTY STATES ============\n");
                    writer.write("The following property states are missing from the shaders:\n\n");
                    
                    for (String missingState : missingPropertyStates) {
                        writer.write(missingState + "\n");
                    }
                    
                    EuphoriaCompanion.LOGGER.info("Wrote {} missing property states to {}", 
                        missingPropertyStates.size(), missingPropertiesPath);
                    
                    // Save the registry for future use
                    registry.saveRegistry();
                } catch (IOException e) {
                    EuphoriaCompanion.LOGGER.error("Failed to write missing property states", e);
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
            // Log total blocks count to debug only
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

        // Split right side values into tokens - all logging is handled by writeDebug
        String[] rightValues = blockValues.split("\\s+");
        
        writeDebug("  Processing entry: Property '" + blockProperty + "' with values '" + blockValues + "'");


        // Check if this line contains block state properties
        boolean hasBlockStateProperties = blockProperty.contains(":");
        
        for (String blockId : blockValues.split("\\s+")) {
            String trimmedId = blockId.trim();
            if (trimmedId.isEmpty() || trimmedId.startsWith("tags_")) {
                writeDebug("    Skipping block entry: '" + trimmedId + "' (empty or tags entry)");
                continue;
            }

            writeDebug("    Analyzing block ID: '" + trimmedId + "'");

            
            if (hasBlockStateProperties) {
                // Use our improved BlockPropertyExtractor to correctly parse property formats
                writeDebug("    Processing block with potential properties: " + blockProperty);
                
                // Parse the block identifier to extract block name and properties
                BlockPropertyExtractor.ParsedBlockIdentifier parsed = 
                    BlockPropertyExtractor.parseBlockIdentifier(blockProperty);
                
                // Log what we've parsed
                writeDebug("    Parsed block name: " + parsed.blockName + " with " + 
                          parsed.properties.size() + " properties");
                
                // If we found properties
                if (!parsed.properties.isEmpty()) {
                    // If this is a vanilla block without namespace, add the minecraft namespace
                    String blockToAdd = blockProperty;
                    
                    if (!parsed.blockName.contains(":")) {
                        // Add minecraft namespace for vanilla blocks
                        String vanillaBlock = "minecraft:" + parsed.blockName;
                        writeDebug("    Converting vanilla block with properties: " + blockProperty + " -> " + vanillaBlock);
                        
                        // Reconstruct with minecraft namespace
                        StringBuilder sb = new StringBuilder(vanillaBlock);
                        for (BlockPropertyExtractor.BlockStateProperty prop : parsed.properties) {
                            sb.append(":").append(prop.getName()).append("=").append(prop.getValue());
                        }
                        blockToAdd = sb.toString();
                    }
                    
                    boolean isNewBlock = shaderBlocks.add(blockToAdd);
                    writeDebug("    ADDED block with properties '" + blockToAdd + "' from value '" + trimmedId + "'");
                } else {
                    // No properties found, add as regular block
                    boolean isNewBlock = shaderBlocks.add(blockProperty);
                    writeDebug("    ADDED block '" + blockProperty + "' from value '" + trimmedId + "'");
                }
            } else {
                // For regular blocks without properties, use the old method
                String blockToAdd = getString(trimmedId);
                if (blockToAdd != null) {
                    // Special case for when we detect a property format (with colons and = sign) in the value
                    if (trimmedId.contains(":") && trimmedId.contains("=")) {
                        writeDebug("    Detected property format in value: " + trimmedId);
                        
                        // Keep the original format for property detection
                        boolean isNewBlock = shaderBlocks.add(trimmedId);
                        if (isNewBlock) {
                            writeDebug("    ADDED property block '" + trimmedId + "' from property '" + blockProperty + "'");
                        } else {
                            writeDebug("    Property block '" + trimmedId + "' already exists from property '" + blockProperty + "'");
                        }
                    } else {
                        // Regular block without properties
                        boolean isNewBlock = shaderBlocks.add(blockToAdd);
                        if (isNewBlock) {
                            writeDebug("    ADDED block '" + blockToAdd + "' from property '" + blockProperty + "' and value '" + trimmedId + "'");
                        } else {
                            writeDebug("    Block '" + blockToAdd + "' already exists (from property '" + blockProperty + "' and value '" + trimmedId + "')");
                        }
                    }
                } else {
                    writeDebug("    Could not process block value: '" + trimmedId + "' from property '" + blockProperty + "'");
                }
            }
        }
    }

    private static @Nullable String getString(String trimmedId) {
        // Special handling for property formats based on patterns, not specific block names
        if (trimmedId.contains(":") && trimmedId.contains("=")) {
            writeDebug("      Processing potential block with properties: " + trimmedId);
            
            // Use our improved BlockPropertyExtractor to correctly handle property parsing
            BlockPropertyExtractor.ParsedBlockIdentifier parsed = 
                BlockPropertyExtractor.parseBlockIdentifier(trimmedId);
            
            writeDebug("      Parsed block name: " + parsed.blockName + " with " + 
                      parsed.properties.size() + " properties");
                      
            // Found properties, return the trimmed ID as is since it contains property information
            if (!parsed.properties.isEmpty()) {
                // If this is a vanilla block without namespace, add the minecraft namespace
                if (!parsed.blockName.contains(":")) {
                    String vanillaBlock = "minecraft:" + parsed.blockName;
                    writeDebug("      Converting vanilla block with properties: " + trimmedId + " -> " + vanillaBlock);
                    
                    // Reconstruct with minecraft namespace
                    StringBuilder sb = new StringBuilder(vanillaBlock);
                    for (BlockPropertyExtractor.BlockStateProperty prop : parsed.properties) {
                        sb.append(":").append(prop.getName()).append("=").append(prop.getValue());
                    }
                    return sb.toString();
                }
                
                writeDebug("      Found block with properties: " + trimmedId);
                return trimmedId;
            }
            // Otherwise, proceed with normal processing
        }
        
        // Normal processing for regular blocks
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
    
    /**
     * Processes blocks with properties and identifies missing property states.
     * This optimized version only checks properties that are actually used by the user,
     * rather than checking every possible block variation.
     * 
     * @param shaderBlocks Set of blocks to modify
     * @param blockPropertiesMap Map of blockIDs to their properties from the properties file
     */
    private static void ensureAllBlockstatesAdded(Set<String> shaderBlocks, 
            Map<String, Set<BlockPropertyExtractor.BlockStateProperty>> blockPropertiesMap) {
        if (blockPropertiesMap.isEmpty()) {
            writeDebug("No block properties map provided, skipping property checks");
            return;
        }
        
        writeDebug("Processing properties for " + blockPropertiesMap.size() + " block types");
        
        // Track blocks with their used properties
        // Map of base block ID -> property name -> set of property values
        Map<String, Map<String, Set<String>>> blockPropertiesUsed = new HashMap<>();
        
        // Track blocks with their used properties without registry
        for (String blockId : shaderBlocks) {
            // Skip blocks without properties
            if (!blockId.contains("=")) {
                continue;
            }
            
            // Extract properties using our parser
            BlockPropertyExtractor.ParsedBlockIdentifier parsed = 
                BlockPropertyExtractor.parseBlockIdentifier(blockId);
            
            // Skip if no properties found
            if (parsed.properties.isEmpty()) {
                continue;
            }
            
            String baseBlockId = parsed.blockName;
            if (!baseBlockId.contains(":")) {
                baseBlockId = "minecraft:" + baseBlockId;
            }
            
            // Store used properties
            Map<String, Set<String>> blockProps = blockPropertiesUsed.computeIfAbsent(baseBlockId, k -> new HashMap<>());
            
            // Add each property value
            for (BlockPropertyExtractor.BlockStateProperty prop : parsed.properties) {
                blockProps.computeIfAbsent(prop.getName(), k -> new HashSet<>()).add(prop.getValue());
                writeDebug("Found property: " + baseBlockId + ":" + prop.getName() + "=" + prop.getValue());
            }
        }
    }
    
    
    /**
     * Find a property with the given name on a block
     */
    private static Optional<Property<?>> findBlockProperty(Block block, String propertyName) {
        for (Property<?> property : block.getStateManager().getProperties()) {
            if (property.getName().equals(propertyName)) {
                return Optional.of(property);
            }
        }
        return Optional.empty();
    }
}