package com.wiIk_wq.tfth_heals.world.level.block;
import com.wiIk_wq.tfth_heals.registry.ModBlockEntities;
import com.wiIk_wq.tfth_heals.world.level.block.entity.FleshCleanserBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
public class FleshCleanserBlock extends Block implements EntityBlock {
    // наше свойство "горит / не горит"
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public FleshCleanserBlock(Properties props) {
// делаем так, чтобы блок светился, когда LIT = true
        super(props.lightLevel(state -> state.getValue(LIT) ? 12 : 0));
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, Boolean.FALSE));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
    // ----------------------------------------------------------
// BlockEntity creation
// ----------------------------------------------------------
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FleshCleanserBlockEntity(pos, state);
    }
    // ----------------------------------------------------------
// Forge 1.20.1 — тикер руками
// ----------------------------------------------------------
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (type != ModBlockEntities.FLESH_CLEANSER_T1.get()) return null;

        // CLIENT: только визуал (партиклы)
        if (level.isClientSide) {
            return (lvl, pos, st, be) -> {
                if (be instanceof FleshCleanserBlockEntity cleanser) {
                    FleshCleanserBlockEntity.clientTick(lvl, pos, st, cleanser);
                }
            };
        }

        // SERVER: логика
        return (lvl, pos, st, be) -> {
            if (be instanceof FleshCleanserBlockEntity cleanser) {
                FleshCleanserBlockEntity.serverTick(lvl, pos, st, cleanser);
            }
        };
    }
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
    // ----------------------------------------------------------
// Кормление магмой / лавой + показ статуса
// ----------------------------------------------------------
    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FleshCleanserBlockEntity cleanser)) {
            return InteractionResult.PASS;
        }
        boolean fed = false;
// Кормим, если в руке подходящий предмет (подогнано под ~5 мин на lava bucket)
        if (!stack.isEmpty()) {
            if (stack.is(Items.MAGMA_BLOCK)) {
// 🔹 Магма-блок: +6 heat (~0.5 мин)
                cleanser.addHeat(6);
                if (!player.isCreative()) stack.shrink(1);
                fed = true;
            } else if (stack.is(Items.MAGMA_CREAM)) {
// 🔹 Магма-крем: +15 heat (~1.25 мин)
                cleanser.addHeat(15);
                if (!player.isCreative()) stack.shrink(1);
                fed = true;
            } else if (stack.is(Items.LAVA_BUCKET)) {
// 🔹 Лава: +60 heat (~5 мин)
                cleanser.addHeat(60);
                if (!player.isCreative()) {
                    player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                }
                fed = true;
            }
        }
// ----- Показать статус над хотбаром -----
        int heat = cleanser.getHeat();
        int maxHeat = cleanser.getMaxHeat();
        int remainingTicks = cleanser.getRemainingTicks();
        int remainingSeconds = remainingTicks / 20;
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        String timeStr;
        if (remainingSeconds <= 0) {
            timeStr = "0s";
        } else if (minutes > 0) {
            timeStr = minutes + "m " + seconds + "s";
        } else {
            timeStr = seconds + "s";
        }
        Component msg = Component.literal(
                "Очиститель: " + heat + " / " + maxHeat + " | Осталось: " + timeStr
        );
// true = actionbar (над хотбаром)
        player.displayClientMessage(msg, true);
        return fed ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
    }
}