package eclipse.euphoriacompanion.util;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

import java.util.*;

public class BlockRegistryHelper {
    public static Set<String> getGameBlocks(Map<String, List<String>> blocksByMod) {
        Set<String> gameBlocks = new HashSet<>();
        for (Block block : Block.REGISTRY) {
            ResourceLocation id = block.getRegistryName();
            if (id == null) continue;

            String registryId = id.toString();
            gameBlocks.add(registryId);

            blocksByMod.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(id.getPath());
        }
        return gameBlocks;
    }
}
