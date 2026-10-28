// Only the faint singularity survives in the late cosmos; never an orbiting ring.
uniform float Radius,Glow;
uniform vec3 DiskRight,DiskNormal,DiskForward;
vec3 finaleRemnant(vec3 ray) {
    if(Phase==7) return vec3(0);
    float tail=Phase==6?(1.-smoothstep(.35,.625,Progress))*.006:mix(.016,.006,Extinction);
    vec3 origin=Eye-Center;
    float nearest=-dot(origin,ray); if(nearest<=0.) return vec3(0);
    vec3 at=origin+ray*nearest;
    float radius=max(Radius,.001),impact=length(at)/radius;
    if(impact>.5) return vec3(0);
    vec3 light=vec3(exp(-impact*impact/.0009));
    float discriminant=radius*radius*.04-dot(at,at);
    if(discriminant>0.) {
        vec3 normal=normalize(origin+ray*(nearest-sqrt(discriminant)));
        vec3 local=vec3(dot(normal,DiskRight),dot(normal,DiskNormal),dot(normal,DiskForward));
        float patches=smoothstep(-.3,.65,sin(dot(local,vec3(2.3,3.1,-1.7))+Time*.44)+.5*sin(local.y*4.-Time*.27));
        float film=.5+.13*sin(dot(local,vec3(1.2,-2.,3.))+.21*Time);
        vec3 tint=.5+.5*cos(film/vec3(.650,.510,.440)*6.2831853);
        light+=tint*patches*(.18+.8*pow(1.-abs(dot(normal,ray)),2.));
    }
    light+=vec3(.55,.73,1.)*exp(-impact*impact/.032)*min(Glow,2.)*.22;
    return light*tail;
}
