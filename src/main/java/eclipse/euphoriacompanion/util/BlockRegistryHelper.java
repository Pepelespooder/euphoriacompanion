package eclipse.euphoriacompanion.util;

import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.*;

public class BlockRegistryHelper {
    public static Set<String> getGameBlocks(final Map<String, List<String>> blocksByMod) {
        final Set<String> gameBlocks = new HashSet<>();
        
        // Using the Fabric registry system
        Registry.BLOCK.forEach(block -> {
            Identifier id = Registry.BLOCK.getId(block);

            String registryId = id.toString();
            gameBlocks.add(registryId);

            blocksByMod.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(id.getPath());
        });
        
        return gameBlocks;
    }
}