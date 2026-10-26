package com.mega.overheaven.foliagecompat.blackhole;

import java.util.LinkedHashMap;
import java.util.List;

public final class BlackHoleReplica {
    private final LinkedHashMap<String,BlackHole> records=new LinkedHashMap<>();
    private List<BlackHole> cached=List.of();
    private String dimension="";
    private long serverTime, elapsed;
    public void accept(BlackHoleNetwork.Update update) {
        if(update.operation()==0) { records.clear(); dimension=update.dimension(); }
        if(!dimension.equals(update.dimension())) return;
        if(update.operation()==2) records.remove(update.removed());
        else for(var hole:update.records()) records.put(hole.id(),hole);
        cached=List.copyOf(records.values()); serverTime=update.time(); elapsed=0;
    }
    public List<BlackHole> records(String currentDimension) { return dimension.equals(currentDimension)?cached:List.of(); }
    public void tick() { elapsed++; }
    public double time(float partial) { return serverTime+elapsed+partial; }
    public void clear() { records.clear(); cached=List.of(); dimension=""; serverTime=0; elapsed=0; }
}
