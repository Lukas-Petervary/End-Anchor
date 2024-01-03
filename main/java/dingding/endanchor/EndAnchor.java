package dingding.endanchor;

import dingding.endanchor.blocks.ModBlocks;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EndAnchor implements ModInitializer {
    public static final String MOD_ID = "endanchor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.registerModBlocks();
    }
}