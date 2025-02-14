package eclipse.euphoriacompanion;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

public class RegistryFactory {
    private static final boolean IS_MODERN;

    static {
        boolean isModern = false;
        try {
            Version mcVersion = FabricLoader.getInstance()
                    .getModContainer("minecraft")
                    .orElseThrow(() -> new RuntimeException("Minecraft mod container not found!"))
                    .getMetadata()
                    .getVersion();

            Version targetVersion = Version.parse("1.19.3");
            isModern = mcVersion.compareTo(targetVersion) >= 0;
        } catch (VersionParsingException e) {
            System.err.println("Failed to parse target version: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Failed to detect Minecraft version: " + e.getMessage());
        }

        IS_MODERN = isModern;
        System.out.println("[EuphoriaCompanion] Modern registry detection: " + IS_MODERN);
    }

    public static RegistryWrapper createRegistryWrapper() {
        return IS_MODERN ? new RegistryWrapperModern() : new RegistryWrapperLegacy();
    }
}