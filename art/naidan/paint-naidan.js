(async () => {
    const body = [];
    const elements = [...Cube.all], tex = Texture.all[0];
    Undo.initEdit({elements, textures:[tex], bitmap:true, outliner:true, uv_mode:true});
    for (const c of [...Cube.all]) c.remove();
    const canvas = document.createElement('canvas'); canvas.width=canvas.height=64;
    const ctx=canvas.getContext('2d'); ctx.imageSmoothingEnabled=false;
    ctx.fillStyle='#efbf45';ctx.fillRect(0,0,64,64);
    const gold=['#efbe43','#f0c046','#f1c149','#efbf45','#f2c24a'];
    let seed=3999;
    const rand=()=>((seed=(Math.imul(seed,1664525)+1013904223)>>>0)/4294967296);
    for(let y=0;y<32;y++)for(let x=0;x<32;x++){
        ctx.fillStyle=gold[Math.floor(rand()*gold.length)];ctx.fillRect(x,y,1,1);
    }
    ctx.fillStyle='#f4c64f';
    for(let i=0;i<12;i++)ctx.fillRect(Math.floor(rand()*28)+2,Math.floor(rand()*21),2,1);
    ctx.fillStyle='#ebba41';
    for(let i=0;i<8;i++)ctx.fillRect(Math.floor(rand()*28)+2,24+Math.floor(rand()*8),2,1);
    const iris=['00111100','01222210','12233221','12333321','12333321','12233221','01222210','00111100'];
    const greens=['#586535','#65763e','#7e914c','#91a45b'];
    for(let y=0;y<8;y++)for(let x=0;x<8;x++){
        ctx.fillStyle=greens[+iris[y][x]];ctx.fillRect(32+x,y,1,1);
    }
    ['#111a12','#fff3cc','#321e15','#f5c958','#dca43b','#65763e'].forEach((color,i)=>{
        ctx.fillStyle=color;ctx.fillRect(40+(i%3)*4,Math.floor(i/3)*4,4,4);
    });
    tex.fromDataURL(canvas.toDataURL('image/png'));await tex.img.decode();
    tex.uv_width=tex.uv_height=64;Project.texture_width=Project.texture_height=64;Project.box_uv=false;
    const groups=Object.fromEntries(Group.all.map(g=>[g.name,g]));
    // A new rounded pear silhouette, with voxel-rounded cross sections.
    const radii=[3.5,5,6,6.5,7,7,7,7,6.5,6.5,6,5.5,5,4.5,3.5,2];
    for(let y=0;y<radii.length;y++){
        const r=radii[y], strips=[];
        for(let x=-r;x<r;x++){
            const z=Math.max(.5,Math.round(r*1.05*Math.sqrt(1-((x+.5)/r)**2)*2)/2);
            const last=strips[strips.length-1];
            if(last&&last.z===z)last.to=x+1;else strips.push({from:x,to:x+1,z});
        }
        strips.forEach((s,i)=>{
            const c=new Cube({name:'soft_body_'+y+'_'+i,from:[s.from,y,-s.z],to:[s.to,y+1,s.z],autouv:0,box_uv:false});
            elements.push(c);body.push(c);c.init().addTo(groups.body);
        });
    }
    for(const c of body){
        c.autouv=0;c.box_uv=false;
        for(const [side,f]of Object.entries(c.faces)){
            const axis=side==='east'||side==='west'?2:0;
            const uv=side==='up'||side==='down'
                ?[(c.from[0]+8)*2,(c.from[2]+8)*2,(c.to[0]+8)*2,(c.to[2]+8)*2]
                :[(c.from[axis]+8)*2,(16-c.to[1])*2,(c.to[axis]+8)*2,(16-c.from[1])*2];
            f.extend({texture:tex.uuid,uv});
        }
    }
    function cube(name,from,to,parent,color,front){
        const c=new Cube({name,from,to,autouv:0,box_uv:false});elements.push(c);c.init().addTo(groups[parent]);
        c.box_uv=false;
        const u=40+(color%3)*4,v=Math.floor(color/3)*4;
        for(const f of Object.values(c.faces))f.extend({texture:tex.uuid,uv:[u+1,v+1,u+2,v+2]});
        if(front)c.faces.north.extend({uv:front});return c;
    }
    for(const[name,x]of[['eye_left',2.5],['eye_right',-2.5]]){
        groups[name].origin=[x,12,-5.5];groups[name+'_pupil'].origin=[x,12,-6.25];
        const widths=[4,6,8,8,8,8,6,4];
        for(let row=0;row<8;row++){
            const w=widths[row],inset=(8-w)/2,y=13.25-row*.3125;
            for(let col=inset;col<8-inset;col++){
                const dx=(col-3.5)/4,dy=(row-3.5)/4;
                const roundness=Math.sqrt(Math.max(0,1-dx*dx-dy*dy));
                const z=-5.5-Math.round(.75*roundness*8)/8;
                const back=-5.5+Math.round(1.5*roundness*8)/8;
                const left=x+(col-4)*.3125;
                cube(name+'_orb_'+row+'_'+col,[left,y-.3125,z],[left+.3125,y,back],name,5,[32+col,row,33+col,row+1]);
                const pupil=(row>=2&&row<=5&&col>=3&&col<=4)||(row>=3&&row<=4&&col>=2&&col<=5);
                if(pupil){
                    cube(name+'_pupil_'+row+'_'+col,[left,y-.3125,z-.0625],[left+.3125,y,z],name+'_pupil',0);
                    if(row===2&&col===3)cube(name+'_shine',[left,y-.3125,z-.09375],[left+.3125,y,z-.0625],name+'_pupil',1);
                }
            }
        }
    }
    groups.face.origin=[0,11.5,-5];
    groups.mouth.origin=[0,10.125,-6.5];groups.upper_lip.origin=[0,10.5,-6.5];groups.lower_lip.origin=[0,9.75,-6.5];
    const widths=[3.5,2.75,1.75];
    for(let i=0;i<widths.length;i++){
        const w=widths[i],y=10.5-i*.25;
        cube('happy_mouth_row_'+i,[-w/2,y-.25,-7.0625],[w/2,y,-6.375],'mouth',2);
    }
    cube('upper_lip_center',[-1.25,10.5,-7.09375],[1.25,10.625,-6.375],'upper_lip',3);
    cube('lower_lip_center',[-.875,9.625,-7.09375],[.875,9.75,-6.375],'lower_lip',3);
    cube('lower_lip_left',[-1.375,9.875,-7.09375],[-.875,10,-6.375],'lower_lip',4);
    cube('lower_lip_right',[.875,9.875,-7.09375],[1.375,10,-6.375],'lower_lip',4);
    Project.geckolib_model_type='ENTITY';Project.model_identifier='naidan';
    Undo.finishEdit('Add fuller body depth and reposition facial bones');Canvas.updateAll();
    return{format:Format.id,cubes:Cube.all.length,meshes:Mesh.all.length,textureSize:[64,64]};
})()
