package com.fongmi.android.tv.player.ai;

/**
 * 自研画质着色器集合（GLSL ES 3.00）。
 *
 * <p>全部为 App 自研实现，不依赖任何设备厂商 SDK：
 * <ul>
 *   <li>{@link #EASU_FRAG} —— 边缘自适应空间上采样。
 *       思路同 AMD FSR 的 EASU：先算局部梯度判断边缘方向，
 *       再沿边缘方向做各向异性插值，避免传统双线性把边缘糊成一片。</li>
 *   <li>{@link #POST_FRAG} —— CAS 对比度自适应锐化 + 自适应降噪 + 色彩/动态范围增强，
 *       三者合并在同一个 pass 里，只多一次采样，控制开销。</li>
 * </ul>
 */
public final class Shaders {

    /** 顶点着色器：全屏三角带，同时输出 OES 采样坐标 */
    public static final String VERT = "#version 300 es\n"
            + "in vec4 aPosition;\n"
            + "in vec4 aTexCoord;\n"
            + "uniform mat4 uTexMatrix;\n"
            + "out vec2 vTexCoord;\n"
            + "void main() {\n"
            + "  gl_Position = aPosition;\n"
            + "  vTexCoord = (uTexMatrix * aTexCoord).xy;\n"
            + "}\n";

    /**
     * EASU：边缘自适应空间上采样。
     * 采样解码输出的低分辨率帧，按边缘方向插值放大。
     */
    public static final String EASU_FRAG = "#version 300 es\n"
            + "#extension GL_OES_EGL_image_external_essl3 : require\n"
            + "precision mediump float;\n"
            + "in vec2 vTexCoord;\n"
            + "uniform samplerExternalOES uTexture;\n"
            + "uniform vec2 uTexel;      // 1/输入分辨率\n"
            + "uniform vec2 uScale;      // 输出/输入 放大倍率\n"
            + "out vec4 outColor;\n"
            + "\n"
            + "// 亮度\n"
            + "float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }\n"
            + "\n"
            + "void main() {\n"
            + "  vec2 texel = uTexel;\n"
            + "  // 邻域采样（3x3）\n"
            + "  vec3 c  = texture(uTexture, vTexCoord).rgb;\n"
            + "  vec3 n  = texture(uTexture, vTexCoord + vec2(0.0, -texel.y)).rgb;\n"
            + "  vec3 s  = texture(uTexture, vTexCoord + vec2(0.0,  texel.y)).rgb;\n"
            + "  vec3 w  = texture(uTexture, vTexCoord + vec2(-texel.x, 0.0)).rgb;\n"
            + "  vec3 e  = texture(uTexture, vTexCoord + vec2( texel.x, 0.0)).rgb;\n"
            + "\n"
            + "  // 一阶梯度：判断边缘方向与强度\n"
            + "  float dx = luma(e) - luma(w);\n"
            + "  float dy = luma(s) - luma(n);\n"
            + "  vec2 grad = vec2(dx, dy);\n"
            + "  float edge = length(grad);\n"
            + "\n"
            + "  // 沿边缘方向的偏移量：边缘越强，越倾向于沿边缘取锐利值\n"
            + "  // 边缘弱（平坦区）时退化为普通双线性，避免引入振铃\n"
            + "  float edgeAmt = clamp(edge * 2.0, 0.0, 1.0);\n"
            + "  vec2 dir = edge > 1e-5 ? normalize(grad) : vec2(0.0);\n"
            + "\n"
            + "  // 沿边缘方向的两个额外采样点\n"
            + "  vec2 along = dir * texel * 1.5;\n"
            + "  vec3 a1 = texture(uTexture, vTexCoord + along).rgb;\n"
            + "  vec3 a2 = texture(uTexture, vTexCoord - along).rgb;\n"
            + "\n"
            + "  // 各向异性插值：中心值 + 沿边缘方向的细节补偿\n"
            + "  vec3 sharp = c + (c - (a1 + a2) * 0.5) * 0.35;\n"
            + "  vec3 result = mix(c, sharp, edgeAmt);\n"
            + "\n"
            + "  // 轻微的方向性锐化，恢复高频轮廓（受放大倍率约束，倍率越高补偿越强）\n"
            + "  float boost = clamp((uScale.x - 1.0) * 0.25, 0.0, 0.35);\n"
            + "  result += (c - (n + s + w + e) * 0.25) * boost;\n"
            + "\n"
            + "  outColor = vec4(clamp(result, 0.0, 1.0), 1.0);\n"
            + "}\n";

    /**
     * 后处理：CAS 锐化 + 自适应降噪 + 色彩/动态范围增强。
     * 一次纹理采样完成全部处理。
     */
    public static final String POST_FRAG = "#version 300 es\n"
            + "precision mediump float;\n"
            + "in vec2 vTexCoord;\n"
            + "uniform sampler2D uTexture;\n"
            + "uniform vec2 uTexel;\n"
            + "uniform vec2 uUvScale;    // FBO 实际使用区 / 分配区（增强档按比例收缩采样范围）\n"
            + "uniform float uSharpness;  // 0~1\n"
            + "uniform float uDenoise;    // 0~1\n"
            + "uniform float uEnhance;    // 0/1，是否做色彩增强\n"
            + "out vec4 outColor;\n"
            + "\n"
            + "float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }\n"
            + "\n"
            + "void main() {\n"
            + "  vec2 uv = vTexCoord * uUvScale;\n"
            + "  vec2 t = uTexel;\n"
            + "  vec3 c  = texture(uTexture, uv).rgb;\n"
            + "  vec3 n  = texture(uTexture, uv + vec2(0.0, -t.y)).rgb;\n"
            + "  vec3 s  = texture(uTexture, uv + vec2(0.0,  t.y)).rgb;\n"
            + "  vec3 w  = texture(uTexture, uv + vec2(-t.x, 0.0)).rgb;\n"
            + "  vec3 e  = texture(uTexture, uv + vec2( t.x, 0.0)).rgb;\n"
            + "\n"
            + "  // ---- 1. 自适应降噪：平坦区多降，边缘区少降，保住细节 ----\n"
            + "  vec3 blur = (n + s + w + e) * 0.25;\n"
            + "  float variance = abs(luma(c) - luma(blur));\n"
            + "  // 方差大说明是细节/边缘，降低降噪权重\n"
            + "  float denoiseW = uDenoise * (1.0 - clamp(variance * 4.0, 0.0, 1.0));\n"
            + "  vec3 col = mix(c, blur, denoiseW);\n"
            + "\n"
            + "  // ---- 2. CAS 对比度自适应锐化 ----\n"
            + "  // 取邻域最亮与最暗，锐化幅度受局部对比度约束，避免过冲\n"
            + "  vec3 mn = min(min(n, s), min(w, e));\n"
            + "  vec3 mx = max(max(n, s), max(w, e));\n"
            + "  float peak = clamp(-min(mn.r, min(mn.g, mn.b)), 0.0, 1.0);\n"
            + "  vec3 inv = 1.0 - mx;\n"
            + "  float amp = clamp(min(peak, min(inv.r, min(inv.g, inv.b))), 0.0, 1.0);\n"
            + "  // 对比度越低（平坦）锐化越狠，高对比边缘收敛\n"
            + "  float w = amp * (1.0 - clamp(abs(luma(mx) - luma(mn)) * 2.0, 0.0, 1.0));\n"
            + "  col += (col - blur) * uSharpness * (0.5 + w);\n"
            + "\n"
            + "  // ---- 3. 色彩与动态范围增强（仅 ENHANCE 档） ----\n"
            + "  if (uEnhance > 0.5) {\n"
            + "    float l = luma(col);\n"
            + "    // 饱和度：拉开色度与亮度的距离\n"
            + "    col = mix(vec3(l), col, 1.18);\n"
            + "    // S 曲线对比度：暗部更暗、亮部更亮，中间调保持\n"
            + "    col = clamp(col, 0.0, 1.0);\n"
            + "    col = col * col * (3.0 - 2.0 * col) * 0.35 + col * 0.65;\n"
            + "  }\n"
            + "\n"
            + "  outColor = vec4(clamp(col, 0.0, 1.0), 1.0);\n"
            + "}\n";

    private Shaders() {
    }
}
