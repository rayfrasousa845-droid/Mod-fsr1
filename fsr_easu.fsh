#version 120
// FSR 1.0 EASU - port GLSL 120 do algoritmo AMD FidelityFX Super Resolution 1.0 (MIT License).
// Amostragem com GL_NEAREST (sem texelFetch/gather no GLSL 120).
uniform sampler2D uTex;
uniform vec2 uInputSize;   // tamanho em texels da textura de entrada (render interno)

vec3 fetchPx(vec2 fp, float dx, float dy) {
    return texture2D(uTex, (fp + vec2(dx, dy) + 0.5) / uInputSize).rgb;
}
float lumaOf(vec3 c) { return c.b * 0.5 + (c.r * 0.5 + c.g); }

void easuSet(inout vec2 dir, inout float len, float w,
             float lA, float lB, float lC, float lD, float lE) {
    float lenX = max(abs(lD - lC), abs(lC - lB));
    float dirX = lD - lB;
    dir.x += dirX * w;
    lenX = clamp(abs(dirX) / max(lenX, 1e-6), 0.0, 1.0);
    lenX *= lenX;
    len += lenX * w;
    float lenY = max(abs(lE - lC), abs(lC - lA));
    float dirY = lE - lA;
    dir.y += dirY * w;
    lenY = clamp(abs(dirY) / max(lenY, 1e-6), 0.0, 1.0);
    lenY *= lenY;
    len += lenY * w;
}

void easuTap(inout vec3 aC, inout float aW, vec2 off, vec2 dir, vec2 len,
             float lob, float clp, vec3 c) {
    vec2 v;
    v.x = off.x * dir.x + off.y * dir.y;
    v.y = off.x * (-dir.y) + off.y * dir.x;
    v *= len;
    float d2 = min(v.x * v.x + v.y * v.y, clp);
    float wB = 0.4 * d2 - 1.0;
    float wA = lob * d2 - 1.0;
    wB *= wB;
    wA *= wA;
    wB = 1.5625 * wB - 0.5625;
    float w = wB * wA;
    aC += c * w;
    aW += w;
}

void main() {
    vec2 pp = gl_TexCoord[0].xy * uInputSize - 0.5;
    vec2 fp = floor(pp);
    pp -= fp;

    //      b c
    //    e f g h
    //    i j k l
    //      n o
    vec3 b = fetchPx(fp, 0.0, -1.0);
    vec3 c = fetchPx(fp, 1.0, -1.0);
    vec3 e = fetchPx(fp, -1.0, 0.0);
    vec3 f = fetchPx(fp, 0.0, 0.0);
    vec3 g = fetchPx(fp, 1.0, 0.0);
    vec3 h = fetchPx(fp, 2.0, 0.0);
    vec3 i = fetchPx(fp, -1.0, 1.0);
    vec3 j = fetchPx(fp, 0.0, 1.0);
    vec3 k = fetchPx(fp, 1.0, 1.0);
    vec3 l = fetchPx(fp, 2.0, 1.0);
    vec3 n = fetchPx(fp, 0.0, 2.0);
    vec3 o = fetchPx(fp, 1.0, 2.0);

    float bL = lumaOf(b), cL = lumaOf(c), eL = lumaOf(e), fL = lumaOf(f);
    float gL = lumaOf(g), hL = lumaOf(h), iL = lumaOf(i), jL = lumaOf(j);
    float kL = lumaOf(k), lL = lumaOf(l), nL = lumaOf(n), oL = lumaOf(o);

    vec2 dir = vec2(0.0);
    float len = 0.0;
    easuSet(dir, len, (1.0 - pp.x) * (1.0 - pp.y), bL, eL, fL, gL, jL);
    easuSet(dir, len, pp.x * (1.0 - pp.y),         cL, fL, gL, hL, kL);
    easuSet(dir, len, (1.0 - pp.x) * pp.y,         fL, iL, jL, kL, nL);
    easuSet(dir, len, pp.x * pp.y,                 gL, jL, kL, lL, oL);

    float dirR = dir.x * dir.x + dir.y * dir.y;
    bool zro = dirR < (1.0 / 32768.0);
    dirR = inversesqrt(max(dirR, 1e-9));
    dirR = zro ? 1.0 : dirR;
    dir.x = zro ? 1.0 : dir.x;
    dir *= dirR;
    len = len * 0.5;
    len *= len;
    float stretch = (dir.x * dir.x + dir.y * dir.y) / max(abs(dir.x), abs(dir.y));
    vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 - 0.5 * len);
    float lob = 0.5 + ((1.0 / 4.0 - 0.04) - 0.5) * len;
    float clp = 1.0 / lob;

    vec3 min4 = min(min(f, g), min(j, k));
    vec3 max4 = max(max(f, g), max(j, k));

    vec3 aC = vec3(0.0);
    float aW = 0.0;
    easuTap(aC, aW, vec2( 0.0, -1.0) - pp, dir, len2, lob, clp, b);
    easuTap(aC, aW, vec2( 1.0, -1.0) - pp, dir, len2, lob, clp, c);
    easuTap(aC, aW, vec2(-1.0,  1.0) - pp, dir, len2, lob, clp, i);
    easuTap(aC, aW, vec2( 0.0,  1.0) - pp, dir, len2, lob, clp, j);
    easuTap(aC, aW, vec2( 0.0,  0.0) - pp, dir, len2, lob, clp, f);
    easuTap(aC, aW, vec2(-1.0,  0.0) - pp, dir, len2, lob, clp, e);
    easuTap(aC, aW, vec2( 1.0,  1.0) - pp, dir, len2, lob, clp, k);
    easuTap(aC, aW, vec2( 2.0,  1.0) - pp, dir, len2, lob, clp, l);
    easuTap(aC, aW, vec2( 2.0,  0.0) - pp, dir, len2, lob, clp, h);
    easuTap(aC, aW, vec2( 1.0,  0.0) - pp, dir, len2, lob, clp, g);
    easuTap(aC, aW, vec2( 1.0,  2.0) - pp, dir, len2, lob, clp, o);
    easuTap(aC, aW, vec2( 0.0,  2.0) - pp, dir, len2, lob, clp, n);

    vec3 pix = min(max4, max(min4, aC / aW));
    gl_FragColor = vec4(clamp(pix, 0.0, 1.0), 1.0);
}
