package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import com.mega.overheaven.foliagecompat.PatchLog;
import java.util.*;

/** Nonblocking frame-owned queries. Parent ranges are inclusive, never added to their children. */
final class GpuDiagnostics implements AutoCloseable {
    private static final int WINDOW=600;
    private static final class Event {
        int begin,end; String name; boolean complete,count; long cpu;
        Event(boolean count) { this.count=count;begin=glGenQueries();if(!count) end=glGenQueries(); }
        void close() { glDeleteQueries(begin);if(end!=0) glDeleteQueries(end); }
    }
    private static final class Slot {
        final List<Event> events=new ArrayList<>();
        final Map<String,Double> cpu=new LinkedHashMap<>();
        int used,subjects;String context;long pixels,bytes;
    }
    private static final class Series {
        final double[] values=new double[WINDOW];int size,cursor;
        void add(double value) { values[cursor]=value;cursor=(cursor+1)%WINDOW;size=Math.min(size+1,WINDOW); }
        String summary() {
            double[] sorted=Arrays.copyOf(values,size);Arrays.sort(sorted);double sum=0;for(double v:sorted) sum+=v;
            return String.format(Locale.ROOT,"mean=%.3f p95=%.3f p99=%.3f max=%.3f",sum/size,sorted[(int)Math.ceil(size*.95)-1],sorted[(int)Math.ceil(size*.99)-1],sorted[size-1]);
        }
    }
    private static final class Group { final Map<String,Series> series=new LinkedHashMap<>();int frames; }
    private final Slot[] slots=new Slot[8];
    private final Map<String,Event> open=new HashMap<>();
    private final Map<String,Group> groups=new LinkedHashMap<>();
    private Slot current;private int slot=-1,frames,skipped;private boolean enabled;
    GpuDiagnostics() { for(int i=0;i<slots.length;i++) slots[i]=new Slot(); }
    void frame(boolean diagnostics,boolean visible,String path,int width,int height) {
        enabled=false;current=null;open.clear();frames++;
        if(!diagnostics || !visible) return;
        slot=(slot+1)%slots.length;Slot next=slots[slot];
        for(int i=0;i<next.used;i++) {
            Event e=next.events.get(i);
            if(e.complete && glGetQueryObjecti(e.count?e.begin:e.end,GL_QUERY_RESULT_AVAILABLE)==0) { skipped++;return; }
        }
        if(next.used>0) consume(next);
        next.used=0;next.cpu.clear();next.subjects=0;next.pixels=0;next.bytes=0;
        next.context=path+" "+width+"x"+height+" scale="+BlackHoleClient.resolution+" steps="+BlackHoleClient.steps;
        current=next;enabled=true;
    }
    private Event event(String name,boolean count) {
        if(!enabled) return null;
        int index=current.used++;Event e;
        if(index==current.events.size()) { e=new Event(count);current.events.add(e); }
        else { e=current.events.get(index);if(e.count!=count) { e.close();e=new Event(count);current.events.set(index,e); } }
        e.name=name;e.complete=false;e.cpu=System.nanoTime();return e;
    }
    void start(String name) { Event e=event(name,false);if(e!=null) { glQueryCounter(e.begin,GL_TIMESTAMP);open.put(name,e); } }
    void end(String name) {
        Event e=open.remove(name);if(e==null) return;
        glQueryCounter(e.end,GL_TIMESTAMP);e.complete=true;cpu(name,(System.nanoTime()-e.cpu)/1e6);
    }
    void start(int part) { start("pass"+part); }
    void end(int part) { end("pass"+part); }
    void cpu(String name,double millis) { if(enabled) current.cpu.merge(name,millis,Double::sum); }
    void metrics(int subjects,long pixels,long bytes) { if(enabled) { current.subjects=subjects;current.pixels=pixels;current.bytes=bytes; } }
    boolean classify() { return enabled && frames%120==1 && glGetQueryi(GL_SAMPLES_PASSED,GL_CURRENT_QUERY)==0; }
    void countStart(String name) { Event e=event(name,true);if(e!=null) { glBeginQuery(GL_SAMPLES_PASSED,e.begin);open.put(name,e); } }
    void countEnd(String name) { Event e=open.remove(name);if(e!=null) { glEndQuery(GL_SAMPLES_PASSED);e.complete=true; } }
    private void consume(Slot s) {
        if(groups.size()>64 && !groups.containsKey(s.context)) groups.remove(groups.keySet().iterator().next());
        Group group=groups.computeIfAbsent(s.context,k->new Group());Map<String,Double> totals=new LinkedHashMap<>();
        for(int i=0;i<s.used;i++) {
            Event e=s.events.get(i);if(!e.complete) continue;
            double value=e.count?glGetQueryObjectui64(e.begin,GL_QUERY_RESULT):(glGetQueryObjectui64(e.end,GL_QUERY_RESULT)-glGetQueryObjectui64(e.begin,GL_QUERY_RESULT))/1e6;
            totals.merge((e.count?"pixels/":"GPU/")+e.name,value,Double::sum);
        }
        if(totals.containsKey("GPU/subjects-total")) totals.put("GPU/subjects-without-diagnostics",Math.max(0,totals.get("GPU/subjects-total")-totals.getOrDefault("GPU/diagnostic-classify",0.)));
        s.cpu.forEach((name,value)->totals.put("CPU/"+name,value));
        if(totals.containsKey("pixels/eligible")) totals.put("fallback-percent",100*totals.getOrDefault("pixels/fallback",0.)/Math.max(1.,totals.get("pixels/eligible")));
        totals.put("subjects",(double)s.subjects);totals.put("composite-pixels",(double)s.pixels);totals.put("targets-MiB",s.bytes/1048576.);
        totals.forEach((name,value)->group.series.computeIfAbsent(name,k->new Series()).add(value));
        if(++group.frames%120==0) {
            StringBuilder text=new StringBuilder();group.series.forEach((name,series)->text.append(name).append("[").append(series.summary()).append("] "));
            PatchLog.LOGGER.info("Black hole diagnostics {} frames={} skipped={} {} (ms except counts; parent GPU/CPU scopes inclusive, not additive; excludes host rendering)",s.context,group.frames,skipped,text);
        }
    }
    @Override public void close() {
        for(Slot s:slots) { for(Event e:s.events) e.close();s.events.clear();s.cpu.clear();s.used=0; }
        open.clear();groups.clear();current=null;slot=-1;frames=0;skipped=0;enabled=false;
    }
}
