// Stable identity, independent of view, time, quality and catalog draw count.
float dreamIdentity(vec3 direction) {
    vec3 p=fract(direction*vec3(123.34,345.45,456.21)+Seed*.013);
    p+=dot(p,p.yzx+34.345);return fract((p.x+p.y)*p.z);
}
vec3 dreamTint(float identity) {
    if(identity<.64) return mix(vec3(.65,.79,1.),vec3(.84,.9,1.),identity/.64);
    if(identity<.88) return mix(vec3(.57,.56,1.),vec3(.79,.66,1.),(identity-.64)/.24);
    if(identity<.96) return vec3(1.,.55,.83);
    return vec3(1.,.84,.63);
}
float dreamShutterIntegral(float t) {
    t=clamp(t,0.,1.);
    float q=clamp((t-.7)/.3,0.,1.);
    return (min(t,.7)+.3*(q-q*q*q+.5*q*q*q*q))/.85;
}
float dreamShutterWeight(float t) { return (1.-smoothstep(.7,1.,t))/.85; }
float dreamArcGain(float arc) { return 1.+23.*smoothstep(0.,radians(20.),arc); }
float dreamTailGate(float arc) { return smoothstep(radians(.04),radians(.35),arc); }
float dreamHalo(float distance,float radius) {
    float u=distance/max(radius,.00001);
    return exp(-4.*u*u)*(1.-smoothstep(.65,1.,u));
}
