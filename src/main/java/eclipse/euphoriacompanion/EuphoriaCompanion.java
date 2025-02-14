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
	public static final String MOD_ID = "Euphoria Companion";
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

		if (!Files.exists(blockPropertiesPath)) {
			LOGGER.warn("No block.properties found in {}", shaderpackPath);
			return shaderBlocks;
		}

		try (BufferedReader reader = Files.newBufferedReader(blockPropertiesPath)) {
			StringBuilder currentLine = new StringBuilder();
			String line;

			while ((line = reader.readLine()) != null) {
				line = line.trim();

				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}

				if (line.endsWith("\\")) {
					currentLine.append(line, 0, line.length() - 1).append(" ");
					continue;
				}

				currentLine.append(line);

				String fullLine = currentLine.toString().trim();
				if (fullLine.contains("=")) {
					String[] parts = fullLine.split("=", 2);
					if (parts.length > 1) {
						String[] blockIds = parts[1].trim().split("\\s+");
						for (String blockId : blockIds) {
							blockId = blockId.trim();
							if (!blockId.isEmpty() && !blockId.startsWith("tags_")) {
								String baseBlockId;

								String[] segments = blockId.split(":");
								if (segments.length > 1) {
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
										StringBuilder baseBlockBuilder = new StringBuilder(segments[0]);
										for (int i = 1; i < baseBlockEndIndex; i++) {
											baseBlockBuilder.append(":").append(segments[i]);
										}
										baseBlockId = baseBlockBuilder.toString();
									} else {
										baseBlockId = blockId;
									}
								} else {
									baseBlockId = blockId;
								}

								if (!baseBlockId.contains(":")) {
									baseBlockId = "minecraft:" + baseBlockId;
								}

								shaderBlocks.add(baseBlockId);
								LOGGER.debug("Added block: {} (from {})", baseBlockId, blockId);
							}
						}
					}
				}

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

	private static void processShaderBlocks(String shaderpackName, Set<String> shaderBlocks, Set<String> gameBlocks,
											Path logsDir, Map<String, List<String>> blocksByMod) {
		if (shaderBlocks.isEmpty()) {
			LOGGER.info("Skipping {} - no valid block.properties found", shaderpackName);
			return;
		}

		LOGGER.info("Found {} blocks in shader properties for {}", shaderBlocks.size(), shaderpackName);

		Set<String> missingFromShader = new HashSet<>(gameBlocks);
		missingFromShader.removeAll(shaderBlocks);

		Set<String> missingFromGame = new HashSet<>(shaderBlocks);
		missingFromGame.removeAll(gameBlocks);

		LOGGER.info("Found {} blocks in shader that don't exist in game", missingFromGame.size());

		String safeName = shaderpackName.replaceAll("[^a-zA-Z0-9.-]", "_");
		Path comparisonPath = logsDir.resolve("block_comparison_" + safeName + ".txt");

		writeComparisonToFile(comparisonPath, shaderpackName, gameBlocks, shaderBlocks,
				missingFromShader, missingFromGame, blocksByMod);
	}

	static public void processShaderPacks() {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		Path shaderpacksDir = gameDir.resolve("shaderpacks");
		boolean anyValidShaderpack = false;

		if (!Files.exists(shaderpacksDir)) {
			try {
				Files.createDirectories(shaderpacksDir);
				LOGGER.info("Created shaderpacks directory");
			} catch (IOException e) {
				LOGGER.error("Failed to create shaderpacks directory", e);
				return;
			}
		}

		Map<String, List<String>> blocksByMod = new TreeMap<>();
		Set<String> gameBlocks = getStrings(blocksByMod);

		Path logsDir = gameDir.resolve("logs");
		if (!Files.exists(logsDir)) {
			try {
				Files.createDirectories(logsDir);
			} catch (IOException e) {
				LOGGER.error("Failed to create logs directory", e);
				return;
			}
		}

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacksDir)) {
			for (Path shaderpackPath : stream) {
				String shaderpackName = shaderpackPath.getFileName().toString();
				Set<String> shaderBlocks;

				if (Files.isDirectory(shaderpackPath)) {
					LOGGER.info("Processing shaderpack (directory): {}", shaderpackName);
					shaderBlocks = readShaderBlockProperties(shaderpackPath);
				} else if (Files.isRegularFile(shaderpackPath) && shaderpackName.toLowerCase().endsWith(".zip")) {
					LOGGER.info("Processing shaderpack (ZIP): {}", shaderpackName);
					try (FileSystem zipFs = FileSystems.newFileSystem(shaderpackPath, (ClassLoader) null)) {
						Path root = zipFs.getPath("/");
						shaderBlocks = readShaderBlockProperties(root);
					} catch (IOException e) {
						LOGGER.error("Failed to process zip file {}", shaderpackName, e);
						continue;
					}
				} else {
					LOGGER.info("Skipping {} - not a directory or zip file", shaderpackName);
					continue;
				}

				if (shaderBlocks != null) {
					if (!shaderBlocks.isEmpty()) {
						anyValidShaderpack = true;
					}
					processShaderBlocks(shaderpackName, shaderBlocks, gameBlocks, logsDir, blocksByMod);
				}
			}

			// Check if any valid shaderpacks were processed
			if (!anyValidShaderpack) {
				LOGGER.error("No valid shaderpacks found in shaderpacks directory!");
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