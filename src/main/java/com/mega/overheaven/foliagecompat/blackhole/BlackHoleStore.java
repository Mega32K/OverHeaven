package com.mega.overheaven.foliagecompat.blackhole;

import com.mega.overheaven.foliagecompat.PatchLog;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class BlackHoleStore extends SavedData {
    public static final int MAX_RECORDS = 256;
    private final Map<String, BlackHole> records = new LinkedHashMap<>();
    public static BlackHoleStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BlackHoleStore::load, BlackHoleStore::new, "overheaven_blackholes");
    }
    public Collection<BlackHole> all() { return java.util.List.copyOf(records.values()); }
    public BlackHole require(String id) {
        BlackHole record = records.get(id);
        if (record == null) throw new IllegalArgumentException("Unknown black hole: " + id);
        return record;
    }
    public void put(BlackHole record) {
        if (!records.containsKey(record.id()) && records.size() >= MAX_RECORDS) throw new IllegalArgumentException("Black hole limit: " + MAX_RECORDS);
        records.put(record.id(), record); setDirty();
    }
    public BlackHole remove(String id) { BlackHole result = require(id); records.remove(id); setDirty(); return result; }
    public static BlackHoleStore load(CompoundTag root) {
        var result = new BlackHoleStore();
        if (root.getInt("schema") != 1) throw new IllegalArgumentException("Unsupported black hole save schema");
        if(!root.contains("schema",3) || !root.contains("holes",9) || (!(root.get("holes") instanceof ListTag values)) || (!values.isEmpty() && values.getElementType()!=10)) throw new IllegalArgumentException("Malformed black hole save root");
        var list = root.getList("holes", 10);
        if (list.size() > MAX_RECORDS) throw new IllegalArgumentException("Too many saved black holes");
        for (int index = 0; index < list.size(); index++) {
            try { var record = BlackHole.load(list.getCompound(index));
                if (result.records.putIfAbsent(record.id(), record) != null) throw new IllegalArgumentException("Duplicate ID " + record.id());
            } catch (IllegalArgumentException exception) { PatchLog.LOGGER.error("Invalid black hole save entry {}: {}", index, exception.getMessage()); }
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag root) {
        root.putInt("schema", 1); var list = new ListTag(); records.values().forEach(record -> list.add(record.save())); root.put("holes", list); return root;
    }
}
