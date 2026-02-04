package com.wiIk_wq.tfth_heals.world.level.block.entity;

import com.wiIk_wq.tfth_heals.registry.ModBlockEntities;
import com.wiIk_wq.tfth_heals.world.level.block.FleshCleanserBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedList;
import java.util.List;

public class FleshCleanserBlockEntity extends BlockEntity {

    private static final String TFTH_MOD_ID = "the_flesh_that_hates";

    private static final String BURN_KEY = "tfth_heals_burn_ticks";
    private static final int BURN_TIME_TICKS = 100; // 5 секунд

    // 🔹 Радиус очистки БЛОКОВ (куб 41x41x41, r=20 в каждую сторону)
    private static final int BLOCK_RADIUS_T1 = 20;
    // 🔹 Радиус воздействия на МОБОВ
    private static final int MOB_RADIUS_T1 = 10;

    // Max heat = 60 единиц * 100 тиков/цикл = 6000 тиков = 5 минут (на lava bucket)
    private static final int MAX_HEAT_T1 = 60;

    // ~4 блока/сек: удаляем 1 блок каждые 5 тиков (20 тиков/сек / 5 = 4 удаления/сек)
    private static final int TICKS_PER_CLEAN = 5;

    // Пользовательские шансы побочек
    private static final float FIRE_CHANCE_T1 = 0.08f;   // 8%
    private static final float MAGMA_CHANCE_T1 = 0.005f; // 0.5%
    private static final float LAVA_CHANCE_T1 = 0.001f;  // 0.1%

    // Шанс оставить "остаток" (charred flesh -> bone block)
    private static final float RESIDUE_CHANCE_T1 = 0.05f; // 5%

    // ====== ВИЗУАЛ (умеренно) ======
    private static final int BORDER_PARTICLE_INTERVAL = 1200;
    private static final int BORDER_PARTICLE_DURATION = 60;          // было 100
    private static final int BORDER_PARTICLE_COUNT_PER_TICK = 12;    // было 50

    private static final int BLOCK_PARTICLE_COUNT_PER_TICK = 1;      // было 3

    private static final int ZONE_PARTICLE_INTERVAL = 40;            // было 20
    private static final int ZONE_PARTICLE_COUNT = 1;                // было 1 (оставим)

    // Для refill очереди (чтобы учитывать новые блоки): раз в 20 тиков, если очередь пуста
    private static final int REFILL_INTERVAL = 20;

    private int heat;
    private int tickCounter;
    private int borderParticleCounter; // счётчик для партиклов границы
    private int cleanCounter; // для удаления по одному каждые 5 тиков
    private int refillCounter; // для refill очереди

    // очередь заражённых блоков, отсортированных по расстоянию от ближних к дальним
    private final LinkedList<BlockPos> infectedQueue = new LinkedList<>();

    public FleshCleanserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLESH_CLEANSER_T1.get(), pos, state);
        this.borderParticleCounter = 0;
        this.cleanCounter = 0;
        this.refillCounter = 0;
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
            this.level.setBlock(
                    this.worldPosition,
                    state.setValue(FleshCleanserBlock.LIT, lit),
                    Block.UPDATE_ALL
            );
        }
    }

    // ----------------------------------------------------------
    // Заполняем очередь заражённых блоков в кубе [-r..r], отсортированных от ближних к дальним
    // ----------------------------------------------------------
    private void refillInfectedQueue(Level level, BlockPos center) {
        infectedQueue.clear();
        int r = BLOCK_RADIUS_T1;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        // Сканируем все блоки в кубе [-r..r] вокруг
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    mutablePos.setWithOffset(center, dx, dy, dz);
                    if (!level.isLoaded(mutablePos)) continue;
                    BlockState state = level.getBlockState(mutablePos);
                    if (isInfectedBlock(state)) {
                        infectedQueue.add(mutablePos.immutable());
                    }
                }
            }
        }

        // сортируем: от ближайших к дальнейшим
        infectedQueue.sort((a, b) -> {
            int da = distanceSq(a, center);
            int db = distanceSq(b, center);
            return Integer.compare(da, db);
        });
    }

    private static int distanceSq(BlockPos p, BlockPos center) {
        int dx = p.getX() - center.getX();
        int dy = p.getY() - center.getY();
        int dz = p.getZ() - center.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    // ==========================================================
    // SERVER TICK — ТОЛЬКО ЛОГИКА/ГЕЙМПЛЕЙ (без партиклов)
    // ==========================================================
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
        be.borderParticleCounter++;
        be.cleanCounter++;
        be.refillCounter++;

        RandomSource random = level.getRandom();

        // --------------------------
        // Очистка заражённых блоков: по одному каждые TICKS_PER_CLEAN тиков
        // --------------------------

        // Refill очереди, если пуста и интервал прошёл (чтобы ловить новые блоки)
        if (be.infectedQueue.isEmpty() && be.refillCounter >= REFILL_INTERVAL) {
            be.refillInfectedQueue(level, pos);
            be.refillCounter = 0;
        }

        // Удаляем один блок, если время пришло и очередь не пуста
        if (be.cleanCounter >= TICKS_PER_CLEAN && !be.infectedQueue.isEmpty()) {
            be.cleanCounter = 0;
            BlockPos targetPos = be.infectedQueue.pollFirst();
            if (targetPos != null && level.isLoaded(targetPos)) {
                BlockState targetState = level.getBlockState(targetPos);
                if (isInfectedBlock(targetState)) {
                    cleanBlock(level, targetPos, targetState, random);
                }
            }

            // Тратим heat раз в 100 тиков (как раньше, для баланса топлива)
            if (be.tickCounter % 100 == 0) {
                be.heat--;
                if (be.heat < 0) be.heat = 0;
                be.setChanged();
            }
        }

        // --------------------------
        // Обработка мобов из мода (инстант-килл + партиклы "изнутри")
        // --------------------------
        int radiusMobs = MOB_RADIUS_T1;
        AABB box = new AABB(pos).inflate(radiusMobs);
        List<LivingEntity> entities = level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && isInfectedEntity(e)
        );

        ServerLevel sl = (ServerLevel) level;

        for (LivingEntity entity : entities) {
            CompoundTag data = entity.getPersistentData();

            // Если моб впервые попал в радиус — запускаем "сгорание изнутри"
            if (!data.contains(BURN_KEY)) {
                data.putInt(BURN_KEY, 0);

                // маленький "старт" эффект
                sl.sendParticles(ParticleTypes.SMOKE,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                        40, 0.2, 0.3, 0.2, 0.2);
                sl.playSound(null, entity.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.6f, 1.6f);
            }

            int t = data.getInt(BURN_KEY);
            t++;
            data.putInt(BURN_KEY, t);

            double ex = entity.getX();
            double ey = entity.getY() + entity.getBbHeight() * 0.5;
            double ez = entity.getZ();

            // 0..5 секунд: "много внутри" — дым + редкие искры/огонёк
            // (каждый тик даём умеренно, чтобы было видно, но не лагало)
            sl.sendParticles(ParticleTypes.SMOKE, ex, ey, ez, 10, 0.15, 0.25, 0.15, 0.02);
            if (random.nextFloat() < 0.35f) {
                sl.sendParticles(ParticleTypes.FLAME, ex, ey, ez, 12, 0.12, 0.20, 0.12, 0.04);
            }
            if (random.nextFloat() < 0.15f) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, ex, ey, ez, 4, 0.10, 0.15, 0.10, 0.02);
            }

            // Лёгкий урон "изнутри", чтобы выглядело как процесс (не моментально)
            // (можешь убрать, если хочешь чисто визуал без урона до финала)
            entity.hurt(level.damageSources().generic(), 0.5F);

            // На 5-й секунде: вспышка, огонь, смерть
            if (t >= BURN_TIME_TICKS) {
                // финальный буст партиклов
                sl.sendParticles(ParticleTypes.FLAME, ex, ey, ez, 40, 0.25, 0.40, 0.25, 0.06);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, ex, ey, ez, 20, 0.25, 0.40, 0.25, 0.02);
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ex, ey, ez, 36, 0.20, 0.35, 0.20, 0.1);

                entity.setSecondsOnFire(4);
                entity.hurt(level.damageSources().generic(), 999.0F);

                sl.playSound(null, ex, ey, ez, SoundEvents.FIRE_AMBIENT, SoundSource.HOSTILE,
                        1.2f, 0.9f + random.nextFloat() * 0.4f);

                // чистим тег, чтобы не держать мусор (на всякий)
                data.remove(BURN_KEY);
            }
        }
    }

    // ==========================================================
    // CLIENT TICK — ТОЛЬКО ВИЗУАЛ (партиклы), без логики
    // ==========================================================
    public static void clientTick(Level level, BlockPos pos, BlockState state, FleshCleanserBlockEntity be) {
        if (!level.isClientSide) return;
        if (!ModList.get().isLoaded(TFTH_MOD_ID)) return;

        // Партиклы показываем только когда блок реально "горит" по стейту
        if (!(state.getBlock() instanceof FleshCleanserBlock)) return;
        if (!state.getValue(FleshCleanserBlock.LIT)) return;

        be.tickCounter++;
        be.borderParticleCounter++;

        RandomSource random = level.getRandom();

        // Постоянные партиклы возле блока (выстреливают с силой)
        be.spawnBlockParticles(level, pos, random);

        // Рандомные редкие партиклы в зоне
        if (be.tickCounter % ZONE_PARTICLE_INTERVAL == 0) {
            be.spawnZoneParticles(level, pos, random);
        }

        // Партиклы на границе: каждые 1200 тиков активируем на 100 тиков (кольцо ауры)
        if (be.borderParticleCounter >= BORDER_PARTICLE_INTERVAL) {
            be.borderParticleCounter = 0; // сброс
        }
        if (be.borderParticleCounter <= BORDER_PARTICLE_DURATION) {
            be.spawnBorderParticles(level, pos, random);
        }
    }

    // Метод для спавна партиклов из блока (выстреливают с силой, сделано заметнее)
    private void spawnBlockParticles(Level level, BlockPos pos, RandomSource random) {
        double px = pos.getX() + 0.5;
        double py = pos.getY() + 1.2; // выше, чтобы видно
        double pz = pos.getZ() + 0.5;

        for (int i = 0; i < BLOCK_PARTICLE_COUNT_PER_TICK; i++) {
            // Скорость в 3–5 блоков радиуса (примерно)
            double vx = random.nextGaussian() * 0.10;          // было 0.30
            double vy = random.nextDouble() * 0.08 + 0.02;     // было 0.4 + 0.1
            double vz = random.nextGaussian() * 0.10;          // было 0.30

            level.addParticle(ParticleTypes.FLAME, px, py, pz, vx, vy, vz);

            // Дым слабее и медленнее, чтоб не разлетался
            if (random.nextFloat() < 0.35f) {                  // было 0.5
                level.addParticle(ParticleTypes.LARGE_SMOKE, px, py, pz, vx * 0.35, vy * 0.35, vz * 0.35);
            }
        }
    }

    // Метод для рандомных редких партиклов в зоне (увеличили шанс/кол-во для теста)
    private void spawnZoneParticles(Level level, BlockPos center, RandomSource random) {
        int r = BLOCK_RADIUS_T1;
        for (int i = 0; i < ZONE_PARTICLE_COUNT * 2; i++) { // утроили для видимости
            double dx = (random.nextDouble() * 2 * r) - r;
            double dy = (random.nextDouble() * 2 * r) - r;
            double dz = (random.nextDouble() * 2 * r) - r;

            double px = center.getX() + 0.5 + dx;
            double py = center.getY() + 0.5 + dy;
            double pz = center.getZ() + 0.5 + dz;

            level.addParticle(ParticleTypes.FLAME, px, py, pz, 0, 0.1, 0); // медленнее вверх
        }
    }

    // Метод для спавна партиклов на границе (горизонтальное кольцо ауры, заметнее)
    private void spawnBorderParticles(Level level, BlockPos center, RandomSource random) {
        double radius = BLOCK_RADIUS_T1 + 0.5;
        double y = center.getY() + 0.5;
        int segments = BORDER_PARTICLE_COUNT_PER_TICK; // удвоили

        for (int i = 0; i < segments; i++) {
            double theta = (i / (double) segments) * 2 * Math.PI;
            double x = radius * Math.cos(theta);
            double z = radius * Math.sin(theta);

            double px = center.getX() + 0.5 + x;
            double py = y + (random.nextDouble() - 0.5) * 0.3; // меньше разброс
            double pz = center.getZ() + 0.5 + z;

            level.addParticle(ParticleTypes.FLAME, px, py, pz, 0, 0.08, 0);
            if (random.nextFloat() < 0.3f) {
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 0, 0.05, 0);
            }
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

    // Любой моб из мода TFTH
    private static boolean isInfectedEntity(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (id == null) return false;
        return TFTH_MOD_ID.equals(id.getNamespace());
    }

    // Очищаем блок + остаток + побочки + звук/партиклы (увеличили партиклы для видимости)
    private static void cleanBlock(Level level, BlockPos pos, BlockState state, RandomSource random) {
        // Сначала воздух
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        // Шанс на "остаток"
        if (random.nextFloat() < RESIDUE_CHANCE_T1) {
            BlockState residue = Blocks.BONE_BLOCK.defaultBlockState();
            if (residue.canSurvive(level, pos)) {
                level.setBlock(pos, residue, Block.UPDATE_ALL);
            }
        }

        // Побочки
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

        // Звук
        if (random.nextFloat() < 0.3f) {
            level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
                    0.3F + random.nextFloat() * 0.2F, 0.8F + random.nextFloat() * 0.4F);
        }

        // ❗ Партиклы очищения тут оставляю КАК ЕСТЬ (не трогаю логику),
        // но они в SERVER context не будут видны как addParticle.
        // Вариант B сознательно переносит всю визуалку в clientTick.
        // Если тебе надо видеть именно эти "очистительные" партиклы — скажи, я перенесу
        // их в клиентскую часть аккуратно, не меняя механику (через событие/флаг).

        double px = pos.getX() + 0.5;
        double py = pos.getY() + 0.5;
        double pz = pos.getZ() + 0.5;
        for (int i = 0; i < 6; i++) {
            double vx = (random.nextDouble() - 0.5) * 0.2;
            double vy = random.nextDouble() * 0.3;
            double vz = (random.nextDouble() - 0.5) * 0.2;
            level.addParticle(ParticleTypes.FLAME, px, py, pz, vx, vy, vz);
            level.addParticle(ParticleTypes.LARGE_SMOKE, px, py, pz, vx * 0.5, vy * 0.5, vz * 0.5);
        }
    }

    // ---- Геттеры для UI ----
    public int getHeat() {
        return this.heat;
    }

    public int getMaxHeat() {
        return MAX_HEAT_T1;
    }

    public int getRemainingTicks() {
        // 1 "heat" держит ~100 тиков
        return this.heat * 100;
    }

    // Сохранение/загрузка NBT
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Heat", this.heat);
        tag.putInt("TickCounter", this.tickCounter);
        tag.putInt("BorderParticleCounter", this.borderParticleCounter);
        tag.putInt("CleanCounter", this.cleanCounter);
        tag.putInt("RefillCounter", this.refillCounter);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.heat = tag.getInt("Heat");
        this.tickCounter = tag.getInt("TickCounter");
        this.borderParticleCounter = tag.getInt("BorderParticleCounter");
        this.cleanCounter = tag.getInt("CleanCounter");
        this.refillCounter = tag.getInt("RefillCounter");
        this.infectedQueue.clear();
    }
}