package com.mega.overheaven.foliagecompat.finale.mixin;

import com.mega.overheaven.foliagecompat.blackhole.client.FinaleAudio;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import java.util.Map;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public abstract class FinaleSoundMixin {
    @Shadow @Final private Map<SoundInstance,ChannelAccess.ChannelHandle> instanceToChannel;
    @Shadow private float calculateVolume(SoundInstance sound) { throw new AssertionError(); }
    @Unique private boolean overheaven$ducked;
    @Inject(method="tick(Z)V",at=@At("TAIL"))
    private void overheaven$gain(boolean paused,CallbackInfo callback) {
        boolean active=com.mega.overheaven.foliagecompat.blackhole.client.FinaleClient.active();
        if(!active && !overheaven$ducked) return;
        instanceToChannel.forEach(this::overheaven$apply);
        if(active && !paused) { boolean frozen=com.mega.overheaven.foliagecompat.blackhole.client.FinaleClient.state().paused(); instanceToChannel.forEach((sound,channel)-> { if(FinaleAudio.own(sound)) channel.execute(c->{ if(frozen) c.pause(); else c.unpause(); }); }); }
        overheaven$ducked=active;
    }
    @Inject(method="play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",at=@At("RETURN"))
    private void overheaven$newSound(SoundInstance sound,CallbackInfo callback) {
        if(!com.mega.overheaven.foliagecompat.blackhole.client.FinaleClient.active()) return;
        var channel=instanceToChannel.get(sound); if(channel!=null) overheaven$apply(sound,channel);
    }
    @Unique private void overheaven$apply(SoundInstance sound,ChannelAccess.ChannelHandle handle) {
        // Never feed the envelope back into SoundEngine's zero-volume/stop decisions.
        float volume=calculateVolume(sound)*FinaleAudio.gain(sound); handle.execute(channel->channel.setVolume(volume));
    }
}
