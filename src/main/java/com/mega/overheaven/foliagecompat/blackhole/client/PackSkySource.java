package com.mega.overheaven.foliagecompat.blackhole.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Reads the selected audited pack, never embeds or substitutes a second procedural star field. */
final class PackSkySource {
    static final String ORIGINAL_SHA256="98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a";
    static final String FULL_SPHERE_SHA256="78c80543179c75cd6beccb93dc86c283cf045a65d5e7fc5cf96e1829db51aede";
    static final String[] OPTIONS={"NIGHT_STAR_AMOUNT","TM_EXPOSURE","TM_CONTRAST","TM_WHITE_PATH","TM_DARK_DESATURATION","T_SATURATION","T_VIBRANCE"};
    private PackSkySource() {}
    static String hash(Path path) throws IOException {
        try(var input=Files.newInputStream(path)) {
            var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int count;
            while((count=input.read(buffer))!=-1) digest.update(buffer,0,count);
            return HexFormat.of().formatHex(digest.digest());
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static String load(Path path,Map<String,String> options,String adapter) throws IOException {
        if(!FULL_SPHERE_SHA256.equals(hash(path))) throw new IOException("Finale sky requires the audited full-sphere workspace pack (SHA-256 "+FULL_SPHERE_SHA256+")");
        try(var zip=new ZipFile(path.toFile())) {
            String common=read(zip,"shaders/lib/common.glsl"),stars=read(zip,"shaders/lib/atmospherics/stars.glsl");
            String display=read(zip,"shaders/program/composite5.glsl");
            var defines=new StringBuilder();
            for(String key:OPTIONS) {
                var matcher=Pattern.compile("(?m)^\\s*#define\\s+"+key+"\\s+([0-9.]+)").matcher(common);
                if(!matcher.find()) throw new IOException("Missing sky option "+key);
                String value=options.getOrDefault(key,matcher.group(1));
                if(!value.matches("[0-9]+(?:\\.[0-9]+)?")) throw new IOException("Invalid sky option "+key);
                double number=Double.parseDouble(value);
                if(!Double.isFinite(number) || number<0 || number>8 || (key.equals("NIGHT_STAR_AMOUNT") && (number>4 || number!=(int)number))) throw new IOException("Unsupported sky option "+key);
                defines.append("#define ").append(key).append(' ').append(value).append('\n');
            }
            // This include initializes atmospheric colors that GetStars does not use. The audited
            // library has no further includes; compile its actual star functions unchanged.
            stars=stars.replace("#include \"/lib/colors/skyColors.glsl\"","");
            String tonemap=function(display,"void LinearToRGB(")+"\n"+function(display,"void DoCompTonemap(")+"\n"+function(display,"void DoBSLColorSaturation(");
            String result=adapter.replace("// PACK_OPTIONS",defines).replace("// PACK_STARS",stars).replace("// PACK_TONEMAP",tonemap);
            if(result.contains("#include")) throw new IOException("Unexpected include in audited pack sky");
            return result;
        }
    }
    private static String read(ZipFile zip,String name) throws IOException {
        var entry=zip.getEntry(name);
        if(entry==null || entry.getSize()<0 || entry.getSize()>512*1024) throw new IOException("Missing/oversized pack source "+name);
        try(var stream=zip.getInputStream(entry)) { return new String(stream.readNBytes(512*1024),StandardCharsets.UTF_8); }
    }
    private static String function(String source,String signature) throws IOException {
        int start=source.indexOf(signature),brace=source.indexOf('{',start);
        if(start<0 || brace<0) throw new IOException("Missing pack display function "+signature);
        int depth=1,end=brace+1;
        while(end<source.length() && depth!=0) { char c=source.charAt(end++);if(c=='{') depth++;else if(c=='}') depth--; }
        if(depth!=0) throw new IOException("Unclosed pack display function");
        return source.substring(start,end);
    }
}
