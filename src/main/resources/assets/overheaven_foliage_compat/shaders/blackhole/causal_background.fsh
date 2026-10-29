#version 330 core
in vec2 texCoord;
layout(location=0) out vec4 SceneColor;
layout(location=1) out vec4 OpaqueColor;
uniform sampler2D Scene,Opaque;
uniform float IntroBackgroundGain;
void main() {
    vec4 scene=texture(Scene,texCoord),opaque=texture(Opaque,texCoord);
    // Only pre-subject background dimming; the impact frames are a late color treatment.
    SceneColor=vec4(scene.rgb*IntroBackgroundGain,scene.a);
    OpaqueColor=vec4(opaque.rgb*IntroBackgroundGain,opaque.a);
}
