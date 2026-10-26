package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import com.mega.overheaven.foliagecompat.PatchLog;

final class GpuDiagnostics implements AutoCloseable {
    private final int[][] queries=new int[8][8];
    private final boolean[][] written=new boolean[8][4];
    private final boolean[] started=new boolean[4];
    private int slot=-1, samples;
    private double total;
    private boolean enabled;
    void frame(boolean diagnostics,boolean visible,String path,int width,int height) {
        enabled=diagnostics && visible;
        if(!enabled) return;
        if(queries[0][0]==0) for(int[] row:queries) for(int index=0;index<row.length;index++) row[index]=glGenQueries();
        slot=(slot+1)%queries.length;
        boolean ready=true, any=false;
        for(int part=0;part<4;part++) if(written[slot][part]) { any=true; ready &= glGetQueryObjecti(queries[slot][part*2+1],GL_QUERY_RESULT_AVAILABLE)!=0; }
        if(ready && any) {
            double elapsed=0;
            for(int part=0;part<4;part++) if(written[slot][part]) elapsed+=(glGetQueryObjectui64(queries[slot][part*2+1],GL_QUERY_RESULT)-glGetQueryObjectui64(queries[slot][part*2],GL_QUERY_RESULT))/1000000.0;
            total+=elapsed; samples++;
            if(samples%120==0) { PatchLog.LOGGER.info("Black hole GPU own passes: mean={} ms, samples={}, target={}x{}, solveScale={}, steps={}, adapter={} (excludes host rendering)",String.format(java.util.Locale.ROOT,"%.3f",total/samples),samples,width,height,BlackHoleClient.resolution,BlackHoleClient.steps,path); total=0; samples=0; }
        }
        if(!ready) { enabled=false; return; }
        java.util.Arrays.fill(written[slot],false); java.util.Arrays.fill(started,false);
    }
    void start(int part) { if(enabled) { glQueryCounter(queries[slot][part*2],GL_TIMESTAMP); started[part]=true; } }
    void end(int part) { if(enabled && started[part]) { glQueryCounter(queries[slot][part*2+1],GL_TIMESTAMP); written[slot][part]=true; started[part]=false; } }
    @Override public void close() {
        for(int row=0;row<queries.length;row++) for(int index=0;index<queries[row].length;index++) { if(queries[row][index]!=0) glDeleteQueries(queries[row][index]); queries[row][index]=0; }
        for(boolean[] row:written) java.util.Arrays.fill(row,false); java.util.Arrays.fill(started,false); slot=-1; samples=0; total=0; enabled=false;
    }
}
