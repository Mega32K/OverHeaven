package com.mega.overheaven.foliagecompat.finale;

import com.mega.overheaven.foliagecompat.PatchLog;
import com.mega.overheaven.foliagecompat.blackhole.BlackHole;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.AABB;

public final class FinaleManager {
    private static final Map<MinecraftServer,FinaleManager> MANAGERS=new IdentityHashMap<>();
    private static final ThreadLocal<Integer> WRITING=ThreadLocal.withInitial(()->0);
    private final MinecraftServer server;
    private final FinaleJournal journal;
    private final Map<String,Run> runs=new LinkedHashMap<>();
    private final ArrayDeque<FinaleJournal.Key> recovery=new ArrayDeque<>();
    private final Set<FinaleJournal.Key> restored=new HashSet<>();
    private boolean ioFailed;
    private long recoveryNanos,recoveryRetryTick;
    public static FinaleManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server,FinaleManager::new);
    }
    private FinaleManager(MinecraftServer server) {
        this.server=server; FinaleJournal loaded=null;
        try { loaded=new FinaleJournal(server); loaded.entries.values().forEach(FinaleJournal.Entry::original); recovery.addAll(loaded.entries.keySet()); }
        catch(IOException|RuntimeException error) { ioFailed=true; PatchLog.LOGGER.error("Finale recovery unavailable; journal retained and new finales disabled",error); }
        journal=loaded;
    }
    public static void unload(MinecraftServer server) { MANAGERS.remove(server); }
    public static boolean locked(net.minecraft.world.level.Level level,BlockPos pos) {
        if(level.isClientSide || WRITING.get()>0 || level.getServer()==null) return false;
        FinaleManager manager=MANAGERS.get(level.getServer());
        return manager!=null && manager.journal!=null && manager.journal.locked(level.dimension().location().toString(),pos);
    }
    private ServerLevel level(String dimension) { return server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(dimension))); }
    private boolean write(ServerLevel level,BlockPos pos,net.minecraft.world.level.block.state.BlockState state) {
        WRITING.set(WRITING.get()+1); try { return level.setBlock(pos,state,2); } finally { WRITING.set(WRITING.get()-1); }
    }
    public void play(BlackHole hole) {
        if(ioFailed) throw new IllegalArgumentException("Recovery I/O failed; inspect server log before playing again");
        if(runs.containsKey(hole.dimension())) throw new IllegalArgumentException("This dimension already has a finale");
        if(journal.count(hole.dimension())>0) throw new IllegalArgumentException("Glass recovery pending in this dimension: "+journal.count(hole.dimension()));
        if(level(hole.dimension())==null) throw new IllegalArgumentException("Dimension unavailable");
        Run run=new Run(hole,FinaleStore.get(server).get(hole.id())); runs.put(hole.dimension(),run); broadcast(run);
    }
    public boolean active(String id) { return runs.values().stream().anyMatch(r->r.hole.id().equals(id)); }
    private Run require(String id) { return runs.values().stream().filter(r->r.hole.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("No running finale: "+id)); }
    public void pause(String id,boolean paused) { Run run=require(id); run.clock.pause(paused); broadcast(run); }
    public void stop(String id) { Run run=require(id); if(run.clock.stopping()<0) { run.clock.stop(); broadcast(run); } }
    public void beforeSourceChange(String id) { if(active(id)) stop(id); }
    public String status(String id) {
        if(journal==null || ioFailed) return "disabled: recovery journal error; inspect server log; records retained";
        Run run=runs.values().stream().filter(r->r.hole.id().equals(id)).findFirst().orElse(null);
        return run==null?"idle; recovery="+journal.entries.size():"phase="+run.timeline.frame(run.clock.elapsed()).phase().id()+", ticks="+run.clock.elapsed()+", paused="+run.clock.paused()+", stop="+run.clock.stopping()+", fragments=disabled, recovery="+journal.count(run.hole.dimension())+", recovery ms="+recoveryNanos/1e6;
    }
    public void snapshot(ServerPlayer player) {
        String dimension=player.level().dimension().location().toString(); Run run=runs.get(dimension);
        FinaleNetwork.send(player,run==null?FinaleNetwork.Update.clear(dimension,now()):packet(run));
    }
    private long now() { return server.overworld().getGameTime(); }
    private FinaleNetwork.Update packet(Run r) {
        return new FinaleNetwork.Update(r.hole.dimension(),r.id,r.hole,r.timeline,r.clock.elapsed(),r.clock.paused(),false,r.clock.stopping(),now(),new long[0],new long[0]);
    }
    private void broadcast(Run r) { var packet=packet(r); for(ServerPlayer p:server.getPlayerList().getPlayers()) if(p.level().dimension().location().toString().equals(r.hole.dimension())) FinaleNetwork.send(p,packet); }
    public void tick() {
        if(journal==null) return;
        for(Run run:List.copyOf(runs.values())) {
            try {
                if(run.clock.stopping()>=0) { run.clock.tick(); if(run.clock.finished()) { finish(run); continue; } if(run.clock.stopping()%20==0) broadcast(run); continue; }
                if(run.clock.paused()) { if(now()%40==0) broadcast(run); continue; }
                var old=run.timeline.frame(run.clock.elapsed()).phase(); run.clock.tick();
                var frame=run.timeline.frame(run.clock.elapsed());
                if(run.clock.elapsed()>=run.timeline.total()) { finish(run); continue; }
                if(frame.phase()!=old || run.clock.elapsed()%40==0) broadcast(run);
            } catch(Exception error) {
                PatchLog.LOGGER.error("Finale aborted; recovery reservation retained",error); ioFailed=error instanceof IOException; run.clock.stop(); broadcast(run);
            }
        }
        recover();
    }
    private void finish(Run run) {
        runs.remove(run.hole.dimension());
        for(ServerPlayer p:server.getPlayerList().getPlayers()) if(p.level().dimension().location().toString().equals(run.hole.dimension())) FinaleNetwork.send(p,FinaleNetwork.Update.clear(run.hole.dimension(),now()));
    }
    private void recover() {
        if(now()<recoveryRetryTick) return;
        long start=System.nanoTime(); int budget=Math.min(128,recovery.size()),writes=0;
        while(budget-->0) {
            var key=recovery.removeFirst(); var entry=journal.entries.get(key); if(entry==null || restored.contains(key)) continue;
            ServerLevel level=level(key.dimension()); BlockPos pos=BlockPos.of(key.position());
            if(level==null || !level.hasChunkAt(pos)) { recovery.addLast(key); continue; }
            var state=level.getBlockState(pos);
            if(state.equals(entry.original())) { restored.add(key); continue; }
            // Foreign state is never overwritten. Keep the reservation and expose it in status.
            if(!state.isAir() || writes>=32 || !level.getEntities(null,new AABB(pos)).isEmpty()) { recovery.addLast(key); continue; }
            if(write(level,pos,entry.original())) { writes++; restored.add(key); } else recovery.addLast(key);
        }
        if(!restored.isEmpty()) {
            Set<String> ready=new HashSet<>(); for(var key:restored) ready.add(journal.entries.get(key).run());
            ready.removeIf(run->journal.entries.entrySet().stream().anyMatch(e->e.getValue().run().equals(run) && !restored.contains(e.getKey())));
            if(!ready.isEmpty()) try {
                Set<String> dimensions=new HashSet<>(); for(var e:journal.entries.entrySet()) if(ready.contains(e.getValue().run())) dimensions.add(e.getKey().dimension());
                // Flush restored chunks before retiring their write-ahead records, once per run.
                for(String dimension:dimensions) { var level=level(dimension); if(level!=null) level.getChunkSource().save(true); }
                var retired=new LinkedHashMap<FinaleJournal.Key,FinaleJournal.Entry>(); journal.entries.forEach((k,v)->{ if(ready.contains(v.run())) retired.put(k,v); });
                retired.keySet().forEach(journal.entries::remove);
                try { journal.flush(); restored.removeAll(retired.keySet()); } catch(IOException error) { journal.entries.putAll(retired); throw error; }
            } catch(IOException|RuntimeException error) { if(!ioFailed) PatchLog.LOGGER.error("Finale restoration journal could not be retired",error); ioFailed=true; recoveryRetryTick=now()+200; }
        }
        recoveryNanos=System.nanoTime()-start;
    }
    private static final class Run {
        final UUID id=UUID.randomUUID(); final BlackHole hole; final FinaleTimeline timeline; final FinaleClock clock;
        // New shows never scan or modify blocks. Startup recovery still owns all legacy journal entries.
        Run(BlackHole hole,FinaleTimeline timeline) { this.hole=hole; this.timeline=timeline; clock=new FinaleClock(timeline); }
    }
}
