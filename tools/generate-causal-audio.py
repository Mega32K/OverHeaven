"""Deterministic original synthesis for Causal Unsealing. No sampled external media.
Run with Python + imageio-ffmpeg. Only the four causal assets are written.
"""
import array, json, math, random, subprocess, tempfile, wave
from pathlib import Path
import imageio_ffmpeg
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'src/main/resources/assets/overheaven_foliage_compat/sounds/finale'
RATE=32000
DURATIONS={'causal_charge':3.98,'causal_fault':1.32,'causal_release':1.38,'causal_recoil':2.05}
def voice(name,t,n,low,c):
    s=lambda f,phase=0: math.sin(math.tau*f*t+phase)
    attack=min(1,t/.018); phase=c*.31
    if name=='causal_charge':
        u=min(1,t/3.98); env=math.sin(math.pi*u)**.7
        return env*(.23*s(36)+.13*s(54.2,phase)+.08*math.sin(math.tau*(160*t+23*t*t)+phase)+low*.5)
    if name=='causal_fault':
        env=attack*math.exp(-t*3.2)
        return env*(.25*math.sin(math.tau*(94*t+44*t*t))+.15*s(723,phase)+.08*s(1091,phase*2)+low*.8+n*.025)
    if name=='causal_release':
        env=min(1,t/.005)*math.exp(-t*3.0)
        body=.48*math.sin(math.tau*(62*t-12*t*t))+.18*s(31)+low*1.2
        crack=n*.19*math.exp(-t*22)+.11*s(2153,phase)*math.exp(-t*8)
        return env*(body+crack)
    u=min(1,t/2.05); env=math.sin(math.pi*u)**.8*(1-u*.6)
    return env*(.18*math.sin(math.tau*(330*t-64*t*t)+phase)+.13*math.sin(math.tau*(496*t-95*t*t)-phase)+.13*s(49,phase)+low*.5)
def main():
    OUT.mkdir(parents=True,exist_ok=True)
    records={}
    for index,(name,duration) in enumerate(DURATIONS.items()):
        rng=random.Random(399955+index); low=0; values=array.array('f')
        for i in range(round(RATE*duration)):
            t=i/RATE; n=rng.uniform(-1,1); low=.97*low+.03*n
            edge=min(1,(duration-t)/.06)
            for c in range(2): values.append(voice(name,t,n,low,c)*edge)
        peak=max(abs(v) for v in values); gain=min(1,.76/peak)
        pcm=array.array('h',(round(v*gain*32767) for v in values))
        with tempfile.TemporaryDirectory() as temp:
            wav=Path(temp)/'source.wav'
            with wave.open(str(wav),'wb') as out:
                out.setnchannels(2);out.setsampwidth(2);out.setframerate(RATE);out.writeframes(pcm.tobytes())
            subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-v','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','6','-metadata','comment=Original deterministic synthesis; OverHeaven Causal Unsealing',str(OUT/(name+'.ogg'))],check=True)
        records[name]={'duration':duration,'peak':round(peak*gain,5),'rate':RATE,'channels':2}
    print(json.dumps(records,indent=2))
if __name__=='__main__': main()
