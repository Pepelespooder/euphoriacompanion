package eclipse.euphoriacompanion;

import net.minecraft.block.Block;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Mod(modid = EuphoriaCompanion.MODID, name = "Euphoria Companion", version = "1.0")
public class EuphoriaCompanion {
	public static final String MODID = "euphoriacompanion";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	@Mod.EventHandler
	public void onServerStarting(FMLServerStartingEvent event) {
		LOGGER.info("Server starting, processing shader packs");
		processShaderPacks(event.getServer().getDataDirectory().toPath());
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		if (event.getSide() == Side.CLIENT) {
			MinecraftForge.EVENT_BUS.register(new ClientKeyHandler());
		}
	}

	@SideOnly(Side.CLIENT)
	public static class ClientKeyHandler {
		private final KeyBinding analyzeKey = new KeyBinding("Reparse Shaders", Keyboard.KEY_F6, "Euphoria Companion");

		public ClientKeyHandler() {
			ClientRegistry.registerKeyBinding(analyzeKey);
		}

		@SubscribeEvent
		public void onClientTick(TickEvent.ClientTickEvent event) {
			if (event.phase == TickEvent.Phase.END && analyzeKey.isPressed()) {
				processShaderPacks(getClientGameDir());
			}
		}

		private Path getClientGameDir() {
			return Minecraft.getMinecraft().gameDir.toPath();
		}
	}

	static public void processShaderPacks(Path gameDir) {
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
		Set<String> gameBlocks = getGameBlocks(blocksByMod);

		Path logsDir = Minecraft.getMinecraft().gameDir.toPath().resolve("logs");
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
					try (FileSystem zipFs = FileSystems.newFileSystem(shaderpackPath, null)) {
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

                if (!shaderBlocks.isEmpty()) {
                    anyValidShaderpack = true;
                }
                processShaderBlocks(shaderpackName, shaderBlocks, gameBlocks, logsDir, blocksByMod);
            }

			if (!anyValidShaderpack) {
				LOGGER.error("No valid shaderpacks found!");
			}
		} catch (IOException e) {
			LOGGER.error("Failed to process shaderpacks directory", e);
		}
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

				if (line.isEmpty() || line.startsWith("#")) continue;

				if (line.endsWith("\\")) {
					currentLine.append(line, 0, line.length() - 1).append(" ");
					continue;
				}

				currentLine.append(line);
				String fullLine = currentLine.toString().trim();
				currentLine.setLength(0);

				if (fullLine.contains("=")) {
					String[] parts = fullLine.split("=", 2);
					if (parts.length > 1) {
						String[] blockIds = parts[1].trim().split("\\s+");
						for (String blockId : blockIds) {
							blockId = blockId.trim();
							if (blockId.isEmpty() || blockId.startsWith("tags_")) continue;

							String baseBlockId = parseBaseBlockId(blockId);
							if (!baseBlockId.contains(":")) {
								baseBlockId = "minecraft:" + baseBlockId;
							}
							shaderBlocks.add(baseBlockId);
							LOGGER.debug("Added block: {} (from {})", baseBlockId, blockId);
						}
					}
				}
			}
			LOGGER.info("Total blocks read from shader properties: {}", shaderBlocks.size());
		} catch (IOException e) {
			LOGGER.error("Failed to read block.properties", e);
		}
		return shaderBlocks;
	}

	private static String parseBaseBlockId(String blockId) {
		// Split into maximum 3 parts but keep original format
		String[] segments = blockId.split(":", 3); // [0]=modid, [1]=blockname, [2]=extra

		// Handle properties first
		for (int i = 1; i < segments.length; i++) {
			if (segments[i].contains("=")) {
				return segments[0] + ":" + segments[1];
			}
		}

		// Return first two segments for comparison
		return segments.length > 1 ? segments[0] + ":" + segments[1] : blockId;
	}

	private static void processShaderBlocks(String shaderpackName, Set<String> shaderBlocks, Set<String> gameBlocks,
											Path logsDir, Map<String, List<String>> blocksByMod) {
		Set<String> missingFromShader = new HashSet<>(gameBlocks);
		missingFromShader.removeAll(shaderBlocks);

		Set<String> missingFromGame = new HashSet<>(shaderBlocks);
		missingFromGame.removeAll(gameBlocks);

		String safeName = shaderpackName.replaceAll("[^a-zA-Z0-9.-]", "_");
		Path comparisonPath = logsDir.resolve("block_comparison_" + safeName + ".txt");

		writeComparisonFile(comparisonPath, shaderpackName, gameBlocks, shaderBlocks,
				missingFromShader, missingFromGame, blocksByMod);
	}

	private static void writeComparisonFile(Path outputPath, String shaderpackName, Set<String> gameBlocks,
											Set<String> shaderBlocks, Set<String> missingFromShader,
											Set<String> missingFromGame, Map<String, List<String>> blocksByMod) {
		try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
			writer.write("=== Block Comparison Summary for " + shaderpackName + " ===\n");
			writer.write(String.format("Total blocks in game: %d\n", gameBlocks.size()));
			writer.write(String.format("Total blocks in shader: %d\n", shaderBlocks.size()));
			writer.write(String.format("Unused blocks from shader: %d\n", missingFromGame.size()));
			writer.write(String.format("Blocks missing from shader: %d\n\n", missingFromShader.size()));

			writeMissingBlocksByMod(writer, missingFromShader);
			writeFullBlockList(writer, blocksByMod);

			LOGGER.info("Report written to {}", outputPath);
		} catch (IOException e) {
			LOGGER.error("Failed to write report", e);
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

	private static Set<String> getGameBlocks(Map<String, List<String>> blocksByMod) {
		Set<String> gameBlocks = new HashSet<>();
		for (Block block : Block.REGISTRY) {
			ResourceLocation id = block.getRegistryName();
			if (id == null) continue;

			// Use direct registry format without splitting
			String registryId = id.toString();
			gameBlocks.add(registryId);

			// For reporting, maintain original registry format
			blocksByMod.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>())
					.add(id.getPath());
		}
		return gameBlocks;
	}
}