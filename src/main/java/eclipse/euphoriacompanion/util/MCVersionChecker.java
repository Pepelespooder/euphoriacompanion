package eclipse.euphoriacompanion.util;

import eclipse.euphoriacompanion.EuphoriaCompanion;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Optional;

public class MCVersionChecker {

    public static int getMCVersion() {
        Optional<ModContainer> minecraftContainer = FabricLoader.getInstance().getModContainer("minecraft");
        if (!minecraftContainer.isPresent()) {
            throw new RuntimeException("Could not get Minecraft version");
        }

        String version = minecraftContainer.get().getMetadata().getVersion().getFriendlyString();

        try {
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return major * 10000 + minor * 100 + patch;
        } catch (Exception e) {
            EuphoriaCompanion.LOGGER.error("Failed to parse Minecraft version: {}", version, e);
            return 0;
        }
    }

    public static boolean evaluateCondition(String condition, int mcVersion) {
        condition = condition.trim();
        if (condition.startsWith("MC_VERSION")) {
            condition = condition.substring("MC_VERSION".length()).trim();
            String[] parts = condition.split("\\s+");
            if (parts.length < 2) return false;
            String operator = parts[0];
            String valueStr = parts[1];
            try {
                int value = Integer.parseInt(valueStr);
                if (">=".equals(operator)) {
                    return mcVersion >= value;
                } else if (">".equals(operator)) {
                    return mcVersion > value;
                } else if ("<=".equals(operator)) {
                    return mcVersion <= value;
                } else if ("<".equals(operator)) {
                    return mcVersion < value;
                } else if ("==".equals(operator)) {
                    return mcVersion == value;
                } else if ("!=".equals(operator)) {
                    return mcVersion != value;
                } else {
                    return false;
                }
            } catch (NumberFormatException e) {
                EuphoriaCompanion.LOGGER.error("Invalid version in condition: {}", valueStr, e);
                return false;
            }
        }
        return false;
    }
}