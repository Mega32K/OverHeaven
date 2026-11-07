package com.mega.overheaven.foliagecompat.worldwarp.client;

import com.google.gson.Gson;
import com.mega.overheaven.foliagecompat.PatchLog;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;

/** Atomic, exact-version adaptation. Existing graph edits (including color loss) are inputs. */
public final class WorldWarpPackAdapter {
    private static final String RESOURCES = "/assets/overheaven_foliage_compat/shaders/worldwarp/";
    private WorldWarpPackAdapter() {}
    public record Edit(String path, String find, String replace, int count) {}
    public static IncludeGraph adapt(IncludeGraph input, Path root) {
        try {
            String uri = root.toUri().toString();
            if (!uri.startsWith("jar:")) return input;
            Path zip = Path.of(URI.create(uri.substring(4, uri.indexOf("!/"))));
            var digest = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.newInputStream(zip)) {
                byte[] bytes = new byte[65536];
                for (int n; (n = stream.read(bytes)) >= 0;) digest.update(bytes, 0, n);
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            if (!hash.equals("98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a")
                    && !hash.equals("78c80543179c75cd6beccb93dc86c283cf045a65d5e7fc5cf96e1829db51aede")) return input;
            Map<String, List<String>> files = new HashMap<>();
            input.getNodes().forEach((path, node) -> files.put(path.getPathString(), node.getLines()));
            var changed = patch(files);
            IncludeGraph result = input.map(path -> (line, text) -> changed.getOrDefault(path.getPathString(), files.get(path.getPathString())).get(line));
            PatchLog.LOGGER.info("worldWarp: Complementary r5.9.3 source adaptation enabled");
            return result;
        } catch (Exception error) {
            PatchLog.LOGGER.warn("worldWarp: shaderpack input retained; adaptation unavailable ({})", error.toString());
            return input;
        }
    }
    public static Map<String, List<String>> patch(Map<String, List<String>> files) throws IOException {
        Edit[] edits;
        try (var stream = WorldWarpPackAdapter.class.getResourceAsStream(RESOURCES + "world_warp_pack.json")) {
            if (stream == null) throw new IOException("Missing worldWarp manifest");
            edits = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Edit[].class);
        }
        Map<String, List<String>> changed = new LinkedHashMap<>();
        for (Edit edit : edits) {
            if (!files.containsKey(edit.path)) continue; // Only reachable Oculus programs exist in the graph.
            var lines = changed.computeIfAbsent(edit.path, p -> new ArrayList<>(files.get(p)));
            int count = 0;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (int at = 0; (at = line.indexOf(edit.find, at)) >= 0; at += edit.find.length()) count++;
                if (line.contains(edit.find)) {
                    if (line.stripLeading().startsWith("#include")) throw new IOException("Include edit: " + edit.path);
                    lines.set(i, line.replace(edit.find, edit.replace));
                }
            }
            if (count != edit.count) throw new IOException("worldWarp anchor: " + edit.path + " [" + edit.find + "] count=" + count);
        }
        var common = changed.computeIfAbsent("/lib/common.glsl", p -> new ArrayList<>(files.get(p)));
        try (var stream = WorldWarpPackAdapter.class.getResourceAsStream(RESOURCES + "world_warp.glsl")) {
            if (stream == null) throw new IOException("Missing worldWarp library");
            common.set(0, new String(stream.readAllBytes(), StandardCharsets.UTF_8) + "\n" + common.get(0));
        }
        String reflections = "/lib/materials/materialMethods/reflections.glsl";
        if (files.containsKey(reflections)) {
            var lines = changed.computeIfAbsent(reflections, p -> new ArrayList<>(files.get(p)));
            try (var stream = WorldWarpPackAdapter.class.getResourceAsStream(RESOURCES + "world_warp_reflection.glsl")) {
                if (stream == null) throw new IOException("Missing worldWarp reflection wrapper");
                int last = lines.size() - 1;
                lines.set(last, lines.get(last) + "\n" + new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return changed;
    }
}
