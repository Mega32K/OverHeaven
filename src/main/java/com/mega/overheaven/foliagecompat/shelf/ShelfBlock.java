package com.mega.overheaven.foliagecompat.shelf;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ShelfBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape NORTH = Shapes.or(box(0, 0, 13, 16, 16, 16), box(0, 0, 11, 16, 4, 13), box(0, 12, 11, 16, 16, 13));
    private static final VoxelShape SOUTH = Shapes.or(box(0, 0, 0, 16, 16, 3), box(0, 0, 3, 16, 4, 5), box(0, 12, 3, 16, 16, 5));
    private static final VoxelShape EAST = Shapes.or(box(0, 0, 0, 3, 16, 16), box(3, 0, 0, 5, 4, 16), box(3, 12, 0, 5, 16, 16));
    private static final VoxelShape WEST = Shapes.or(box(13, 0, 0, 16, 16, 16), box(11, 0, 0, 13, 4, 16), box(11, 12, 0, 13, 16, 16));

    public ShelfBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public boolean useShapeForLightOcclusion(BlockState state) { return true; }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ShelfBlockEntity(pos, state); }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        int slot = ShelfInteraction.slot(state.getValue(FACING), hit.getDirection(), hit.getLocation().x - pos.getX(),
                hit.getLocation().z - pos.getZ(), hand, player.isShiftKeyDown());
        if (slot < 0) return InteractionResult.PASS;
        // Reject unauthorized exchanges on both sides; also keep held-item use from firing.
        if (player.isSpectator() || !player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.FAIL;
        if (!(level.getBlockEntity(pos) instanceof ShelfBlockEntity shelf)) return InteractionResult.PASS;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() && shelf.displayedItem(slot).isEmpty()) return InteractionResult.CONSUME;
        if (!level.isClientSide) {
            boolean inserting = !held.isEmpty();
            player.setItemInHand(InteractionHand.MAIN_HAND, shelf.exchange(slot, held, player.getAbilities().instabuild));
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            level.playSound(null, pos, inserting ? SoundEvents.CHISELED_BOOKSHELF_INSERT : SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ShelfBlockEntity shelf) shelf.dropContents();
            super.onRemove(state, level, pos, replacement, moving);
        }
    }
}
