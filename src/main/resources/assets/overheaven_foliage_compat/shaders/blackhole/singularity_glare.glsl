uniform int GlareEnabled;
uniform sampler2D GlareFront;
uniform sampler2D GlareBack;
uniform vec2 GlareCenter;
uniform vec2 GlareSize;
uniform float GlareDistance;
uniform float GlareArm;
uniform float GlareStrength;
uniform float GlarePulse;
float glareArmProfile(vec2 offset,float lengthPixels,float pixelScale) {
    float along=abs(offset.x)/lengthPixels;
    if(along>=1.) return 0.;
    float taper=pow(1.-along,1.45)*(1.-smoothstep(.86,1.,along));
    float widthPixels=max(.65,pixelScale*(.6+2.4*pow(1.-along,2.)));
    float narrow=exp(-offset.y*offset.y/(widthPixels*widthPixels+.25));
    float shoulder=exp(-offset.y*offset.y/(widthPixels*widthPixels*7.+.25));
    return taper*(narrow*.85+shoulder*.15);
}
vec3 singularityGlare(vec2 uv,float opaque,float surface,bool transparent) {
    if(GlareEnabled==0 || GlareDistance>=opaque) return vec3(0.);
    vec2 offset=gl_FragCoord.xy-GlareCenter*GlareSize;
    if(max(abs(offset.x),abs(offset.y))>=GlareArm) return vec3(0.);
    float pixelScale=min(GlareSize.x,GlareSize.y)/1080.;
    float crossLight=glareArmProfile(offset,GlareArm,pixelScale)+glareArmProfile(offset.yx,GlareArm,pixelScale);
    float radial=length(offset)/GlareArm;
    float halo=exp(-radial*radial*12.)*(1.-smoothstep(.5,.82,radial))*.3;
    vec3 front=texelFetch(GlareFront,ivec2(0),0).rgb;
    vec4 back=texelFetch(GlareBack,ivec2(0),0);
    bool behind=transparent && GlareDistance>=surface;
    if((LayerMode==1 && !behind) || (LayerMode==2 && behind)) return vec3(0.);
    vec3 visibility=front+(behind?vec3(back.a):back.rgb);
    if(behind) visibility*=clamp(texture(Transmission,uv).rgb,0.,1.);
    return vec3(1.,.985,.965)*visibility*(crossLight*3.+halo*1.8)*GlareStrength*(1.+.3*GlarePulse);
}
