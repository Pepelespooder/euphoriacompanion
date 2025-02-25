package eclipse.euphoriacompanion.util;

import eclipse.euphoriacompanion.EuphoriaCompanion;
import net.minecraftforge.fml.common.Loader;

public class MCVersionChecker {

    public static int getMCVersion() {
        String version = Loader.instance().getMinecraftModContainer().getVersion();
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
                switch (operator) {
                    case ">=":
                        return mcVersion >= value;
                    case ">":
                        return mcVersion > value;
                    case "<=":
                        return mcVersion <= value;
                    case "<":
                        return mcVersion < value;
                    case "==":
                        return mcVersion == value;
                    case "!=":
                        return mcVersion != value;
                    default:
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