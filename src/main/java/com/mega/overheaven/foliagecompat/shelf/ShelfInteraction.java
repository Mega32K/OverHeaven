package com.mega.overheaven.foliagecompat.shelf;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;

/** Shared front-face mapping: left/right are as seen by someone facing the shelf. */
final class ShelfInteraction {
    private ShelfInteraction() { }

    static int slot(Direction facing, Direction hitFace, double localX, double localZ,
                    InteractionHand hand, boolean sneaking) {
        if (hand != InteractionHand.MAIN_HAND || sneaking || hitFace != facing) return -1;
        double horizontal = switch (facing) {
            case NORTH -> 1.0 - localX;
            case SOUTH -> localX;
            case WEST -> localZ;
            case EAST -> 1.0 - localZ;
            default -> Double.NaN;
        };
        if (!Double.isFinite(horizontal) || horizontal < -1.0e-7 || horizontal > 1.0 + 1.0e-7) return -1;
        // A reversed facing computes 1-x; round-off must not change the exact boundary's slot.
        return Math.max(0, Math.min(2, (int) Math.floor(horizontal * 3.0 + 1.0e-9)));
    }
}
