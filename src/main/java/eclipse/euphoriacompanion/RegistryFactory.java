// RegistryFactory.java
package eclipse.euphoriacompanion;

public class RegistryFactory {
    private static final boolean IS_MODERN;
    private static final String MODERN_REGISTRY_CLASS = "net.minecraft.registry.Registries";

    static {
        boolean isModern;
        try {
            Class.forName(MODERN_REGISTRY_CLASS);
            isModern = true;
        } catch (ClassNotFoundException e) {
            isModern = false;
        }
        IS_MODERN = isModern;
    }

    public static RegistryWrapper createRegistryWrapper() {
        return IS_MODERN ? new RegistryWrapperModern() : new RegistryWrapperLegacy();
    }
}