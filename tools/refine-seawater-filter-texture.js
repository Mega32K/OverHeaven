(async () => {
    const density = 8;
    const tex = Texture.all.find(t => t.name === 'seawater_filter.png');
    const faces = [];
    for (const cube of Cube.all) {
        const d = cube.to.map((v, i) => v - cube.from[i]);
        for (const face of Object.keys(cube.faces)) {
            const width = face === 'east' || face === 'west' ? d[2] : d[0];
            const height = face === 'up' || face === 'down' ? d[2] : d[1];
            faces.push({cube, face, w: Math.max(1, Math.round(width * density)), h: Math.max(1, Math.round(height * density))});
        }
    }
    faces.sort((a, b) => b.h - a.h || b.w - a.w);
    const size = 1024;
    let x = 2, y = 2, row = 0;
    for (const f of faces) {
        if (x + f.w + 2 > size) { x = 2; y += row + 4; row = 0; }
        f.x = x; f.y = y;
        x += f.w + 4;
        row = Math.max(row, f.h);
    }
    const height = 2 ** Math.ceil(Math.log2(y + row + 2));
    const atlas = document.createElement('canvas');
    atlas.width = size; atlas.height = height;
    const ctx = atlas.getContext('2d');
    ctx.imageSmoothingEnabled = false;
    ctx.fillStyle = '#25333e'; ctx.fillRect(0, 0, size, height);
    let seed = 3999;
    const random = () => ((seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0) / 4294967296);
    const rgb = c => 'rgb(' + c.map(v => Math.max(0, Math.min(255, Math.round(v)))).join(',') + ')';
    const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
    const recessFaces = {top: 'down', bottom: 'up', front: 'south', back: 'north'};
    const topInside = {top_port_north: 'south', top_port_south: 'north', top_port_west: 'east', top_port_east: 'west'};
    function material(name, face) {
        if (name.startsWith('pipe_inner') || (/^pipe_(top|bottom|front|back)_/.test(name) && face === recessFaces[name.split('_')[1]])) return 'pipe_inside';
        if (name.startsWith('pipe_') || name === 'top_port_water' || name === 'top_port_floor') return 'water';
        if (/^(cartridge_glint|side_glint)/.test(name)) return 'glint';
        if (/^(cartridge_collar|cartridge_shadow)/.test(name)) return 'dark';
        if (/^(cartridge_|side_filter)/.test(name)) return 'glass';
        if (topInside[name] === face) return 'dark';
        if (/copper|flange|top_neck$|switch_glint/.test(name)) return 'copper';
        if (/vent_well|base_grille$/.test(name)) return 'vent';
        if (/bolt_/.test(name)) return 'bolt';
        if (/rim_|corner_cap|bezel|control_panel|top_port_(north|south|west|east)/.test(name)) return 'steel';
        if (/corner_guard|foot_|inner_shadow|switch_socket|top_neck_step/.test(name)) return 'dark';
        return 'metal';
    }
    const bases = {metal:[56,66,78], dark:[29,39,50], steel:[135,154,169], copper:[184,104,57], bolt:[179,196,206], vent:[10,19,27]};
    for (const f of faces) {
        const tile = document.createElement('canvas'); tile.width=f.w; tile.height=f.h;
        const g=tile.getContext('2d'), kind=material(f.cube.name,f.face);
        const shiny=['steel','copper','bolt'].includes(kind);
        for (let v=0;v<f.h;v++) for(let u=0;u<f.w;u++) {
            const sx=(u+.5)/f.w, sy=(v+.5)/f.h;
            const n=(random()-.5)*3;
            let color;
            if (kind==='glass') {
                const curvature=Math.pow(Math.sin(sx*Math.PI),.65);
                color=mix([9,78,119],[23,164,205],Math.round(curvature*12)/12);
                if (sx>.2 && sx<.32) color=mix(color,[150,225,239],.55);
                if (sx>.34 && sx<.41) color=mix(color,[121,213,234],.25);
                if (sy<.12 || sy>.94) color=mix(color,[7,61,98],.45);
                if (sy>.16 && sy<.19) color=mix(color,[132,224,241],.55);
                if (sy>.21 && sy<.24 && sx<.7) color=mix(color,[96,205,228],.22);
            } else if (kind==='glint') {
                color=mix([56,178,215],[160,232,244],.3+.25*Math.sin(sy*Math.PI));
            } else if (kind==='pipe_inside') {
                color=mix([9,53,91],[20,106,150],Math.round(sx*6)/6);
                if (sy<.13) color=mix(color,[37,143,184],.3);
            } else if (kind==='water') {
                const band=Math.round((.5+.5*Math.sin(sx*3.14))*6)/6;
                color=mix([13,111,164],[48,188,224],band*.7);
                if (sy<.15) color=mix(color,[114,218,240],.48);
                if (sy>.86) color=mix(color,[12,79,127],.35);
            } else {
                const base=bases[kind];
                const grain=(Math.floor(u/4)*7+Math.floor(v/3)*13)%11-5;
                const shine=shiny ? Math.round((.5-sy)*12)+((v%7===0)?3:0) : 0;
                color=base.map(c=>c+grain*.55+shine);
            }
            g.fillStyle=rgb(color.map(c=>c+n));g.fillRect(u,v,1,1);
        }
        if (!['pipe_inside','glass','glint','vent'].includes(kind) && f.w>=4 && f.h>=4) {
            const light=kind==='copper'?'#e5ac71':kind==='water'?'#63d4ec':kind==='dark'?'#465563':kind==='metal'?'#657584':'#cedae0';
            const shade=kind==='copper'?'#78432a':kind==='water'?'#105d8e':kind==='steel'||kind==='bolt'?'#647989':'#1c2934';
            g.fillStyle=light;g.fillRect(1,1,f.w-2,1);g.fillRect(1,1,1,f.h-2);
            g.fillStyle=shade;g.fillRect(0,f.h-2,f.w,2);g.fillRect(f.w-2,0,2,f.h);
            g.fillStyle=rgb(mix(bases[kind]||[30,140,185],[28,37,46],.35));
            g.fillRect(0,0,1,1);g.fillRect(f.w-1,0,1,1);
        }
        if (['metal','steel','copper'].includes(kind) && f.w>12 && f.h>10) {
            g.fillStyle=kind==='copper'?'rgba(255,195,121,.24)':'rgba(205,225,235,.16)';
            for(let i=0;i<Math.floor(f.w*f.h/220);i++) {
                const a=3+Math.floor(random()*(f.w-8)), b=3+Math.floor(random()*(f.h-7));
                g.fillRect(a,b,2+Math.floor(random()*4),1);
            }
        }
        if(kind==='bolt' && f.w>=5 && f.h>=5) {
            g.fillStyle='#5a6b78';g.fillRect(2,Math.floor(f.h/2),f.w-4,1);
        }
        ctx.drawImage(tile,f.x,f.y);
        ctx.drawImage(tile,0,0,f.w,1,f.x,f.y-1,f.w,1);
        ctx.drawImage(tile,0,f.h-1,f.w,1,f.x,f.y+f.h,f.w,1);
        ctx.drawImage(tile,0,0,1,f.h,f.x-1,f.y,1,f.h);
        ctx.drawImage(tile,f.w-1,0,1,f.h,f.x+f.w,f.y,1,f.h);
    }
    Undo.initEdit({elements:Cube.all,textures:[tex],bitmap:true,uv_mode:true});
    tex.fromDataURL(atlas.toDataURL('image/png'));
    await tex.img.decode();
    Project.texture_width=size;Project.texture_height=height;
    for(const f of faces) {
        f.cube.autouv=0;
        f.cube.faces[f.face].extend({uv:[f.x,f.y,f.x+f.w,f.y+f.h],texture:tex.uuid});
    }
    Undo.finishEdit('Refine metal, glass and blue pipe interiors');
    Canvas.updateAll();
    return {width:size,height,faces:faces.length,data:tex.getDataURL()};
})()
