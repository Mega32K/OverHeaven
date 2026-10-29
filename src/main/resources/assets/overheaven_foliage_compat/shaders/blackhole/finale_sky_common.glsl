uniform float OrbitAngle,OrbitSpeed;
uniform int StarsOnly;
uniform int ExposureEnabled,GalaxySamples;
uniform float ExposureSeconds;
uniform float ExposureAngles[65],ExposureTravel[65],GalaxyAngles[3];
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
// dissolve_2d.fsh reference: keep a texel until its stable noise is below Dissolve.
// Evaluate noise on the celestial direction instead of screen UV: no seam or camera-fixed holes.
float skyDissolveThreshold(vec3 direction) {
    float noise=cosmicNoise(direction*6.5)*.58
        +cosmicNoise(direction*17.+vec3(11.3,4.1,7.6))*.29
        +cosmicNoise(direction*43.+vec3(-3.1,7.4,2.3))*.13;
    float order=.04+.78*smoothstep(.17,.83,noise);
    // The last three islands also erode by threshold, rather than dimming their entire color.
    for(int island=0;island<3;island++) {
        vec3 center=normalize(island==0?vec3(.25,.24,-1):island==1?vec3(-.6,.45,-.8):vec3(.7,-.25,.8));
        float mask=1.-smoothstep(.07,.17,length(direction-center));
        float last=.835+float(island)*.072+(noise-.5)*.025;
        order=mix(order,max(order,last),mask);
    }
    return order;
}
float skyDissolveCoverage(float threshold,float footprint) {
    if(Extinction<=.001) return 1.;
    if(Extinction>=1.) return 0.;
    // Only the subpixel boundary is feathered. Surviving regions retain their original exposure.
    float width=clamp(footprint,.0001,.012);
    return 1.-smoothstep(threshold-width,threshold+width,Extinction);
}
float starSurvival(vec3 star) {
    if(Extinction<=.001) return 1.;
    if(Extinction>=1.) return 0.;
    return step(Extinction,skyDissolveThreshold(star));
}
vec3 cosmicRay(vec3 direction) { return cosmicRotate(direction,cosmicAxis(),-OrbitAngle); }
float cosmicTrail() {
    if(ExposureEnabled==0) return .0006+min(.011,abs(OrbitSpeed)*.008);
    return (.0006+min(.011,abs(OrbitSpeed)*min(.08,ExposureSeconds)))*smoothstep(0.,.025,ExposureSeconds);
}
