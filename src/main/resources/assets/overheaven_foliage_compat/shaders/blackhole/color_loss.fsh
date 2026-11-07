#version 330 core
uniform sampler2D Scene;
uniform vec3 RgbLoss;
uniform int Layered;
in vec2 texCoord;
out vec4 fragColor;
#include "color_loss_common.glsl"
void main() {
    vec4 source = texture(Scene, texCoord);
    float base = min(RgbLoss.r, min(RgbLoss.g, RgbLoss.b));
    float loss = base;
    if (Layered == 0 && base < 1.0) {
        vec3 residual = (RgbLoss - base) / (1.0 - base);
        loss += (1.0 - base) * ohSelectiveLoss(ohSrgbToLinear(source.rgb), residual);
    }
    fragColor = vec4(ohDesaturate(source.rgb, loss, vec3(0.2126, 0.7152, 0.0722)), source.a);
}
