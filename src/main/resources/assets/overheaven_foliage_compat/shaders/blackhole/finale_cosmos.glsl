uniform float OrbitAngle,OrbitSpeed;
uniform int StarsOnly;
vec3 cosmicAxis() { return normalize(vec3(.24,.91,-.34)); }
vec3 cosmicRotate(vec3 direction,vec3 axis,float angle) {
    return direction*cos(angle)+cross(axis,direction)*sin(angle)+axis*dot(axis,direction)*(1.-cos(angle));
}
float cosmicHash(vec3 position) {
    position=fract(position*.1031+Seed*.0017);
    position+=dot(position,position.yzx+33.33);
    return fract((position.x+position.y)*position.z);
}
float cosmicNoise(vec3 position) {
    vec3 cell=floor(position),weight=fract(position); weight=weight*weight*(3.-2.*weight);
    float lower=mix(mix(cosmicHash(cell),cosmicHash(cell+vec3(1,0,0)),weight.x),mix(cosmicHash(cell+vec3(0,1,0)),cosmicHash(cell+vec3(1,1,0)),weight.x),weight.y);
    float upper=mix(mix(cosmicHash(cell+vec3(0,0,1)),cosmicHash(cell+vec3(1,0,1)),weight.x),mix(cosmicHash(cell+vec3(0,1,1)),cosmicHash(cell+vec3(1,1,1)),weight.x),weight.y);
    return mix(lower,upper,weight.z);
}
float cosmicCloud(vec3 position) {
    float value=cosmicNoise(position)*.60+cosmicNoise(position*2.07+vec3(11.3,4.1,7.6))*.28;
    if(Detail>0) value+=cosmicNoise(position*4.13+vec3(-3.1,7.4,2.3))*.12;
    return value;
}
float starSurvival(vec3 star) {
    if(Extinction>=1.) return 0.;
    float order=cosmicHash(floor(star*9.));
    float remaining=1.-smoothstep(.08+order*.53,.23+order*.53,Extinction);
    for(int island=0;island<3;island++) {
        vec3 direction=normalize(island==0?vec3(.25,.24,-1):island==1?vec3(-.6,.45,-.8):vec3(.7,-.25,.8));
        float mask=1.-smoothstep(.07,.17,length(star-direction));
        float alive=1.-smoothstep(.77+float(island)*.072,.825+float(island)*.072,Extinction);
        remaining=max(remaining,mask*alive);
    }
    return remaining;
}
vec3 cosmicRay(vec3 direction) { return cosmicRotate(direction,cosmicAxis(),-OrbitAngle); }
float cosmicTrail() { return .0006+min(.011,abs(OrbitSpeed)*.008); }
vec3 cosmicScenery(vec3 direction) {
    if(StarsOnly!=0 || Phase>=6) return vec3(0);
    vec3 axis=cosmicAxis(),right=normalize(cross(axis,vec3(0,0,1))),forward=cross(right,axis);
    vec3 ray=cosmicRotate(direction,axis,-OrbitAngle*.16);
    vec3 local=vec3(dot(ray,right),dot(ray,axis),dot(ray,forward));
    float winding=Phase==3?mix(.2,1.1,smoothstep(.12,.95,Progress)):Phase==4?mix(1.1,3.2,smoothstep(0.,1.,Progress)):Phase>=5?3.2:.2;
    float warped=local.y+.19*sin(local.x*3.8+local.z*2.4+winding)+.065*sin(local.z*12.-local.x*4.+winding*1.3);
    float dust=cosmicCloud(local*5.2+vec3(winding*.21,Seed*.03,0));
    float fine=cosmicNoise(local*23.+dust*3.);
    float band=exp(-abs(warped)*8.)*smoothstep(.29,.63,dust);
    float hollow=1.-smoothstep(.34,.61,cosmicNoise(local*8.-vec3(2.1,5.7,7.)));
    float ash=exp(-abs(warped+.025)*32.)*hollow;
    vec3 cold=mix(vec3(.016,.10,.32),vec3(.035,.54,.72),dust);
    vec3 warm=mix(vec3(.34,.055,.012),vec3(1.1,.48,.13),dust);
    float gold=smoothstep(-.5,.5,local.x+.6*local.z);
    vec3 color=mix(cold,warm,gold)*band*(.28+.72*fine)*(1.-ash*.83);
    float lace=0.;
    for(int ribbon=0;ribbon<3;ribbon++) {
        float offset=float(ribbon)-1.;
        float ridge=warped+offset*.11+sin(local.x*8.+local.z*5.+offset*2.+winding)*.028;
        float width=max(.0019,length(dFdx(direction))+length(dFdy(direction)));
        float strands=exp(-abs(ridge)/width)*smoothstep(.32,.62,dust)*(.25+.75*pow(.5+.5*sin(local.z*34.+dust*7.+winding),2.));
        lace+=strands;
    }
    float fold=Phase==4?sin(Progress*3.14159265):0.;
    color+=mix(vec3(.13,.52,1.),vec3(1.,.68,.3),gold)*lace*(.12+fold*.32);
    if(Phase==4 && length(Center-Eye)>.00001) {
        // A whole-sky temporal fold travels away from the real source direction, even behind the viewer.
        float directionToSource=dot(direction,normalize(Center-Eye));
        for(int foldIndex=0;foldIndex<3;foldIndex++) {
            float start=foldIndex==0?.24:foldIndex==1?.58:.84;
            float age=(Progress-start)/.14;
            if(age>0. && age<1.) {
                float frontier=mix(1.,-1.,age);
                float ribbon=exp(-pow((directionToSource-frontier)/.055,2.))*sin(age*3.14159265);
                color+=mix(vec3(.11,.26,.75),vec3(.85,.34,.13),gold)*ribbon*(.06+band*.75)*(.45+fine*.55);
            }
        }
    }
    float shock=Phase==2?(1.-smoothstep(.84,1.,Progress))*.22:0.;
    color+=vec3(.13,.24,.36)*band*shock;
    float consumed=1.-smoothstep(.03,.77,Extinction);
    float front=1.-smoothstep(.05+dust*.43,.20+dust*.43,Extinction);
    float burning=exp(-pow((Extinction-(.08+dust*.5))/.045,2.))*(1.-smoothstep(.65,.8,Extinction));
    color=color*consumed*front+vec3(.16,.25,.32)*lace*burning*.13;
    return color/(vec3(1.)+color);
}
