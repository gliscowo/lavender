#version 150

uniform sampler2D InSampler;
uniform float Alpha;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    color.a *= Alpha;

    fragColor = color;
}
