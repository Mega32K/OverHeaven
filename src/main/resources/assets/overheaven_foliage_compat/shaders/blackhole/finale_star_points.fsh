#version 330 core
in vec2 Local;
flat in float Width,Trail,Flux;
flat in vec3 Tint;
out vec4 Color;
void main() {
    float across=exp(-Local.y*Local.y/(Width*Width+.25));
    float along=exp(-Local.x*Local.x/(Trail*Trail+Width*Width+.25));
    Color=vec4(Tint*across*along*Flux,0);
}
