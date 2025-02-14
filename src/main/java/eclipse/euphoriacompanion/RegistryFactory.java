package eclipse.euphoriacompanion;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;

public class RegistryFactory {
    private static final boolean IS_MODERN;

    static {
        boolean isModern = false;
        try {
            Version mcVersion = FabricLoader.getInstance()
                    .getModContainer("minecraft")
                    .orElseThrow(() -> new RuntimeException("Minecraft mod container not found!")) // Fixed line
                    .getMetadata()
                    .getVersion();

            if (mcVersion instanceof SemanticVersion) {
                SemanticVersion semVer = (SemanticVersion) mcVersion;
                isModern = semVer.compareTo(SemanticVersion.parse("1.19.3")) >= 0;
            }
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