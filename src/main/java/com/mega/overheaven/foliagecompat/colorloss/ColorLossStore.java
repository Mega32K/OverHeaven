package com.mega.overheaven.foliagecompat.colorloss;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class ColorLossStore extends SavedData {
    public static final int VERSION = 2;
    private ColorLossTransition global = ColorLossTransition.steady(ColorLossProfile.uniform(0));
    private final Map<UUID, ColorLossTransition> players = new HashMap<>();

    public static ColorLossStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ColorLossStore::load, ColorLossStore::new, "overheaven_color_loss");
    }
    public ColorLossTransition global() { return global; }
    public ColorLossTransition forPlayer(UUID id) { return players.getOrDefault(id, global); }
    public boolean overridden(UUID id) { return players.containsKey(id); }
    public void global(ColorLossTransition value) { global = value; setDirty(); }
    public void player(UUID id, ColorLossTransition value) { players.put(id, value); setDirty(); }
    public void inherit(UUID id) { players.remove(id); setDirty(); }

    private static ListTag profile(ColorLossProfile profile) {
        ListTag list = new ListTag();
        for (float value : profile.values()) list.add(FloatTag.valueOf(value));
        return list;
    }
    private static ColorLossProfile profile(ListTag list, int version) {
        if (version < VERSION) {
            if (list.size() != 360) throw new IllegalArgumentException("Invalid legacy color-loss curve");
            return new ColorLossProfile(new float[]{list.getFloat(0), list.getFloat(120), list.getFloat(240)});
        }
        float[] values = new float[list.size()];
        for (int i = 0; i < values.length; i++) values[i] = list.getFloat(i);
        return new ColorLossProfile(values);
    }
    private static CompoundTag encode(ColorLossTransition transition) {
        CompoundTag tag = new CompoundTag();
        tag.put("from", profile(transition.from())); tag.put("to", profile(transition.to()));
        tag.putLong("start", transition.start()); tag.putInt("duration", transition.duration());
        return tag;
    }
    private static ColorLossTransition decode(CompoundTag tag, int version) {
        return new ColorLossTransition(profile(tag.getList("from", Tag.TAG_FLOAT), version), profile(tag.getList("to", Tag.TAG_FLOAT), version),
                tag.getLong("start"), tag.getInt("duration"));
    }
    public static ColorLossStore load(CompoundTag tag) {
        int version = tag.getInt("version");
        if (version > VERSION) throw new IllegalArgumentException("Unsupported color-loss save version " + version);
        ColorLossStore store = new ColorLossStore(); store.global = decode(tag.getCompound("global"), version);
        CompoundTag players = tag.getCompound("players");
        for (String id : players.getAllKeys()) store.players.put(UUID.fromString(id), decode(players.getCompound(id), version));
        if (version < VERSION) store.setDirty();
        return store;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", VERSION);
        tag.put("global", encode(global)); CompoundTag entries = new CompoundTag();
        players.forEach((id, value) -> entries.put(id.toString(), encode(value))); tag.put("players", entries);
        return tag;
    }
}
