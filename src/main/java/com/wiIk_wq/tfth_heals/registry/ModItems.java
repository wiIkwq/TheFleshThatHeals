package com.wiIk_wq.tfth_heals.registry;
import com.wiIk_wq.tfth_heals.TfthHeals;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
@Mod.EventBusSubscriber(modid = TfthHeals.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TfthHeals.MOD_ID);
    public static final RegistryObject<Item> FLESH_CLEANSER_T1_ITEM = ITEMS.register(
            "flesh_cleanser_t1",
            () -> new BlockItem(ModBlocks.FLESH_CLEANSER_T1.get(), new Item.Properties())
    );
    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(FLESH_CLEANSER_T1_ITEM);
        }
    }
}