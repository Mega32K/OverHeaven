package com.mega.overheaven.foliagecompat.blackhole.client;

import com.mega.overheaven.foliagecompat.FoliageCompat;
import com.mega.overheaven.foliagecompat.finale.FinaleTimeline;
import com.mega.overheaven.foliagecompat.finale.FinaleChoreography;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Original looping stems plus normalized cues; late joiners never replay elapsed impulses. */
public final class FinaleAudio {
    private static final List<Voice> voices=new ArrayList<>();
    private static final Voice[] stems=new Voice[4];
    private static double cursor;
    private static final java.util.Set<String> fired=new java.util.HashSet<>();
    public static void resetCursor(double ticks) { cursor=ticks; fired.clear(); }
    public static boolean own(SoundInstance sound) { return sound instanceof Voice; }
    public static float gain(SoundInstance sound) {
        if(!own(sound)) return FinaleClient.gameGain();
        if(!FinaleClient.active() || FinaleClient.state().paused()) return 0;
        var f=FinaleClient.frame(0); float value=f.phase()==FinaleTimeline.Phase.SILENCE?(float)(1-FinaleTimeline.smooth(0,.5,f.progress())):f.phase()==FinaleTimeline.Phase.RETURN?0:1;
        return value*(FinaleClient.stopping(0)>=0?1-FinaleClient.stopping(0):1);
    }
    public static void tick() {
        if(!FinaleClient.active()) { close(); return; }
        var state=FinaleClient.state(); double now=FinaleClient.ticks(0); var frame=FinaleClient.frame(0);
        String[] names={"hum","pressure","grain","tail"};
        for(int i=0;i<stems.length;i++) if(stems[i]==null || !Minecraft.getInstance().getSoundManager().isActive(stems[i])) { stems[i]=new Voice(names[i],true,null); voices.add(stems[i]); Minecraft.getInstance().getSoundManager().play(stems[i]); }
        for(int i=0;i<stems.length;i++) { stems[i].volume((float)FinaleChoreography.stem(i,frame)); stems[i].pitch((float)FinaleChoreography.pitch(i,frame)); }
        if(!state.paused() && state.stopping()<0 && now>=cursor && now-cursor<=5) {
            for(var cue:FinaleChoreography.CUES) if(!cue.glass() || FinaleClient.glass().length>0) cue(cue,now);
        }
        cursor=now; voices.removeIf(Voice::isStopped);
    }
    private static void cue(FinaleChoreography.Cue cue,double now) {
        var phase=cue.phase(); double fraction=cue.fraction(); String name=cue.sound(); boolean spatial=cue.glass();
        var timeline=FinaleClient.state().timeline(); double at=timeline.start(phase)+timeline.duration(phase)*fraction;
        if(cursor<at && now>=at && fired.add(phase.id()+":"+fraction+":"+name)) {
            BlockPos pos=spatial?BlockPos.of(FinaleClient.glass()[(int)(fraction*(FinaleClient.glass().length-1))]):null;
            var voice=new Voice(name,false,pos); voice.volume(cue.gain()); voices.add(voice); Minecraft.getInstance().getSoundManager().play(voice);
        }
    }
    public static void close() {
        if(voices.isEmpty()) return;
        var sound=Minecraft.getInstance().getSoundManager(); for(Voice voice:voices) sound.stop(voice); voices.clear(); Arrays.fill(stems,null);
    }
    private static final class Voice extends AbstractTickableSoundInstance {
        private int age;
        Voice(String name,boolean loop,BlockPos position) {
            super(SoundEvent.createVariableRangeEvent(new ResourceLocation(FoliageCompat.MOD_ID,"finale."+name)),SoundSource.AMBIENT,RandomSource.create(0));
            looping=loop; relative=position==null; attenuation=position==null?Attenuation.NONE:Attenuation.LINEAR; volume=loop?0:.7f; pitch=1;
            if(position!=null) { x=position.getX()+.5; y=position.getY()+.5; z=position.getZ()+.5; }
        }
        void volume(float value) { volume=value; }
        void pitch(float value) { pitch=value; }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() { if(!FinaleClient.active() || !looping && !FinaleClient.state().paused() && ++age>160) stop(); }
    }
}
