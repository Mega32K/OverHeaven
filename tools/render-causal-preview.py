"""Package the independent 24 fps GPU fixture and an original score preview.
This is not a Minecraft capture. No game or instance files are opened.
"""
import array, math, subprocess, wave, argparse
from pathlib import Path
import imageio_ffmpeg
ROOT=Path(__file__).resolve().parents[1]
SOUNDS=ROOT/'src/main/resources/assets/overheaven_foliage_compat/sounds/finale'
RATE=32000

def smooth(a,b,t):
    p=max(0,min(1,(t-a)/(b-a)));return p*p*(3-2*p)
def stem(layer,p):
    vacuum=1-.97*smooth(.526,.535,p)*(1-smooth(.55,.56875,p))
    charge=smooth(.025,.4375,p)*(1-smooth(.56875,.825,p))
    gains=[.42*smooth(0,.08,p),.48*charge,.26*smooth(.175,.4375,p)*(1-smooth(.57,.825,p)),.08*smooth(0,.175,p)+.23*smooth(.28,.52,p)*(1-smooth(.56875,.825,p))]
    return vacuum*gains[layer]
def pitch(layer,p):
    return 1+.025*math.sin(p*19)*(1-smooth(.6,1,p)) if layer==0 else 1+.25*smooth(.175,.53,p)*(1-smooth(.57,.825,p))
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--audit',default='audit/portal-impact-reference-2026-10-29/gpu');options=parser.parse_args()
    out=ROOT/options.audit;encoder=imageio_ffmpeg.get_ffmpeg_exe();names=['hum','pressure','grain','tail','causal_charge','causal_fault','causal_release','causal_recoil'];decoded={}
    for name in names:
        result=subprocess.run([encoder,'-v','error','-i',str(SOUNDS/(name+'.ogg')),'-f','f32le','-ac','2','-ar',str(RATE),'pipe:1'],check=True,stdout=subprocess.PIPE)
        data=array.array('f');data.frombytes(result.stdout);decoded[name]=data
    cues=[('causal_charge',.16,.48,4.208),('causal_fault',1.4,.38,2.72),('causal_fault',2.12,.48,3.44),('causal_fault',2.84,.58,4.16),('causal_release',4.4,.85,5.78),('causal_recoil',4.55,.64,6.6)]
    pcm=array.array('h');positions=[0.,0.,0.,0.];peak=0
    for frame in range(RATE*8):
        t=frame/RATE;p=t/8;voice=[0.,0.]
        for layer,name in enumerate(names[:4]):
            data=decoded[name];at=int(positions[layer])%(len(data)//2);gain=stem(layer,p)
            for channel in range(2):voice[channel]+=data[at*2+channel]*gain
            positions[layer]+=pitch(layer,p)
        for name,start,gain,end in cues:
            at=round((t-start)*RATE);data=decoded[name]
            if 0<=at<len(data)//2 and t<end:
                fade=1-smooth(end-.1,end,t)
                for channel in range(2):voice[channel]+=data[at*2+channel]*gain*fade
        for value in voice:
            peak=max(peak,abs(value));pcm.append(round(max(-.98,min(.98,value*.84))*32767))
    with wave.open(str(out/'causal-score-preview.wav'),'wb') as wav:
        wav.setnchannels(2);wav.setsampwidth(2);wav.setframerate(RATE);wav.writeframes(pcm.tobytes())
    subprocess.run([encoder,'-v','error','-y','-framerate','24','-i',str(out/'frames/%04d.jpg'),'-i',str(out/'causal-score-preview.wav'),'-t','8','-c:v','libx264','-preset','medium','-crf','18','-pix_fmt','yuv420p','-c:a','aac','-b:a','192k','-movflags','+faststart','-metadata','comment=Independent synthetic GPU fixture; original audio. Not a Minecraft recording.',str(out/'causal-unsealing-8s.mp4')],check=True)
    print('Preview: 192 frames / 24 fps = 8 seconds, 960x540. Original audio pre-master peak:',round(peak,5))
if __name__=='__main__':main()
