#version 330 core
in vec2 texCoord;
out vec4 Color;
uniform sampler2D Scene;
uniform sampler2D Foreground;
void main() {
    vec4 host=texture(Scene,texCoord), front=texture(Foreground,texCoord);
    Color=vec4(pow(max(front.rgb,vec3(0.0)),vec3(2.2))+pow(clamp(front.a,0.0,1.0),2.2)*host.rgb,host.a);
}
