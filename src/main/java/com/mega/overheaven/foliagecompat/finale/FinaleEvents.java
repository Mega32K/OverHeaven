package com.mega.overheaven.foliagecompat.finale;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class FinaleEvents {
    @SubscribeEvent public void start(ServerAboutToStartEvent event) { FinaleManager.get(event.getServer()); }
    @SubscribeEvent public void end(ServerStoppedEvent event) { FinaleManager.unload(event.getServer()); }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) { if(event.phase==TickEvent.Phase.END) FinaleManager.get(event.getServer()).tick(); }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) { sync(event); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { sync(event); }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) { sync(event); }
    private void sync(PlayerEvent event) { if(event.getEntity() instanceof ServerPlayer p) FinaleManager.get(p.server).snapshot(p); }
    @SubscribeEvent public void breaking(BlockEvent.BreakEvent event) { if(event.getLevel() instanceof Level level && FinaleManager.locked(level,event.getPos())) event.setCanceled(true); }
    @SubscribeEvent public void placing(BlockEvent.EntityPlaceEvent event) { if(event.getLevel() instanceof Level level && FinaleManager.locked(level,event.getPos())) event.setCanceled(true); }
    @SubscribeEvent public void interact(PlayerInteractEvent.RightClickBlock event) {
        if(FinaleManager.locked(event.getLevel(),event.getPos()) || event.getFace()!=null && FinaleManager.locked(event.getLevel(),event.getPos().relative(event.getFace()))) event.setCanceled(true);
    }
    @SubscribeEvent public void explode(ExplosionEvent.Detonate event) { event.getAffectedBlocks().removeIf(pos->FinaleManager.locked(event.getLevel(),pos)); }
    @SubscribeEvent public void piston(PistonEvent.Pre event) {
        if(!(event.getLevel() instanceof Level level)) return;
        var resolver=event.getStructureHelper(); resolver.resolve();
        java.util.List<BlockPos> affected=new java.util.ArrayList<>(resolver.getToPush()); affected.addAll(resolver.getToDestroy()); affected.add(event.getFaceOffsetPos()); affected.add(event.getPos());
        for(BlockPos pos:affected) if(FinaleManager.locked(level,pos) || FinaleManager.locked(level,pos.relative(event.getDirection())) || FinaleManager.locked(level,pos.relative(event.getDirection().getOpposite()))) { event.setCanceled(true); return; }
    }
}
