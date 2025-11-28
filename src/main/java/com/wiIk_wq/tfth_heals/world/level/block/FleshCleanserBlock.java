package com.wiIk_wq.tfth_heals.world.level.block;

import com.wiIk_wq.tfth_heals.registry.ModBlockEntities;
import com.wiIk_wq.tfth_heals.world.level.block.entity.FleshCleanserBlockEntity;
import net.minecraft.core.BlockPos;
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
        if (!level.isClientSide && type == ModBlockEntities.FLESH_CLEANSER_T1.get()) {
            return (lvl, pos, st, be) -> {
                if (be instanceof FleshCleanserBlockEntity cleanser) {
                    FleshCleanserBlockEntity.serverTick(lvl, pos, st, cleanser);
                }
            };
        }
        return null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ----------------------------------------------------------
    // Кормление магмой / лавой
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
        if (stack.isEmpty()) return InteractionResult.PASS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FleshCleanserBlockEntity cleanser)) {
            return InteractionResult.PASS;
        }

        boolean fed = false;

        if (stack.is(Items.MAGMA_BLOCK)) {
            cleanser.addHeat(200);
            if (!player.isCreative()) stack.shrink(1);
            fed = true;
        } else if (stack.is(Items.MAGMA_CREAM)) {
            cleanser.addHeat(100);
            if (!player.isCreative()) stack.shrink(1);
            fed = true;
        } else if (stack.is(Items.LAVA_BUCKET)) {
            cleanser.addHeat(800);
            if (!player.isCreative()) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }
            fed = true;
        }

        return fed ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
}