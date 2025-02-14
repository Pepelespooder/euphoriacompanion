// RegistryWrapper.java
package eclipse.euphoriacompanion;

import net.minecraft.util.Identifier;
import java.util.function.Consumer;

public interface RegistryWrapper {
    void forEachBlock(Consumer<BlockEntry> consumer);
    Identifier getBlockId(Object block);

    class BlockEntry {
        private final Identifier identifier;

        public BlockEntry(Object block, Identifier identifier) {
            this.identifier = identifier;
        }

        public Identifier getIdentifier() {
            return identifier;
        }
    }
}