package com.mega.overheaven.foliagecompat.blackhole;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record BlackHole(String id, String dimension, double x, double y, double z,
                        Map<String, Double> settings, double phase, long epoch, long seed) {
    public static final Map<String, Double> DEFAULTS;
    static {
        Map<String, Double> defaults = new LinkedHashMap<>();
        defaults.put("radius", 3.2); defaults.put("inner", 3.8); defaults.put("outer", 11.0);
        defaults.put("thickness", 0.12); defaults.put("tilt", 30.0); defaults.put("yaw", -83.0);
        defaults.put("brightness", 3.2); defaults.put("rotation", 0.25); defaults.put("infall", 0.12);
        defaults.put("lensing", 1.0); defaults.put("glow", 0.7); defaults.put("playing", 1.0);
        defaults.put("hotColor", (double) 0xfff9f2); defaults.put("coldColor", (double) 0x98765d);
        DEFAULTS = java.util.Collections.unmodifiableMap(defaults);
    }
    public BlackHole {
        if (id == null || !id.matches("[a-zA-Z0-9_.-]{1,48}")) throw new IllegalArgumentException("ID must contain 1-48 letters, digits, _, . or -");
        if (dimension == null || dimension.length()>256 || ResourceLocation.tryParse(dimension) == null) throw new IllegalArgumentException("Invalid dimension");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 30_000_000) throw new IllegalArgumentException("Invalid position");
        if (!Double.isFinite(phase) || phase < 0 || phase > 1.0e12 || epoch < 0) throw new IllegalArgumentException("Invalid animation phase/epoch");
        if (settings == null || !settings.keySet().equals(DEFAULTS.keySet())) throw new IllegalArgumentException("Invalid settings schema");
        settings = Map.copyOf(settings);
        for (var entry : settings.entrySet()) {
            double value = entry.getValue();
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite " + entry.getKey());
            String key = entry.getKey();
            double min = switch (key) { case "radius", "inner", "outer" -> 0.1; case "thickness" -> 0.02; case "rotation", "infall" -> -4; case "tilt", "yaw" -> -360; default -> 0; };
            double max = switch (key) { case "radius", "inner", "outer" -> 128; case "thickness" -> 16; case "rotation", "infall" -> 4; case "tilt", "yaw" -> 360; case "playing" -> 1; case "hotColor", "coldColor" -> 0xffffff; default -> 8; };
            if (value < min || value > max || ((key.endsWith("Color") || key.equals("playing")) && value != Math.rint(value))) throw new IllegalArgumentException("Out of range: " + key + " [" + min + ", " + max + "]");
        }
        if (settings.get("inner") <= settings.get("radius") || settings.get("outer") <= settings.get("inner")) throw new IllegalArgumentException("Require radius < inner < outer");
    }
    public static BlackHole reference(String id, String dimension, double x, double y, double z, long now, long seed) {
        return new BlackHole(id, dimension, x, y, z, DEFAULTS, 0, now, seed);
    }
    public double value(String key) { return settings.get(key); }
    public double time(double now) { return phase + (value("playing") == 1 ? Math.max(0, now - epoch) / 20.0 : 0); }
    public BlackHole setting(String key, String input, long now) {
        if (!DEFAULTS.containsKey(key)) throw new IllegalArgumentException("Unknown property: " + key);
        double parsed;
        if (key.endsWith("Color")) {
            if (!input.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Color requires #RRGGBB (quote it in commands)");
            parsed = Integer.parseInt(input.substring(1), 16);
        } else if (key.equals("playing")) {
            if (!input.equals("true") && !input.equals("false")) throw new IllegalArgumentException("playing requires true or false");
            parsed = input.equals("true") ? 1 : 0;
        } else { parsed = Double.parseDouble(input); }
        var updated = new LinkedHashMap<>(settings); updated.put(key, parsed);
        return new BlackHole(id, dimension, x, y, z, updated, time(now), now, seed);
    }
    public BlackHole move(String level, double nextX, double nextY, double nextZ) {
        return new BlackHole(id, level, nextX, nextY, nextZ, settings, phase, epoch, seed);
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putString("id", id); tag.putString("dimension", dimension);
        tag.putDouble("x", x); tag.putDouble("y", y); tag.putDouble("z", z);
        tag.putDouble("phase", phase); tag.putLong("epoch", epoch); tag.putLong("seed", seed);
        var properties = new CompoundTag(); settings.forEach(properties::putDouble); tag.put("settings", properties);
        return tag;
    }
    public static BlackHole load(CompoundTag tag) {
        if (!tag.contains("id", 8) || !tag.contains("dimension", 8) || !tag.contains("settings", 10)) throw new IllegalArgumentException("Incomplete black hole record");
        for(String key:new String[]{"x","y","z","phase","epoch","seed"}) if(!tag.contains(key,99)) throw new IllegalArgumentException("Missing numeric field "+key);
        var settings = new LinkedHashMap<>(DEFAULTS); var properties = tag.getCompound("settings");
        for (String key : properties.getAllKeys()) {
            if (!DEFAULTS.containsKey(key) || !properties.contains(key, 99)) throw new IllegalArgumentException("Invalid property " + key);
            settings.put(key, properties.getDouble(key));
        }
        return new BlackHole(tag.getString("id"), tag.getString("dimension"), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), settings, tag.getDouble("phase"), tag.getLong("epoch"), tag.getLong("seed"));
    }
}
