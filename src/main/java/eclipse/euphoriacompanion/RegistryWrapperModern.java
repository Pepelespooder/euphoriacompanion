// RegistryWrapperModern.java
package eclipse.euphoriacompanion;

import net.minecraft.registry.Registries;
import net.minecraft.block.Block;
import net.minecraft.util.Identifier;
import java.util.function.Consumer;

public class RegistryWrapperModern implements RegistryWrapper {
    @Override
    public void forEachBlock(Consumer<BlockEntry> consumer) {
        Registries.BLOCK.forEach(block ->
                consumer.accept(new BlockEntry(block, Registries.BLOCK.getId(block))));
    }

    @Override
    public Identifier getBlockId(Object block) {
        return Registries.BLOCK.getId((Block) block);
    }
}