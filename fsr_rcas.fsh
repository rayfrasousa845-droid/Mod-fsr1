#version 120
// FSR 1.0 RCAS - port GLSL 120 do algoritmo AMD FidelityFX Super Resolution 1.0 (MIT License).
uniform sampler2D uTex;
uniform vec2 uInputSize;   // tamanho em texels (resolucao cheia)
uniform float uSharp;      // exp2(-stops); 0.0 = RCAS desligado

vec3 px(vec2 p, float dx, float dy) {
    return texture2D(uTex, (p + vec2(dx, dy) + 0.5) / uInputSize).rgb;
}

void main() {
    vec2 p = floor(gl_TexCoord[0].xy * uInputSize);
    vec3 b = px(p, 0.0, -1.0);
    vec3 d = px(p, -1.0, 0.0);
    vec3 e = px(p, 0.0, 0.0);
    vec3 f = px(p, 1.0, 0.0);
    vec3 h = px(p, 0.0, 1.0);

    vec3 mn4 = min(min(b, d), min(f, h));
    vec3 mx4 = max(max(b, d), max(f, h));

    vec2 peakC = vec2(1.0, -4.0);
    vec3 hitMin = min(mn4, e) / max(4.0 * mx4, vec3(1e-4));
    vec3 hitMax = (peakC.x - max(mx4, e)) / min(4.0 * mn4 + peakC.y, vec3(-1e-4));
    vec3 lobeRGB = max(-hitMin, hitMax);
    float lobe = max(-(0.25 - (1.0 / 16.0)),
                     min(max(max(lobeRGB.r, lobeRGB.g), lobeRGB.b), 0.0)) * uSharp;

    float rcpL = 1.0 / (4.0 * lobe + 1.0);
    vec3 pix = (lobe * (b + d + h + f) + e) * rcpL;
    gl_FragColor = vec4(clamp(pix, 0.0, 1.0), 1.0);
}
