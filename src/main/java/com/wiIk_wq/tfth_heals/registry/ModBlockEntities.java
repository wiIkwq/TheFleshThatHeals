package com.wiIk_wq.tfth_heals.registry;
import com.wiIk_wq.tfth_heals.TfthHeals;
import com.wiIk_wq.tfth_heals.world.level.block.entity.FleshCleanserBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TfthHeals.MOD_ID);
    public static final RegistryObject<BlockEntityType<FleshCleanserBlockEntity>> FLESH_CLEANSER_T1 =
            BLOCK_ENTITIES.register(
                    "flesh_cleanser_t1",
                    () -> BlockEntityType.Builder.of(
                            FleshCleanserBlockEntity::new,
                            ModBlocks.FLESH_CLEANSER_T1.get()
                    ).build(null)
            );
}