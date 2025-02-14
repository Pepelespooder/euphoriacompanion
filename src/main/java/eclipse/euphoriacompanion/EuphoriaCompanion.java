package eclipse.euphoriacompanion;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;


public class EuphoriaCompanion implements ModInitializer {
	public static final String MOD_ID = "euphoriacompanion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static RegistryWrapper REGISTRY;

	// Lazy initialization of registry
	private static synchronized RegistryWrapper getRegistry() {
		if (REGISTRY == null) {
			REGISTRY = RegistryFactory.createRegistryWrapper();
		}
		return REGISTRY;
	}


	private static Set<String> readShaderBlockProperties(Path shaderpackPath) {
		Set<String> shaderBlocks = new HashSet<>();
		Path blockPropertiesPath = shaderpackPath.resolve("shaders/block.properties");

		// Skip if block.properties doesn't exist
		if (!Files.exists(blockPropertiesPath)) {
			LOGGER.warn("No block.properties found in {}", shaderpackPath);
			return shaderBlocks;
		}

		try (BufferedReader reader = new BufferedReader(new FileReader(blockPropertiesPath.toFile()))) {
			StringBuilder currentLine = new StringBuilder();
			String line;

			while ((line = reader.readLine()) != null) {
				line = line.trim();

				// Skip empty lines and comments
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}

				// If line ends with backslash, accumulate lines
				if (line.endsWith("\\")) {
					currentLine.append(line, 0, line.length() - 1).append(" ");
					continue;
				}

				// Add the final line to complete the entry
				currentLine.append(line);

				// Process the complete entry
				String fullLine = currentLine.toString().trim();
				if (fullLine.contains("=")) {
					String[] parts = fullLine.split("=", 2);
					if (parts.length > 1) {
						// Split the right side of the = sign to get block IDs
						String[] blockIds = parts[1].trim().split("\\s+");
						for (String blockId : blockIds) {
							blockId = blockId.trim();
							// Skip empty strings and "tags_" entries
							if (!blockId.isEmpty() && !blockId.startsWith("tags_")) {
								String baseBlockId;

								// Handle block properties
								String[] segments = blockId.split(":");
								if (segments.length > 1) {
									// Check if any segment after the first one contains an equals sign
									boolean hasProperties = false;
									int baseBlockEndIndex = 0;

									for (int i = 1; i < segments.length; i++) {
										if (segments[i].contains("=")) {
											hasProperties = true;
											baseBlockEndIndex = i;
											break;
										}
									}

									if (hasProperties) {
										// Reconstruct the base block ID up to the property segment
										StringBuilder baseBlockBuilder = new StringBuilder(segments[0]);
										for (int i = 1; i < baseBlockEndIndex; i++) {
											baseBlockBuilder.append(":").append(segments[i]);
										}
										baseBlockId = baseBlockBuilder.toString();
									} else {
										// No properties found, use the entire block ID
										baseBlockId = blockId;
									}
								} else {
									// No colons found, treat as vanilla block
									baseBlockId = blockId;
								}

								// Add minecraft namespace if missing
								if (!baseBlockId.contains(":")) {
									baseBlockId = "minecraft:" + baseBlockId;
								}

								shaderBlocks.add(baseBlockId);
								LOGGER.debug("Added block: {} (from {})", baseBlockId, blockId);
							}
						}
					}
				}

				// Reset the StringBuilder for the next entry
				currentLine.setLength(0);
			}

			LOGGER.info("Total blocks read from shader properties: {}", shaderBlocks.size());

		} catch (IOException e) {
			LOGGER.error("Failed to read block.properties file", e);
		}

		return shaderBlocks;
	}

	private static void writeComparisonToFile(Path outputPath, String shaderpackName, Set<String> gameBlocks,
                                              Set<String> shaderBlocks, Set<String> missingFromShader,
                                              Set<String> missingFromGame, Map<String, List<String>> blocksByMod) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile()))) {
			// Write summary
			writer.write("=== Block Comparison Summary for " + shaderpackName + " ===");
			writer.newLine();
			writer.write(String.format("Total blocks in game: %d", gameBlocks.size()));
			writer.newLine();
			writer.write(String.format("Total blocks in shader: %d", shaderBlocks.size()));
			writer.newLine();
			writer.write(String.format("Unused blocks from shader: %d", missingFromGame.size()));
			writer.newLine();
			writer.write(String.format("Blocks missing from shader: %d", missingFromShader.size()));
			writer.newLine();
			writer.newLine();
			writer.newLine();

			// Write missing blocks sorted by mod
			writer.write("=== Blocks Missing From Shader (By Mod) ===");
			writer.newLine();
			Map<String, List<String>> missingByMod = new TreeMap<>();
			for (String block : missingFromShader) {
				String[] parts = block.split(":", 2);
				String modId = parts[0];
				String blockName = parts[1];
				missingByMod.computeIfAbsent(modId, k -> new ArrayList<>()).add(blockName);
			}

			for (Map.Entry<String, List<String>> entry : missingByMod.entrySet()) {
				writer.write("--- " + entry.getKey() + " (" + entry.getValue().size() + " blocks) ---");
				writer.newLine();
				List<String> blocks = entry.getValue();
				Collections.sort(blocks);
				for (String block : blocks) {
					writer.write(entry.getKey() + ":" + block);
					writer.newLine();
				}
				writer.newLine();
			}
			writer.newLine();



			// Write full block list by mod
			writer.write("=== Full Block List By Mod ===");
			writer.newLine();

			for (Map.Entry<String, List<String>> entry : blocksByMod.entrySet()) {
				String modId = entry.getKey();
				List<String> blocks = entry.getValue();
				blocks.sort(String::compareTo);

				writer.write("=== " + modId + " (" + blocks.size() + " blocks) ===");
				writer.newLine();
				writer.newLine();

				for (String block : blocks) {
					writer.write(modId + ":" + block);
					writer.newLine();
				}

				writer.newLine();
			}

			LOGGER.info("Successfully wrote block comparison to {}", outputPath);

		} catch (IOException e) {
			LOGGER.error("Failed to write block comparison for {}", shaderpackName, e);
		}
	}

	static public void processShaderPacks() {
		// Get game directory and shaderpacks directory
		Path gameDir = FabricLoader.getInstance().getGameDir();
		Path shaderpacksDir = gameDir.resolve("shaderpacks");

		// Create shaderpacks directory if it doesn't exist
		if (!Files.exists(shaderpacksDir)) {
			try {
				Files.createDirectories(shaderpacksDir);
				LOGGER.info("Created shaderpacks directory");
			} catch (IOException e) {
				LOGGER.error("Failed to create shaderpacks directory", e);
				return;
			}
		}

		// Create a map to store blocks by mod ID and set of game blocks
		Map<String, List<String>> blocksByMod = new TreeMap<>();
		Set<String> gameBlocks = getStrings(blocksByMod);

		// Create logs directory if needed
		Path logsDir = gameDir.resolve("logs");
		if (!Files.exists(logsDir)) {
			try {
				Files.createDirectories(logsDir);
			} catch (IOException e) {
				LOGGER.error("Failed to create logs directory", e);
				return;
			}
		}

		// Process each shaderpack
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacksDir)) {
			for (Path shaderpackPath : stream) {
				if (Files.isDirectory(shaderpackPath)) {
					String shaderpackName = shaderpackPath.getFileName().toString();
					LOGGER.info("Processing shaderpack: {}", shaderpackName);

					// Read shader block properties
					Set<String> shaderBlocks = readShaderBlockProperties(shaderpackPath);

					if (shaderBlocks.isEmpty()) {
						LOGGER.info("Skipping {} - no valid block.properties found", shaderpackName);
						continue;
					}

					LOGGER.info("Found {} blocks in shader properties for {}", shaderBlocks.size(), shaderpackName);

					// Calculate differences
					Set<String> missingFromShader = new HashSet<>(gameBlocks);
					missingFromShader.removeAll(shaderBlocks);

					// Calculate blocks in shader but not in game
					Set<String> missingFromGame = new HashSet<>(shaderBlocks);
					missingFromGame.removeAll(gameBlocks);

					// Log the number of blocks not in game
					LOGGER.info("Found {} blocks in shader that don't exist in game", missingFromGame.size());

					// Create comparison file for this shaderpack
					String safeName = shaderpackName.replaceAll("[^a-zA-Z0-9.-]", "_");
					Path comparisonPath = logsDir.resolve("block_comparison_" + safeName + ".txt");

					writeComparisonToFile(comparisonPath, shaderpackName, gameBlocks, shaderBlocks,
							missingFromShader, missingFromGame, blocksByMod);
				}
			}
		} catch (IOException e) {
			LOGGER.error("Failed to process shaderpacks directory", e);
		}
	}

	private static @NotNull Set<String> getStrings(Map<String, List<String>> blocksByMod) {
		Set<String> gameBlocks = new HashSet<>();

		// Use lazy initialization of registry
		RegistryWrapper registry = getRegistry();
		registry.forEachBlock(blockEntry -> {
			Identifier blockId = blockEntry.getIdentifier();
			String modId = blockId.getNamespace();
			String blockPath = blockId.getPath();
			String fullBlockId = modId + ":" + blockPath;

			// Add to game block set
			gameBlocks.add(fullBlockId);

			// Get or create the list for this mod
			blocksByMod.computeIfAbsent(modId, k -> new ArrayList<>())
					.add(blockPath);
		});
		return gameBlocks;
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Euphoria Companion Mod");

		// Register to run when the server starts, after all registries are complete
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			LOGGER.info("Server starting, processing shader packs");
			processShaderPacks();
		});
	}
}