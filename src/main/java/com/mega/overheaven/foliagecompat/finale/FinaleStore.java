package com.mega.overheaven.foliagecompat.finale;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Definitions only. Running shows deliberately do not resume after a server restart. */
public final class FinaleStore extends SavedData {
    private final Map<String,FinaleTimeline> definitions=new LinkedHashMap<>();
    public static FinaleStore get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(FinaleStore::load,FinaleStore::new,"overheaven_finale_definitions"); }
    public FinaleTimeline get(String id) { return definitions.getOrDefault(id,FinaleTimeline.defaults()); }
    public void put(String id,FinaleTimeline timeline) { definitions.put(id,timeline); setDirty(); }
    public void reset(String id) { definitions.remove(id); setDirty(); }
    public static FinaleStore load(CompoundTag tag) {
        if(tag.getInt("schema")!=1) throw new IllegalArgumentException("Unsupported finale definition schema");
        FinaleStore result=new FinaleStore(); CompoundTag entries=tag.getCompound("definitions");
        for(String id:entries.getAllKeys()) result.definitions.put(id,new FinaleTimeline(entries.getIntArray(id)));
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag) { tag.putInt("schema",1); CompoundTag entries=new CompoundTag(); definitions.forEach((id,time)->entries.putIntArray(id,time.durations())); tag.put("definitions",entries); return tag; }
}
