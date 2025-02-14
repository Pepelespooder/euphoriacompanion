package eclipse.euphoriacompanion;

public class RegistryFactory {
    public static RegistryWrapper createRegistryWrapper() {
        return new RegistryWrapperLegacy(); // Always use legacy for 1.16-1.19.2
    }
}