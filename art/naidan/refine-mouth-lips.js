(async () => {
    const tex=Texture.all[0],elements=[...Cube.all];
    Undo.initEdit({elements,textures:[tex],bitmap:true,outliner:true});
    const groups=Object.fromEntries(Group.all.map(g=>[g.name,g]));
    const lipGroups=['upper_lip','lower_lip','laugh_upper_lip','laugh_lower_lip','talk_upper_lip','talk_lower_lip'];
    for(const c of [...Cube.all])if(lipGroups.includes(c.parent?.name))c.remove();
    const canvas=document.createElement('canvas');canvas.width=canvas.height=64;
    const ctx=canvas.getContext('2d');ctx.drawImage(tex.img,0,0);
    ['#2c211a','#69503a','#19120f'].forEach((color,i)=>{
        ctx.fillStyle=color;ctx.fillRect(40+i*4,28,4,4);
    });
    tex.fromDataURL(canvas.toDataURL('image/png'));await tex.img.decode();
    function color(face,index){face.extend({texture:tex.uuid,uv:[41+index*4,29,42+index*4,30]});}
    function block(name,from,to,parent,highlight){
        const c=new Cube({name,from,to,autouv:0,box_uv:false,visibility:groups[parent].visibility});
        elements.push(c);c.init().addTo(groups[parent]);
        for(const face of Object.values(c.faces))color(face,0);
        if(highlight)color(c.faces.up,1);color(c.faces.down,2);return c;
    }
    const forms=[
        {id:'neutral',widths:[3.5,2.75,1.75],top:10.5,step:.25,upper:'upper_lip',lower:'lower_lip',topZ:-7.25,bottomZ:-7.3125},
        {id:'laugh',widths:[3,3.5,3.5,3.25,2.75,2],top:11.5,step:.5,upper:'laugh_upper_lip',lower:'laugh_lower_lip',topZ:-6.25,bottomZ:-7.25},
        {id:'talk',widths:[3,3.25,2.75,2,1.25],top:10.75,step:.25,upper:'talk_upper_lip',lower:'talk_lower_lip',topZ:-6.75,bottomZ:-7.25}
    ];
    for(const form of forms){
        const rows=form.widths.map((w,i)=>({l:-w/2,r:w/2,t:form.top-i*form.step,b:form.top-(i+1)*form.step}));
        const bottom=rows[rows.length-1].b,half=form.widths[0]/2,lowerHalf=form.widths[form.widths.length-1]/2;
        const skins=Cube.all.filter(c=>c.name.startsWith('neutral_skin_'));
        const backAt=(y,width)=>Math.max(...skins.filter(c=>y>=c.from[1]&&y<c.to[1]&&c.from[0]<width&&c.to[0]>-width).map(c=>c.from[2]))+.0625;
        const upperBack=backAt(form.top,half),lowerBack=backAt(bottom-.125,lowerHalf);
        block(form.id+'_upper_lip',[-half,form.top,form.topZ+.125],[half,form.top+.1875,upperBack],form.upper,true);
        block(form.id+'_lower_lip',[-lowerHalf,bottom-.125,form.bottomZ+.125],[lowerHalf,bottom,lowerBack],form.lower,false);
    }
    const faceAxes={west:[0,0],east:[0,1],down:[1,0],up:[1,1],north:[2,0],south:[2,1]};
    const tidyGroups=[...lipGroups,'laugh_mouth_shell','talk_mouth_shell','laugh_mouth','talk_mouth'];
    let hiddenFaces=0;
    for(const name of tidyGroups){
        const cubes=groups[name].children.filter(c=>c.type==='cube');
        for(const c of cubes)for(const [face,[axis,positive]]of Object.entries(faceAxes)){
            const position=(positive?c.to:c.from)[axis],others=[0,1,2].filter(a=>a!==axis);
            if(cubes.some(n=>n!==c&&Math.abs((positive?n.from:n.to)[axis]-position)<1e-6&&others.every(a=>n.from[a]<=c.from[a]&&n.to[a]>=c.to[a]))){
                c.faces[face].extend({texture:null});hiddenFaces++;
            }
        }
    }
    Undo.finishEdit('Clean mouth contours and remove protruding lip fragments');Canvas.updateAll();
    return{cubes:Cube.all.length,lipCubes:Cube.all.filter(c=>lipGroups.includes(c.parent?.name)).length,hiddenFaces};
})()
