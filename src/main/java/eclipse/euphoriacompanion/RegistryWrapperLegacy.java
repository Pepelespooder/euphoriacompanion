// RegistryWrapperLegacy.java
package eclipse.euphoriacompanion;

import net.minecraft.util.Identifier;
import net.minecraft.block.Block;
import java.util.function.Consumer;

public class RegistryWrapperLegacy implements RegistryWrapper {
    private final Object registry;
    private final Class<?> registryClass;

    public RegistryWrapperLegacy() {
        try {
            Class<?> foundClass;
            // Try the different possible Registry class locations
            try {
                foundClass = Class.forName("net.minecraft.util.registry.Registry");
            } catch (ClassNotFoundException e) {
                foundClass = Class.forName("net.minecraft.core.Registry");
            }

            registryClass = foundClass;
            // Get the BLOCK field
            registry = registryClass.getField("BLOCK").get(null);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize legacy registry", e);
        }
    }

    @Override
    public void forEachBlock(Consumer<BlockEntry> consumer) {
        try {
            // Get the forEach method
            registryClass.getMethod("forEach", Consumer.class)
                    .invoke(registry, (Consumer<Block>) block -> {
                        Identifier id = getBlockId(block);
                        consumer.accept(new BlockEntry(block, id));
                    });
        } catch (Exception e) {
            throw new RuntimeException("Failed to iterate blocks", e);
        }
    }

    @Override
    public Identifier getBlockId(Object block) {
        try {
            return (Identifier) registryClass.getMethod("getId", Object.class)
                    .invoke(registry, block);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get block ID", e);
        }
    }
}