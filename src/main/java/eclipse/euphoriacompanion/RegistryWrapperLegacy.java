package eclipse.euphoriacompanion;

import net.minecraft.util.registry.Registry;
import net.minecraft.block.Block;
import net.minecraft.util.Identifier;
import java.util.function.Consumer;

public class RegistryWrapperLegacy implements RegistryWrapper {
    @Override
    public void forEachBlock(Consumer<BlockEntry> consumer) {
        Registry.BLOCK.forEach(block ->
                consumer.accept(new BlockEntry(block, Registry.BLOCK.getId(block))));
    }

    @Override
    public Identifier getBlockId(Object block) {
        return Registry.BLOCK.getId((Block) block);
    }
}