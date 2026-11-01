(async () => {
    const tex=Texture.all[0],elements=[...Cube.all];
    Undo.initEdit({elements,textures:[tex],bitmap:true,outliner:true});
    const groups=Object.fromEntries(Group.all.map(g=>[g.name,g]));
    function group(name,parent,origin){
        if(!groups[name])groups[name]=new Group({name,origin:[...origin]}).init().addTo(groups[parent]);
        return groups[name];
    }
    group('face_talk','naidan_root',[0,11.5,-5]);
    for(const name of ['eye_left','eye_left_pupil','eye_right','eye_right_pupil']){
        group('talk_'+name,name.endsWith('_pupil')?'talk_'+name.replace('_pupil',''):'face_talk',groups[name].origin);
    }
    group('talk_mouth','face_talk',[0,10.125,-5.75]);
    group('talk_mouth_shell','face_talk',[0,10,-5]);
    group('talk_upper_lip','talk_mouth',[0,10.75,-6.25]);
    group('talk_lower_lip','talk_mouth',[0,9.5,-6.75]);
    group('talk_tongue','talk_mouth',[0,9.5,-5.75]);
    for(const c of [...Cube.all])if(c.name.startsWith('talk_'))c.remove();
    const canvas=document.createElement('canvas');canvas.width=canvas.height=64;
    const ctx=canvas.getContext('2d');ctx.drawImage(tex.img,0,0);
    const colors=['#291b15','#453023','#c59b6f','#ad7260','#e6b64b','#34231d'];
    colors.forEach((color,i)=>{ctx.fillStyle=color;ctx.fillRect(56+(i%2)*4,Math.floor(i/2)*4,4,4);});
    tex.fromDataURL(canvas.toDataURL('image/png'));await tex.img.decode();
    function swatch(face,color){
        const u=57+(color%2)*4,v=1+Math.floor(color/2)*4;
        face.extend({texture:tex.uuid,uv:[u,v,u+1,v+1]});
    }
    function box(name,from,to,parent,color){
        const c=new Cube({name,from,to,autouv:0,box_uv:false});
        elements.push(c);c.init().addTo(groups[parent]);
        for(const f of Object.values(c.faces))swatch(f,color);
        return c;
    }
    for(const name of ['eye_left','eye_left_pupil','eye_right','eye_right_pupil']){
        for(const source of groups[name].children.filter(c=>c.type==='cube')){
            const c=box('talk_'+source.name,[...source.from],[...source.to],'talk_'+name,0);
            c.origin=[...source.origin];c.rotation=[...source.rotation];
            for(const [side,f]of Object.entries(c.faces)){
                const old=source.faces[side];f.extend({texture:old.texture,uv:[...old.uv],rotation:old.rotation});
            }
        }
    }
    const skins=Cube.all.filter(c=>c.name.startsWith('neutral_skin_'));
    const widths=[3,3.25,2.75,2,1.25],back=-5.75;
    const rows=widths.map((w,i)=>({left:-w/2,right:w/2,bottom:10.5-i*.25,top:10.75-i*.25}));
    const inside=(x,y)=>rows.some(r=>x>r.left&&x<r.right&&y>r.bottom&&y<r.top);
    const cuts=(a,b,values)=>[a,...new Set(values.filter(v=>v>a&&v<b)),b].sort((a,b)=>a-b);
    function skinUV(c){
        for(const [side,f]of Object.entries(c.faces)){
            const axis=side==='east'||side==='west'?2:0;
            const uv=side==='up'||side==='down'
                ?[(c.from[0]+8)*2,(c.from[2]+8)*2,(c.to[0]+8)*2,(c.to[2]+8)*2]
                :[(c.from[axis]+8)*2,(16-c.to[1])*2,(c.to[axis]+8)*2,(16-c.from[1])*2];
            f.extend({texture:tex.uuid,uv});
        }
    }
    for(const s of skins){
        const xs=cuts(s.from[0],s.to[0],rows.flatMap(r=>[r.left,r.right]));
        const ys=cuts(s.from[1],s.to[1],rows.flatMap(r=>[r.bottom,r.top]));
        for(let i=0;i<xs.length-1;i++)for(let j=0;j<ys.length-1;j++){
            const x1=xs[i],x2=xs[i+1],y1=ys[j],y2=ys[j+1],x=(x1+x2)/2,y=(y1+y2)/2;
            if(inside(x,y))continue;
            const c=box('talk_shell_'+s.name+'_'+i+'_'+j,[x1,y1,s.from[2]],[x2,y2,s.to[2]],'talk_mouth_shell',4);
            skinUV(c);
            if(inside(x1-.001,y))swatch(c.faces.west,5);
            if(inside(x2+.001,y))swatch(c.faces.east,5);
            if(inside(x,y1-.001))swatch(c.faces.down,5);
            if(inside(x,y2+.001))swatch(c.faces.up,1);
        }
    }
    rows.forEach((r,i)=>box('talk_cavity_'+i,[r.left,r.bottom,back-.03125],[r.right,r.top,-4.96875],'talk_mouth',0));
    function rim(name,y1,y2,left,right,parent,color){
        const strips=skins.filter(s=>y1>=s.from[1]&&y1<s.to[1]&&s.from[0]<right&&s.to[0]>left);
        strips.forEach((s,i)=>box(name+'_'+i,[Math.max(left,s.from[0]),y1,s.from[2]-.03125],[Math.min(right,s.to[0]),y2,s.from[2]+.0625],parent,color));
    }
    rim('talk_upper_rim',10.75,10.875,-1.5,1.5,'talk_upper_lip',2);
    rim('talk_lower_rim',9.375,9.5,-.625,.625,'talk_lower_lip',4);
    const tongue=box('talk_tongue',[-.5,9.5,-6.125],[.5,9.6875,-5.625],'talk_tongue',3);
    swatch(tongue.faces.up,2);
    function show(node,value){node.visibility=value;if(node.children)node.children.forEach(c=>show(c,value));}
    show(groups.face,false);show(groups.face_laugh,false);show(groups.face_talk,true);
    Undo.finishEdit('Add open-eyed speaking expression with a small recessed mouth');Canvas.updateAll();
    return{format:Format.id,cubes:Cube.all.length,groups:Group.all.length};
})()
