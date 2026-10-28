package com.mega.overheaven.foliagecompat.blackhole.client;

import com.mega.overheaven.foliagecompat.PatchLog;

/** Owns one sky source at a time. A failed pack never silently switches to invented stars. */
final class FinaleSky implements AutoCloseable {
    @FunctionalInterface interface Source { String read() throws Exception; }
    private GlProgram program;
    private Object identity;
    private boolean initialized,packMode;
    GlProgram select(boolean shaders,Object packIdentity,Source vertex,Source source,Source vanilla) {
        if(initialized && shaders==packMode && (!shaders || identity==packIdentity)) return program;
        close(); initialized=true; packMode=shaders; identity=packIdentity;
        try { program=new GlProgram(vertex.read(),(shaders?source:vanilla).read()); }
        catch(Exception error) {
            PatchLog.LOGGER.error("Finale sky unavailable ({}): {}. Sky stays black; no procedural fallback while shaders are enabled.",shaders?"shader pack":"mod stars",error.toString());
        }
        return program;
    }
    String path() { return packMode?program==null?"pack-unavailable":"complementary-full-sphere":"mod-stars"; }
    @Override public void close() {
        if(program!=null) program.close(); program=null;identity=null;initialized=false;
    }
}
