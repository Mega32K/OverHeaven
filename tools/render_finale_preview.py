"""Assemble synthetic GPU output; does not run or control Minecraft."""
import pathlib,argparse
from PIL import Image,ImageDraw
p=argparse.ArgumentParser();p.add_argument('gpu');a=p.parse_args();root=pathlib.Path(a.gpu)
frames=[]
for index,file in enumerate(sorted(root.glob('preview-*.png'))):
 im=Image.open(file).convert('RGB').resize((960,540),Image.Resampling.LANCZOS)
 ImageDraw.Draw(im).text((14,14),f'SYNTHETIC GPU | {40.4+index/30:.2f}s | camera distance 18 blocks',fill='white')
 frames.append(im)
frames[0].save(root/'wave-crossing.webp',save_all=True,append_images=frames[1:],duration=[33,33,34]*(len(frames)//3),loop=0,quality=82,method=4)
sheet=Image.new('RGB',(1920,540));draw=ImageDraw.Draw(sheet)
for i,name in enumerate(['singularity-sky-before.png','singularity-sky-after.png']):
 im=Image.open(root/name).convert('RGB').resize((960,540),Image.Resampling.LANCZOS);sheet.paste(im,(i*960,0));draw.text((i*960+14,14),'BEFORE' if i==0 else 'AFTER - NASA sky + brighter cross',fill='white')
sheet.save(root/'before-after.png')
print('Frames:',len(frames),'WebP bytes:',(root/'wave-crossing.webp').stat().st_size)
