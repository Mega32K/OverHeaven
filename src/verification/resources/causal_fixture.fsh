#version 330 core
in vec2 texCoord;
layout(location=0) out vec4 Color;
layout(location=1) out float Depth;
uniform mat4 VP,InverseVP;
uniform vec3 Eye;
uniform int Mode,Layer,Night;
void main(){
    vec4 point=InverseVP*vec4(texCoord*2.-1.,.98,1.);vec3 ray=normalize(point.xyz/point.w-Eye);
    float clouds=sin(ray.x*17.+ray.z*8.)*sin(ray.y*32.-ray.z*13.);
    vec3 sky=mix(vec3(.30,.47,.66),vec3(.72,.80,.88),pow(max(0.,1.-abs(ray.y)),2.));
    sky+=pow(max(clouds,0.),3.)*.21;
    float t=1000000.;vec3 color=sky;
    if(ray.y<-.01){float ground=(-5.-Eye.y)/ray.y;if(ground>0.){t=ground;vec3 q=Eye+ray*t;float line=step(.92,fract(q.x*.13))+step(.92,fract(q.z*.13));color=mix(vec3(.21,.25,.25),vec3(.50,.46,.36),min(1.,line));color=mix(color,sky,1.-exp(-t*.0015));}}
    if(abs(ray.z)>.00001){float wall=(-100.-Eye.z)/ray.z;vec3 q=Eye+ray*wall;
        float ridge=11.+6.*sin(q.x*.045)+4.*sin(q.x*.11);
        if(wall>0.&&wall<t&&q.y<ridge){t=wall;color=vec3(.17,.23,.30)+.05*sin(q.x*.7+q.y*.23);}
        float facade=(-62.-Eye.z)/ray.z;q=Eye+ray*facade;
        if(facade>0.&&facade<t&&abs(q.x)>12.&&abs(q.x)<36.&&q.y>-5.&&q.y<22.-floor(abs(q.x)/7.)*1.8){t=facade;float window=step(.22,fract(q.x*.45))*step(.20,fract(q.y*.52));color=mix(vec3(.20,.17,.14),vec3(.53,.56,.48),window);}
    }
    if(Mode==1 || Mode==2&&texCoord.x<.5 || Mode==4&&length((texCoord-vec2(.36,.51))*vec2(1.,1.6))<.16 || Mode==5&&texCoord.x<.5&&sin(texCoord.x*210.)*sin(texCoord.y*170.)>.2){t=Mode==1?1.5:8.;color=Mode==4?vec3(.07,1.7,.05):vec3(.16,.23,.29)+.05*step(.5,fract(texCoord.x*52.));}
    if(Night!=0)color*=.16;
    float depth=1.;if(t<999999.){vec4 clip=VP*vec4(Eye+ray*t,1.);depth=clip.z/clip.w*.5+.5;}
    bool glass=(Mode==3||Mode==6)&&texCoord.x<.64;
    vec3 transmission=Mode==6?vec3(.55,.73,.86):vec3(.72,.83,.93),residual=Mode==6?vec3(.026,.075,.10):vec3(.045,.055,.07);
    if(Layer==1){Color=vec4(glass?transmission:vec3(1),1);Depth=1.;if(glass){vec4 p=VP*vec4(Eye+ray*8.,1);Depth=p.z/p.w*.5+.5;}}
    else{Color=vec4(Layer==2&&glass?residual+transmission*color:color,1);Depth=depth;}
}
