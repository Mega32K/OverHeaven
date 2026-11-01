package com.mega.overheaven.foliagecompat.naidan;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class NaidanBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final String CONTROLLER = "performance";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANDI = RawAnimation.begin().then("andi", Animation.LoopType.PLAY_ONCE);
    private static final RawAnimation LAUGH = RawAnimation.begin().thenLoop("laugh");
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private long playingUntil;
    private String playingAnimation = "";

    public NaidanBlockEntity(BlockPos pos, BlockState state) { super(Naidan.BLOCK_ENTITY.get(), pos, state); }

    public enum Performance {
        // Round up to complete server ticks: andi animation 1.3611s, laugh sound 11.2107s.
        ANDI("andi", 28), LAUGH("laugh", 225);

        final String animation;
        final int ticks;

        Performance(String animation, int ticks) {
            this.animation = animation;
            this.ticks = ticks;
        }

        SoundEvent sound() { return this == ANDI ? Naidan.ANDI.get() : Naidan.LAUGH.get(); }
    }

    public boolean play(ServerLevel level, Performance performance) {
        if (level.getGameTime() < playingUntil) return false;
        finish(level);
        playingAnimation = performance.animation;
        playingUntil = level.getGameTime() + performance.ticks;
        setChanged();
        triggerAnim(CONTROLLER, playingAnimation);
        level.playSound(null, worldPosition, performance.sound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.scheduleTick(worldPosition, getBlockState().getBlock(), performance.ticks);
        return true;
    }

    public void finish(ServerLevel level) {
        if (playingAnimation.isEmpty()) return;
        long remaining = playingUntil - level.getGameTime();
        if (remaining > 0) {
            // An overdue tick from the previous performance must not consume the new stop timer.
            level.scheduleTick(worldPosition, getBlockState().getBlock(), (int) remaining);
            return;
        }
        stopTriggeredAnimation(CONTROLLER, playingAnimation);
        playingAnimation = "";
        playingUntil = 0;
        setChanged();
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("PlayingUntil", playingUntil);
        tag.putString("PlayingAnimation", playingAnimation);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        playingUntil = tag.getLong("PlayingUntil");
        playingAnimation = tag.getString("PlayingAnimation");
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER, 0, state -> state.setAndContinue(IDLE))
                .triggerableAnim("andi", ANDI).triggerableAnim("laugh", LAUGH));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
}
