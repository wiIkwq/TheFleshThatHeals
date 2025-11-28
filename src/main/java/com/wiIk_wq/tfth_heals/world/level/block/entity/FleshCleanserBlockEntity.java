package com.wiIk_wq.tfth_heals.world.level.block.entity;

import com.wiIk_wq.tfth_heals.registry.ModBlockEntities;
import com.wiIk_wq.tfth_heals.world.level.block.FleshCleanserBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class FleshCleanserBlockEntity extends BlockEntity {

    private static final String TFTH_MOD_ID = "the_flesh_that_hates";

    // Параметры T1
    private static final int RADIUS_T1 = 5;
    private static final int MAX_HEAT_T1 = 2000;
    private static final int TICKS_PER_CONSUME_T1 = 10; // каждые 10 тиков = 0.5 сек

    private static final float FIRE_CHANCE_T1 = 0.15f;
    private static final float MAGMA_CHANCE_T1 = 0.10f;
    private static final float LAVA_CHANCE_T1 = 0.02f;

    private int heat;
    private int tickCounter;

    public FleshCleanserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLESH_CLEANSER_T1.get(), pos, state);
    }

    // Публичный метод для блока
    public void addHeat(int amount) {
        this.heat = Math.min(this.heat + amount, MAX_HEAT_T1);
        setChanged();
    }

    // ----------------------------------------------------------
    // Включаем / выключаем визуальный "огонь" на блоке
    // ----------------------------------------------------------
    private void setLit(boolean lit) {
        if (this.level == null) return;

        BlockState state = this.level.getBlockState(this.worldPosition);
        if (!(state.getBlock() instanceof FleshCleanserBlock)) return;

        if (state.getValue(FleshCleanserBlock.LIT) != lit) {
            // Можно оставить print для дебага
            System.out.println("Flesh Cleanser at " + this.worldPosition + " LIT -> " + lit);

            this.level.setBlock(
                    this.worldPosition,
                    state.setValue(FleshCleanserBlock.LIT, lit),
                    Block.UPDATE_ALL
            );
        }
    }

    // Основной тик
    public static void serverTick(Level level, BlockPos pos, BlockState state, FleshCleanserBlockEntity be) {
        if (level.isClientSide) return;
        if (!ModList.get().isLoaded(TFTH_MOD_ID)) return;

        // нет тепла — блок гаснет и больше ничего не делает
        if (be.heat <= 0) {
            be.setLit(false);
            return;
        }

        // есть тепло — блок "горит"
        be.setLit(true);

        be.tickCounter++;
        if (be.tickCounter < TICKS_PER_CONSUME_T1) {
            return;
        }
        be.tickCounter = 0;
        be.heat--;
        be.setChanged();

        RandomSource random = level.getRandom();
        int radius = RADIUS_T1;

        // Чистим несколько случайных блоков за тик, чтобы не лагать
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 16; i++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dy = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            mutablePos.setWithOffset(pos, dx, dy, dz);

            if (!level.isLoaded(mutablePos)) continue;

            BlockState targetState = level.getBlockState(mutablePos);
            if (isInfectedBlock(targetState)) {
                cleanBlock(level, mutablePos, targetState, random);
            }
        }

        // Обработка мобов
        AABB box = new AABB(pos).inflate(radius);
        List<LivingEntity> entities = level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> !e.isRemoved() && isInfectedEntity(e)
        );

        for (LivingEntity entity : entities) {
            entity.setSecondsOnFire(4);
            entity.hurt(level.damageSources().lava(), 6.0F);
        }
    }

    // Определяем заражённый блок TFTH
    private static boolean isInfectedBlock(BlockState state) {
        Block block = state.getBlock();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null) return false;
        if (!TFTH_MOD_ID.equals(id.getNamespace())) return false;

        String path = id.getPath();
        return path.contains("flesh")
                || path.contains("meat")
                || path.contains("growth")
                || path.contains("tumor")
                || path.contains("tissue");
    }

    // Определяем заражённого моба TFTH
    private static boolean isInfectedEntity(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (id == null) return false;
        if (!TFTH_MOD_ID.equals(id.getNamespace())) return false;

        String path = id.getPath();
        return path.contains("flesh")
                || path.contains("infect")
                || path.contains("abomination")
                || path.contains("growth");
    }

    // Очищаем блок + побочные эффекты (огонь/магма/лава)
    private static void cleanBlock(Level level, BlockPos pos, BlockState state, RandomSource random) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        float r = random.nextFloat();

        if (r < LAVA_CHANCE_T1) {
            if (Blocks.LAVA.defaultBlockState().canSurvive(level, pos)) {
                level.setBlock(pos, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
            }
        } else if (r < LAVA_CHANCE_T1 + MAGMA_CHANCE_T1) {
            if (Blocks.MAGMA_BLOCK.defaultBlockState().canSurvive(level, pos)) {
                level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            }
        } else if (r < LAVA_CHANCE_T1 + MAGMA_CHANCE_T1 + FIRE_CHANCE_T1) {
            BlockPos above = pos.above();
            if (level.getBlockState(above).isAir() &&
                    Blocks.FIRE.defaultBlockState().canSurvive(level, above)) {
                level.setBlock(above, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    // Сохранение/загрузка NBT
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Heat", this.heat);
        tag.putInt("TickCounter", this.tickCounter);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.heat = tag.getInt("Heat");
        this.tickCounter = tag.getInt("TickCounter");
    }
}