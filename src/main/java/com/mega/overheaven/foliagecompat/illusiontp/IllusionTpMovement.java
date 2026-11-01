package com.mega.overheaven.foliagecompat.illusiontp;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Physical-side bridge for Entity.move; no client class is linked on a dedicated server. */
public final class IllusionTpMovement {
    private static Predicate<Entity> clientLock = entity -> false;
    private IllusionTpMovement() {}
    public static void clientLock(Predicate<Entity> predicate) { clientLock = predicate; }
    public static boolean locked(Entity entity) {
        return entity instanceof ServerPlayer player ? IllusionTpManager.isActive(player)
                : entity.level().isClientSide && clientLock.test(entity);
    }
}
