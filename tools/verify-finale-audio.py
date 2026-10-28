"""Decode originals, validate catalog/positional format and periodic boundaries. No playback."""
import argparse, array, hashlib, json, math, struct, subprocess
from pathlib import Path
import imageio_ffmpeg
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--report',type=Path,default=root/'audit/finale-reimagined-2026-10-27/audio-verification.json')
args=parser.parse_args()
folder=root/'src/main/resources/assets/overheaven_foliage_compat/sounds/finale'
catalog=json.loads((folder.parents[1]/'sounds.json').read_text(encoding='utf-8'))
expected={'hum','pressure','grain','tail','ignite','pulse','crack','rupture','impact','reverse','time_break','fold'}
assert {p.stem for p in folder.glob('*.ogg')}==expected
results=[]
for file in sorted(folder.glob('*.ogg')):
    raw=file.read_bytes(); header=raw.index(b'\x01vorbis'); channels=raw[header+11]
    rate=struct.unpack_from('<I',raw,header+12)[0]
    assert rate==32000 and channels==(1 if file.stem in ('crack','rupture') else 2),(file,channels,rate)
    assert catalog['finale.'+file.stem]['sounds'][0]['name'].endswith('finale/'+file.stem)
    data=subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-v','error','-i',str(file),'-f','f32le','-ar','32000','-'],capture_output=True,check=True).stdout
    values=array.array('f');values.frombytes(data)
    assert all(math.isfinite(x) for x in values),file
    peak=max(abs(x) for x in values); assert 0<peak<.98,(file,peak)
    result={'name':file.name,'channels':channels,'seconds':len(values)/(32000*channels),'peak':peak,'sha256':hashlib.sha256(raw).hexdigest()}
    if file.stem in ('hum','pressure','grain','tail'):
        seam=max(abs(values[c]-values[-channels+c]) for c in range(channels))
        differences=sorted(abs(values[i]-values[i-channels]) for i in range(channels,len(values),17))
        ordinary=differences[int(len(differences)*.99)]
        assert seam<=ordinary*1.5+.004,(file,seam,ordinary)
        result.update(loop_seam_jump=seam,interior_99_percentile=ordinary)
    results.append(result)
args.report.parent.mkdir(parents=True,exist_ok=True)
args.report.write_text(json.dumps(results,indent=2),encoding='utf-8')
print('12 original OGG assets decoded; catalog, stereo/positional channels, headroom and loop boundaries passed:',args.report)
