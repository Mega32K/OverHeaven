package com.mega.overheaven.foliagecompat.colorloss.client;

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

/** Exact-version, atomic source adaptation before Oculus include expansion/preprocessing. */
public final class ColorLossPackAdapter {
    public static final String PACK_SHA256 = "98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a";
    // Audited r5.9.3 development copy: only full-sphere stars and their sky/reflection coordinates differ.
    private static final String FULL_SPHERE_STARS_PACK_SHA256 = "78c80543179c75cd6beccb93dc86c283cf045a65d5e7fc5cf96e1829db51aede";
    private static final String RESOURCES = "/assets/overheaven_foliage_compat/shaders/blackhole/";
    private ColorLossPackAdapter() {}
    public record Edit(String path, String find, String replace, int count) {}

    public static IncludeGraph adapt(IncludeGraph original, Path root) {
        try {
            URI uri = root.toUri();
            if (!uri.getScheme().equals("jar")) throw new IOException("Expected the audited shaderpack ZIP");
            String raw = uri.toString();
            Path archive = Path.of(URI.create(raw.substring(4, raw.indexOf("!/"))));
            String fingerprint;
            try (var input = Files.newInputStream(archive)) {
                var digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[65536];
                for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n);
                fingerprint = HexFormat.of().formatHex(digest.digest());
            }
            if (!fingerprint.equals(PACK_SHA256) && !fingerprint.equals(FULL_SPHERE_STARS_PACK_SHA256))
                throw new IOException("Shaderpack fingerprint mismatch: " + fingerprint);
            Map<String, List<String>> files = new HashMap<>();
            original.getNodes().forEach((path, node) -> files.put(path.getPathString(), node.getLines()));
            Map<String, List<String>> changed = patch(files);
            IncludeGraph adapted = original.map(path -> (line, text) -> changed.getOrDefault(path.getPathString(), files.get(path.getPathString())).get(line));
            PatchLog.LOGGER.info("Color loss: layered source adaptation enabled (shaderpack SHA-256 {})", fingerprint);
            return adapted;
        } catch (Exception error) {
            PatchLog.LOGGER.warn("Color loss: original shaderpack retained; final-image approximation ({})", error.toString());
            return original;
        }
    }

    /** Also used by the standalone exact-source GPU verification, with the original include indices intact. */
    public static Map<String, List<String>> patch(Map<String, List<String>> files) throws IOException {
        Edit[] edits;
        try (var input = ColorLossPackAdapter.class.getResourceAsStream(RESOURCES + "color_loss_pack.json")) {
            if (input == null) throw new IOException("Missing color loss adapter manifest");
            edits = new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Edit[].class);
        }
        Map<String, List<String>> changed = new LinkedHashMap<>();
        for (Edit edit : edits) {
            List<String> original = files.get(edit.path);
            // Oculus only includes reachable programs; e.g. 1.8.0 never loads gbuffers_lightning.
            if (original == null) continue;
            List<String> output = changed.computeIfAbsent(edit.path, key -> new ArrayList<>(original));
            int matches = 0;
            for (int i = 0; i < original.size(); i++) {
                String line = original.get(i);
                if (!line.strip().equals(edit.find)) continue;
                if (line.stripLeading().startsWith("#include") || !output.get(i).equals(line))
                    throw new IOException("Overlapping/include source edit: " + edit.path + ":" + i);
                output.set(i, line.substring(0, line.length() - line.stripLeading().length()) + edit.replace);
                matches++;
            }
            if (matches != edit.count) throw new IOException("Source anchor mismatch: " + edit.path + " [" + edit.find + "], " + matches);
        }
        String common = "/lib/common.glsl";
        var lines = new ArrayList<>(files.get(common));
        try (var input = ColorLossPackAdapter.class.getResourceAsStream(RESOURCES + "color_loss_common.glsl")) {
            if (input == null) throw new IOException("Missing color loss shader library");
            lines.set(0, "#define OH_COLOR_LOSS_PACK\n" + new String(input.readAllBytes(), StandardCharsets.UTF_8) + "\n" + lines.get(0));
        }
        changed.put(common, lines);
        return changed;
    }
}
