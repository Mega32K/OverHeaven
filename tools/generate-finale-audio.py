"""Original deterministic finale synthesis; no samples, recordings, or external media.
Requires imageio-ffmpeg 0.6.0. All sustained voices are periodic at a 12-second loop.
"""
import array, math, random, subprocess, tempfile, wave
from pathlib import Path
import imageio_ffmpeg
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/overheaven_foliage_compat/sounds/finale'
RATE = 32000
LOOPS = ('hum', 'pressure', 'grain', 'tail')
DURATIONS = dict(hum=12, pressure=12, grain=12, tail=12, ignite=2.2,
                 pulse=3.2, crack=1.8, rupture=4.8, impact=5.5,
                 reverse=4.5, time_break=2.8, fold=3.6)
TAU = math.tau

def sine(frequency, time, phase=0):
    return math.sin(TAU * frequency * time + phase)

def periodic(frequency):
    return round(frequency * 12) / 12

def sample(name, t, noise, low, channel):
    offset = channel * .23
    if name == 'hum':
        # Stable beacon slowly resolves into beating, non-harmonic upper partials.
        return (.30*sine(110, t) + .10*sine(165.25, t, offset)
                + .08*sine(220.083333333, t, .32*sine(1/12, t))
                + .04*sine(331.5, t, offset*2)) * (.88+.12*sine(1/12, t))
    if name == 'pressure':
        return (.36*sine(38, t)+.19*sine(57, t)+.08*sine(76.083333333, t))*(.78+.22*sine(1/6, t))
    if name == 'grain':
        value = 0
        for index, frequency in enumerate((554.3333, 830.5, 1109.0833, 1661.25, 2217.0833)):
            pulse = (.5+.5*sine((index+2)/12, t, index+offset))**6
            value += sine(periodic(frequency), t, .4*sine(1/12, t))*pulse*.085
        return value
    if name == 'tail':
        value = 0
        for index, frequency in enumerate((55, 110.1667, 146.8333, 220, 277.25, 329.6667, 440.0833)):
            value += sine(periodic(frequency), t, offset*index+.22*sine((index%3+1)/12, t)) * (.072 if index<4 else .035)
        return value*(.82+.18*sine(1/12, t))
    if name == 'ignite':
        return (.29*sine(1760, t)+.19*sine(2639.5, t)+low*.45) * (1-math.exp(-t*70))*math.exp(-t*3.4)
    if name == 'pulse':
        return (.55*math.sin(TAU*(72*t-6*t*t))+.8*low+.04*noise)*min(1,t/.025)*math.exp(-t*1.65)
    if name == 'crack':
        value = 0
        for start, strength in ((0,.7),(.09,.5),(.26,.8),(.41,.35),(.64,.25)):
            age = t-start
            if age >= 0:
                value += (noise*.32+sine(2380,age)*.14+sine(3711,age)*.09)*strength*math.exp(-age*32)*min(1,age/.001)
        return value
    if name == 'rupture':
        # High transient, scattered glass resonances, long sparse decay; no bass duplication.
        value = noise*.36*math.exp(-t*9)*min(1,t/.001)
        for index, frequency in enumerate((1673, 2401, 3067, 4231, 5581)):
            value += sine(frequency, t, index*.7)*.08*math.exp(-t*(2.1+index*.9))*(.5+.5*sine(7+index,t))
        return value
    if name == 'impact':
        return (.58*math.sin(TAU*(56*t-2.4*t*t))+.15*sine(31,t)+low*1.25+noise*.06)*min(1,t/.008)*math.exp(-t*1.3)
    if name == 'reverse':
        swell = math.sin(math.pi*min(1,t/4.5))**2
        return (math.sin(TAU*(240*t-20*t*t))*.20+math.sin(TAU*(510*t-39*t*t)+offset)*.12+low*.5)*swell
    if name == 'time_break':
        return (.24*math.sin(TAU*(2700*t-480*t*t))+.18*sine(4117,t)+noise*.18)*min(1,t/.002)*math.exp(-t*4.7)
    # Fold: a brief rising-and-compressing choral breath, never a voice or dialogue.
    envelope = math.sin(math.pi*min(1,t/3.6))**2
    return (.15*math.sin(TAU*(130*t+23*t*t))+.10*math.sin(TAU*(194.5*t+34*t*t)+offset)+low*.3)*envelope

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for index, (name, duration) in enumerate(DURATIONS.items()):
        rng = random.Random(3999+index*171)
        channels = 1 if name in ('crack', 'rupture') else 2
        values = array.array('f'); low = 0
        for i in range(round(RATE*duration)):
            t = i/RATE; noise = rng.uniform(-1, 1); low = .985*low+.015*noise
            edge = 1 if name in LOOPS else min(1, (duration-t)/.06)
            for channel in range(channels):
                values.append(sample(name,t,noise,low,channel)*edge)
        peak = max(abs(value) for value in values)
        gain = min(1, .68/peak)
        pcm = array.array('h', (round(value*gain*32767) for value in values))
        with tempfile.TemporaryDirectory() as temp:
            wav = Path(temp)/'source.wav'
            with wave.open(str(wav),'wb') as out:
                out.setnchannels(channels); out.setsampwidth(2); out.setframerate(RATE); out.writeframes(pcm.tobytes())
            subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-v','error','-y','-i',str(wav),
                            '-c:a','libvorbis','-q:a','6','-metadata','comment=Original procedural synthesis; OverHeaven finale',
                            str(OUT/(name+'.ogg'))],check=True)
        print(name, channels, 'channels', duration, 'seconds', 'peak', round(peak*gain,4), flush=True)

if __name__ == '__main__':
    main()
