package com.wiIk_wq.tfth_heals;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
@Mod(TfthHeals.MODID)
public class TfthHeals {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "tfth_heals";
    public TfthHeals() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(this);

    }
}
