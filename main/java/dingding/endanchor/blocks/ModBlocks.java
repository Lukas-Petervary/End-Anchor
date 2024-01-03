package dingding.endanchor.blocks;


import dingding.endanchor.EndAnchor;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;


public class ModBlocks {
    public static final Block END_ANCHOR = registerBlock("end_anchor", new EndAnchorBlock(
            FabricBlockSettings.copyOf(
                Blocks.RESPAWN_ANCHOR
            ).luminance(state -> EndAnchorBlock.getLightLevel(state, 15))
        )
    );

    private static Block registerBlock(String name, Block block) {
        registerBlockItem(name, block);
        return Registry.register(Registries.BLOCK, new Identifier(EndAnchor.MOD_ID, name), block);
    }
    private static Item registerBlockItem(String name, Block block) {
        return Registry.register(Registries.ITEM, new Identifier(EndAnchor.MOD_ID, name),
                new BlockItem(block, new FabricItemSettings())
        );
    }
    public static void registerModBlocks() {
        EndAnchor.LOGGER.debug("Registering ModBlocks for " + EndAnchor.MOD_ID);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register((entries) -> entries.add(END_ANCHOR));
    }

}