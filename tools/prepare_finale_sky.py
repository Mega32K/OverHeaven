"""Offline-only NASA SVS 4851 derivatives. Requires numpy, scipy, OpenEXR, Pillow.
Sources are user-supplied download/cache files; this script never downloads at runtime.
"""
import argparse, pathlib, json, hashlib, gzip, struct
import numpy as np
import OpenEXR
from scipy import ndimage
from PIL import Image
p=argparse.ArgumentParser();p.add_argument('source');p.add_argument('output');p.add_argument('--preview');a=p.parse_args()
src=pathlib.Path(a.source);out=pathlib.Path(a.output);out.mkdir(parents=True,exist_ok=True)
def save(name,payload):
 (out/name).write_bytes(gzip.compress(payload,compresslevel=9,mtime=0))
def packed(rgb):
 maximum=rgb.max(axis=2); exponent=np.maximum(-16,np.floor(np.log2(np.maximum(maximum,1e-20)))).astype(np.int32)+16
 exponent=np.clip(exponent,0,31)
 mant=np.floor(rgb*np.exp2(24-exponent)[...,None]+.5).astype(np.uint32)
 overflow=mant.max(axis=2)>511;exponent+=overflow
 mant=np.floor(rgb*np.exp2(24-exponent)[...,None]+.5).clip(0,511).astype(np.uint32)
 return (mant[:,:,0]|(mant[:,:,1]<<9)|(mant[:,:,2]<<18)|(exponent.astype(np.uint32)<<27)).astype('<u4')
background=OpenEXR.File(str(src/'milkyway_2020_8k.exr')).channels()['RGB'].pixels.astype(np.float32)
background=np.nan_to_num(background,nan=0,posinf=0,neginf=0).clip(0,65000)
# Filter unresolved faint catalog points before downsampling, retaining the actual dust lanes.
background=ndimage.gaussian_filter(background,sigma=(.65,.65,0),mode=('reflect','wrap','reflect'))
background=background.reshape(2048,2,4096,2,3).mean(axis=(1,3))
save('milkyway.rgb9e5.gz',struct.pack('<4sIII',b'OHMW',1,4096,2048)+packed(background).tobytes())
if a.preview:
 preview=1-np.exp(-background*5);preview=np.power(preview,1/2.2)
 Image.fromarray((preview.clip(0,1)*255).astype(np.uint8)).resize((2048,1024)).save(a.preview)
del background
rgb=OpenEXR.File(str(src/'hiptyc_2020_8k.exr')).channels()['RGB'].pixels.astype(np.float32)
lum=np.einsum('ijk,k->ij',rgb,np.array([.2126,.7152,.0722],np.float32))
labels,count=ndimage.label(lum>.015)
flux=ndimage.sum(lum,labels,index=np.arange(1,count+1))
order=np.argsort(-flux,kind='stable')[:12000]+1
regions=ndimage.find_objects(labels)
records=[];directions=[]
for label in order:
 sy,sx=regions[label-1];weight=np.where(labels[sy,sx]==label,lum[sy,sx],0)
 total=float(weight.sum()); yy,xx=np.mgrid[sy,sx]
 theta=(yy+.5)*np.pi/4096;lon=(xx+.5)*2*np.pi/8192-np.pi
 direction=np.array([(np.cos(lon)*np.sin(theta)*weight).sum(),(np.cos(theta)*weight).sum(),(np.sin(lon)*np.sin(theta)*weight).sum()])/total
 direction/=np.linalg.norm(direction)
 # Merge seam-adjacent fragments and peaks belonging to the same bright source.
 if directions and max(np.asarray(directions)@direction)>np.cos(2*np.pi/8192*2): continue
 color=(rgb[sy,sx]*weight[...,None]).sum(axis=(0,1))/total;color/=max(float(color.max()),1e-8)
 records.append([*direction,*color,total]);directions.append(direction)
 if len(records)==4096: break
if len(records)!=4096: raise RuntimeError('Insufficient resolved stars '+str(len(records)))
records=np.asarray(records,np.float32)
rawflux=records[:,6].copy();records[:,6]=(.38+np.log1p(rawflux)/np.log1p(rawflux.max())*2.4).clip(.38,2.78)
save('bright-stars.bin.gz',struct.pack('<4sII',b'OHST',1,len(records))+records.astype('<f4').tobytes())
manifest={'source':'https://svs.gsfc.nasa.gov/4851/','credit':'NASA/Goddard Space Flight Center Scientific Visualization Studio; visualization: Ernie Wright (USRA). Gaia DR2: ESA/Gaia/DPAC.','usage':'https://svs.gsfc.nasa.gov/help/','projection':'Celestial equirectangular, north at row zero; longitude increases along texture U.','derivative':'4096x2048 linear RGB9_E5 with periodic filtering; 4096 resolved bright sources, ranked by integrated source luminance; normalized visual flux.','sources':[],'outputs':[],'software':{'OpenEXR':OpenEXR.__version__,'numpy':np.__version__}}
for name in ['milkyway_2020_8k.exr','hiptyc_2020_8k.exr']:
 b=(src/name).read_bytes();manifest['sources'].append({'file':name,'bytes':len(b),'sha256':hashlib.sha256(b).hexdigest()})
for name in ['milkyway.rgb9e5.gz','bright-stars.bin.gz']:
 b=(out/name).read_bytes();manifest['outputs'].append({'file':name,'bytes':len(b),'sha256':hashlib.sha256(b).hexdigest()})
(out/'SOURCE.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps(manifest,ensure_ascii=False,indent=2))
