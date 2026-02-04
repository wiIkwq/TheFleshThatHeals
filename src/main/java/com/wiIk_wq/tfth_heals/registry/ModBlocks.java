package com.wiIk_wq.tfth_heals.registry;
import com.wiIk_wq.tfth_heals.TfthHeals;
import com.wiIk_wq.tfth_heals.world.level.block.FleshCleanserBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TfthHeals.MOD_ID);
    // Первый уровень очистителя (hardness/blast res как CDU)
    public static final RegistryObject<Block> FLESH_CLEANSER_T1 = BLOCKS.register(
            "flesh_cleanser_t1",
            () -> new FleshCleanserBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_GRAY)
                            .strength(6.0F)  // как CDU
                            .explosionResistance(20.0F)  // как CDU
                            .requiresCorrectToolForDrops()
            )
    );
}