#version 150

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    color.a = min(1.0f, color.a * 1e8f);

    fragColor = color;
}
