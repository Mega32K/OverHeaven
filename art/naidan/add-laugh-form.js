(async () => {
    const tex=Texture.all[0], elements=[...Cube.all];
    Undo.initEdit({elements,textures:[tex],bitmap:true,outliner:true});
    const groups=Object.fromEntries(Group.all.map(g=>[g.name,g]));
    function group(name,parent,origin){
        if(!groups[name])groups[name]=new Group({name,origin}).init().addTo(groups[parent]);
        return groups[name];
    }
    group('face_laugh','naidan_root',[0,11,-5]);
    group('laugh_eye_left','face_laugh',[2.5,12.5,-4.5]);
    group('laugh_eye_right','face_laugh',[-2.5,12.5,-4.5]);
    group('laugh_mouth','face_laugh',[0,10,-6.5]);
    group('laugh_upper_lip','laugh_mouth',[0,11.5,-6]);
    group('laugh_lower_lip','laugh_mouth',[0,8.5,-7]);
    group('laugh_teeth','laugh_mouth',[0,11.25,-6]);
    group('laugh_tongue','laugh_mouth',[0,9,-7]);
    group('neutral_mouth_fill','face',[0,10,-5]);
    group('laugh_mouth_shell','face_laugh',[0,10,-5]);
    const body=Cube.all.filter(c=>c.name.startsWith('soft_body_'));
    const source=body.map(c=>{
        const skin=Cube.all.find(s=>s.name==='neutral_skin_'+c.name);
        return{cube:c,from:skin?[...skin.from]:[...c.from],to:[...c.to]};
    });
    for(const c of [...Cube.all])if(c.name.startsWith('laugh_'))c.remove();
    for(const c of [...Cube.all])if(c.name.startsWith('neutral_skin_'))c.remove();
    const canvas=document.createElement('canvas');canvas.width=canvas.height=64;
    const ctx=canvas.getContext('2d');ctx.drawImage(tex.img,0,0);
    const colors=['#24180f','#fff0cb','#e6b247','#98554b','#bd7960','#efbf45','#302017','#38251b'];
    colors.forEach((color,i)=>{ctx.fillStyle=color;ctx.fillRect(40+(i%3)*4,12+Math.floor(i/3)*4,4,4);});
    tex.fromDataURL(canvas.toDataURL('image/png'));await tex.img.decode();
    function swatch(face,color){
        const u=41+(color%3)*4,v=13+Math.floor(color/3)*4;
        face.extend({texture:tex.uuid,uv:[u,v,u+1,v+1]});
    }
    function box(name,from,to,parent,color){
        const c=new Cube({name,from,to,autouv:0,box_uv:false});
        elements.push(c);c.init().addTo(groups[parent]);
        for(const f of Object.values(c.faces))swatch(f,color);
        return c;
    }
    function cube(name,x1,y1,x2,y2,parent,color,offset=.0625){
        const eye=parent.startsWith('laugh_eye_'),z=eye?-5:-7;
        return box(name,[x1,y1,z-offset],[x2,y2,eye?-4.25:-5.75],parent,color);
    }
    for(const [name,x]of [['laugh_eye_left',2.5],['laugh_eye_right',-2.5]]){
        const heights=[12.125,12.3125,12.4375,12.4375,12.3125,12.125];
        heights.forEach((y,i)=>cube(name+'_'+i,x-.9375+i*.3125,y,x-.625+i*.3125,y+.25,name,0));
    }
    const widths=[3,3.5,3.5,3.25,2.75,2],back=-5;
    const rows=widths.map((w,i)=>({left:-w/2,right:w/2,bottom:11-i*.5,top:11.5-i*.5}));
    const inside=(x,y)=>rows.some(r=>x>r.left&&x<r.right&&y>r.bottom&&y<r.top);
    function skinUV(c){
        c.box_uv=false;c.autouv=0;
        for(const [side,f]of Object.entries(c.faces)){
            const axis=side==='east'||side==='west'?2:0;
            const uv=side==='up'||side==='down'
                ?[(c.from[0]+8)*2,(c.from[2]+8)*2,(c.to[0]+8)*2,(c.to[2]+8)*2]
                :[(c.from[axis]+8)*2,(16-c.to[1])*2,(c.to[axis]+8)*2,(16-c.from[1])*2];
            f.extend({texture:tex.uuid,uv});
        }
    }
    const cuts=(a,b,values)=>[a,...new Set(values.filter(v=>v>a&&v<b)),b].sort((a,b)=>a-b);
    const affected=source.filter(s=>s.from[0]<1.75&&s.to[0]>-1.75&&s.from[1]<11.5&&s.to[1]>8.5);
    for(const s of affected){
        s.cube.from[2]=back;skinUV(s.cube);
        const neutral=box('neutral_skin_'+s.cube.name,s.from,[s.to[0],s.to[1],back],'neutral_mouth_fill',5);
        skinUV(neutral);
        const xs=cuts(s.from[0],s.to[0],rows.flatMap(r=>[r.left,r.right]));
        const ys=cuts(s.from[1],s.to[1],rows.flatMap(r=>[r.bottom,r.top]));
        for(let i=0;i<xs.length-1;i++)for(let j=0;j<ys.length-1;j++){
            const x1=xs[i],x2=xs[i+1],y1=ys[j],y2=ys[j+1],x=(x1+x2)/2,y=(y1+y2)/2;
            if(inside(x,y))continue;
            const c=box('laugh_shell_'+s.cube.name+'_'+i+'_'+j,[x1,y1,s.from[2]],[x2,y2,back],'laugh_mouth_shell',5);
            skinUV(c);
            if(inside(x1-.001,y))swatch(c.faces.west,6);
            if(inside(x2+.001,y))swatch(c.faces.east,6);
            if(inside(x,y1-.001))swatch(c.faces.down,6);
            if(inside(x,y2+.001))swatch(c.faces.up,7);
        }
    }
    rows.forEach((r,i)=>box('laugh_cavity_back_'+i,[r.left,r.bottom,back-.03125],[r.right,r.top,back+.0625],'laugh_mouth',0));
    function rim(name,y1,y2,left,right,parent){
        const strips=source.filter(s=>y1>=s.from[1]&&y1<s.to[1]&&s.from[0]<right&&s.to[0]>left);
        strips.forEach((s,i)=>box(name+'_'+i,[Math.max(left,s.from[0]),y1,s.from[2]-.03125],[Math.min(right,s.to[0]),y2,s.from[2]+.0625],parent,2));
    }
    rim('laugh_upper_rim',11.5,11.625,-1.5,1.5,'laugh_upper_lip');
    rim('laugh_lower_rim',8.375,8.5,-1,1,'laugh_lower_lip');
    box('laugh_teeth_center',[-.5,11.125,-5.75],[.5,11.5625,-5.125],'laugh_teeth',1);
    box('laugh_teeth_left',[-1.125,11.125,-5.25],[-.5,11.5625,-4.75],'laugh_teeth',1);
    box('laugh_teeth_right',[.5,11.125,-5.25],[1.125,11.5625,-4.75],'laugh_teeth',1);
    const tongue=box('laugh_tongue_base',[-.75,8.5,-5.75],[.75,8.8125,-4.125],'laugh_tongue',3);
    swatch(tongue.faces.up,4);
    box('laugh_tongue_back',[-.5,8.8125,-5.125],[.5,9,-4.125],'laugh_tongue',4);
    groups.laugh_mouth.origin=[0,10,-4.5];
    groups.laugh_teeth.origin=[0,11.5,-5.25];
    groups.laugh_tongue.origin=[0,8.5,-4.25];
    function visibility(node,value){node.visibility=value;if(node.children)node.children.forEach(c=>visibility(c,value));}
    visibility(groups.face,false);visibility(groups.face_laugh,true);
    Undo.finishEdit('Inset laughing mouth into the body with interior walls and recessed teeth');Canvas.updateAll();
    return{format:Format.id,cubes:Cube.all.length,groups:Group.all.map(g=>g.name)};
})()
