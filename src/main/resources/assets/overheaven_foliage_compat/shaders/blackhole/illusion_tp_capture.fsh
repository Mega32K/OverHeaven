#version 330 core
uniform sampler2D Source;
uniform sampler2D BeforeHand;
uniform sampler2D PreviousMask;
uniform int Mode;
uniform int HasMask;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float current = texelFetch(Source, pixel, 0).r;
    if (Mode == 0) {
        fragColor = vec4(current, 0.0, 0.0, 1.0);
        return;
    }
    float mask = HasMask != 0 ? texelFetch(PreviousMask, pixel, 0).r : 0.0;
    // Oculus: compare the exact depth before/after each flushed hand pass.
    // Vanilla: the depth buffer was cleared immediately before hand rendering.
    float written = Mode == 1 ? step(0.0000001, abs(current - texelFetch(BeforeHand, pixel, 0).r))
                             : (current < 1.0 ? 1.0 : 0.0);
    fragColor = vec4(max(mask, written), 0.0, 0.0, 1.0);
}
