package com.wiIk_wq.tfth_heals;

import com.wiIk_wq.tfth_heals.registry.ModBlockEntities;
import com.wiIk_wq.tfth_heals.registry.ModBlocks;
import com.wiIk_wq.tfth_heals.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(TfthHeals.MOD_ID)
public class TfthHeals {

    public static final String MOD_ID = "tfth_heals";

    public TfthHeals() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
    }
}