package eclipse.euphoriacompanion.util;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

public class BlockRegistryHelper {
    public static Set<String> getGameBlocks(final Map<String, List<String>> blocksByMod) {
        final Set<String> gameBlocks = new HashSet<>();

        // Using the Fabric registry system for 1.19.3+
        Registries.BLOCK.forEach(block -> {
            Identifier id = Registries.BLOCK.getId(block);

            String registryId = id.toString();
            gameBlocks.add(registryId);

            blocksByMod.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(id.getPath());
        });
        
        return gameBlocks;
    }
}