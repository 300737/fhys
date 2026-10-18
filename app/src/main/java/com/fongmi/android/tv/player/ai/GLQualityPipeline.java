package com.fongmi.android.tv.player.ai;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;

/**
 * 软件自研画质 GL 渲染管线（运行在独立 GL 线程）。
 *
 * <p>这是 App 自己实现的画质引擎，不依赖任何设备的硬件 AI 能力：
 * 不使用 NVIDIA SHIELD 的 AI-Upscaling、不调用任何厂商私有 API，
 * 全部由 GLSL 着色器在 GPU 上完成，因此任意支持 OpenGL ES 3.0 的设备行为一致。
 *
 * <p>数据流向：
 * <pre>
 *   MediaCodec ──> SurfaceTexture(OES) ──> [Pass1 EASU 超分] ──> FBO
 *                                                                  │
 *   Surface(显示) <── eglSwapBuffers <── [Pass2 CAS/降噪/色彩] <────┘
 * </pre>
 *
 * <p>解码器输出给我们创建的 {@link #getDecoderSurface()}，画面不再直达显示层，
 * 而是先经两个着色器 pass 处理后再上屏。
 */
public final class GLQualityPipeline implements SurfaceTexture.OnFrameAvailableListener {

    /** 管线状态回调。初始化是异步的，调用方需等 {@link #onReady} 才能拿到解码输出面。 */
    public interface Callback {
        void onReady(@NonNull Surface decoderSurface);

        void onError();
    }

    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    // EGL14 未暴露 EGL_HEIGHT，这里直接用 EGL 规范定义的常量值
    private static final int EGL_WIDTH = 0x3057;
    private static final int EGL_HEIGHT = 0x3056;
    private static final int FLOAT_SIZE = 4;

    // 全屏三角带：位置(x,y) + 纹理坐标(s,t)
    private static final float[] VERTICES = {
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
    };

    private final Surface outputSurface;
    /** 超分缓冲尺寸，可大于屏幕（伪 4K 超采样） */
    private final int dstWidth;
    private final int dstHeight;
    /** 屏幕（EGL window）实际尺寸，最终上屏 pass 用它做视口 */
    private int outWidth;
    private int outHeight;
    private final Callback callback;

    private HandlerThread glThread;
    private Handler glHandler;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private EGLConfig eglConfig;
    /** 显示表面是否已挂载（解码器切换走之后才能挂载成功） */
    private volatile boolean outputAttached;
    private int attachRetries;

    private SurfaceTexture decoderTexture;
    private Surface decoderSurface;
    private int oesTexId;
    private int fboId;
    private int fboTexId;

    private int easuProgram;
    private int postProgram;
    private int vboId;

    private final float[] texMatrix = new float[16];
    private volatile boolean released;
    private volatile boolean frameAvailable;
    /**
     * 等待输出挂载期间已消费但尚未渲染的帧。
     * 挂载成功后立即补渲染这一帧，保证画面从切换瞬间无缝衔接、不黑屏不跳帧。
     */
    private boolean framePendingRender;
    private volatile int srcWidth = 1;
    private volatile int srcHeight = 1;
    /** FBO 纹理固定按显示尺寸分配一次，档位切换只改视口，不做重新分配（避免闪屏与额外开销） */
    private int fboAllocWidth = 1;
    private int fboAllocHeight = 1;
    /**
     * Pass1（EASU）本帧的渲染尺寸：
     * AI 增强档 = 显示尺寸（放大到屏幕/伪 4K 缓冲），增强档 = 源尺寸（1:1，零缩放损失、更省电）。
     */
    private int passWidth = 1;
    private int passHeight = 1;

    // ---- 调试埋点：每 2 秒汇总一次真实处理量 ----
    private long frameCount;
    private long decoderFrameCount;
    private long lastLogNanos;
    private long loggedFrames;

    public GLQualityPipeline(@NonNull Surface output, int dstW, int dstH, @NonNull Callback callback) {
        this.outputSurface = output;
        this.dstWidth = Math.max(1, dstW);
        this.dstHeight = Math.max(1, dstH);
        this.callback = callback;
        start();
    }

    private void start() {
        glThread = new HandlerThread("ai-quality-gl");
        glThread.start();
        glHandler = new Handler(glThread.getLooper());
        glHandler.post(() -> {
            if (released) return;
            if (!initEgl() || !initGl()) {
                Log.e(AiQuality.TAG, "pipeline init FAILED, eglError=0x"
                        + Integer.toHexString(EGL14.eglGetError())
                        + " glError=0x" + Integer.toHexString(GLES30.glGetError())
                        + " | easuProg=" + easuProgram + " postProg=" + postProgram
                        + " | display=" + (eglDisplay != EGL14.EGL_NO_DISPLAY)
                        + " ctx=" + (eglContext != EGL14.EGL_NO_CONTEXT)
                        + " surf=" + (eglSurface != EGL14.EGL_NO_SURFACE)
                        + ", falling back to direct decode output");
                cleanup();
                callback.onError();
                return;
            }
            Log.d(AiQuality.TAG, "pipeline resources ready | renderer=" + GLES30.glGetString(GLES30.GL_RENDERER)
                    + " | targetBuffer=" + dstWidth + "x" + dstHeight
                    + " | easuProg=" + easuProgram + " postProg=" + postProgram
                    + " | decoderSurface=" + decoderSurface);
            // 先把解码器切到我们的 SurfaceTexture，旧的显示表面连接断开后才能挂载输出
            callback.onReady(decoderSurface);
            glHandler.postDelayed(this::tryAttachOutput, 200);
        });
    }

    /**
     * 挂载显示输出表面。此时解码器可能刚从显示表面断开（连接异步释放），
     * 因此用重试兜底，成功后才开始真正的逐帧渲染。
     */
    private void tryAttachOutput() {
        if (released || outputAttached) return;
        EGLSurface ws = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, outputSurface, new int[]{EGL14.EGL_NONE}, 0);
        if (ws == EGL14.EGL_NO_SURFACE) {
            if (++attachRetries <= 40) {
                if (attachRetries == 1 || attachRetries % 10 == 0) {
                    Log.w(AiQuality.TAG, "output surface busy (retry " + attachRetries
                            + "), eglError=0x" + Integer.toHexString(EGL14.eglGetError()));
                }
                glHandler.postDelayed(this::tryAttachOutput, 200);
                return;
            }
            Log.e(AiQuality.TAG, "attach output FAILED after retries, falling back");
            cleanup();
            callback.onError();
            return;
        }
        if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface);
        eglSurface = ws;
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            Log.e(AiQuality.TAG, "makeCurrent on output FAILED, eglError=0x" + Integer.toHexString(EGL14.eglGetError()));
            cleanup();
            callback.onError();
            return;
        }
        int[] value = new int[1];
        outWidth = EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL_WIDTH, value, 0) && value[0] > 0 ? value[0] : dstWidth;
        outHeight = EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL_HEIGHT, value, 0) && value[0] > 0 ? value[0] : dstHeight;
        outputAttached = true;
        Log.d(AiQuality.TAG, "display surface attached " + outWidth + "x" + outHeight
                + " | retries=" + attachRetries);
        // 挂载成功后立即补渲染等待期间消费的最后一帧，画面无缝衔接（不等下一个解码帧）
        if (framePendingRender) {
            framePendingRender = false;
            glHandler.post(() -> {
                if (released || !outputAttached) return;
                try {
                    renderFrame();
                    Log.d(AiQuality.TAG, "pending frame rendered after attach (seamless hot-switch)");
                } catch (Exception e) {
                    Log.w(AiQuality.TAG, "pending frame render failed: " + e);
                }
            });
        }
    }

    /** 供 MediaCodec / ExoPlayer 作为解码输出目标，初始化完成前返回 null */
    public Surface getDecoderSurface() {
        return decoderSurface;
    }

    /** 视频分辨率变化（切换清晰度、起播拿到真实尺寸）时更新，无需重建管线 */
    public void setSource(int srcW, int srcH) {
        this.srcWidth = Math.max(1, srcW);
        this.srcHeight = Math.max(1, srcH);
        if (glHandler != null && !released) {
            glHandler.post(() -> {
                if (decoderTexture != null) decoderTexture.setDefaultBufferSize(srcWidth, srcHeight);
            });
        }
    }

    // ---------------- EGL ----------------

    /**
     * 初始化 EGL：只建 display/config/context，并用 1x1 pbuffer 作为临时绘图面。
     *
     * <p>关键点：此时解码器仍连着显示 Surface（一个 Surface 只允许一个生产者），
     * 直接对它建窗口表面必然 EGL_BAD_ALLOC（神盾实测 0x3003）。
     * 所以先用 pbuffer 建 GL 资源并切解码器，等连接断开后再由 {@link #tryAttachOutput()} 挂载窗口表面。
     */
    private boolean initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false;
        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return false;

        int[] attribs = {
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT | EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) return false;
        eglConfig = configs[0];

        int[] ctxAttribs = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE};
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0);
        if (eglContext == EGL14.EGL_NO_CONTEXT) return false;

        // 1x1 pbuffer：仅为让上下文可用（创建纹理/着色器需要 current context）
        int[] pbAttribs = {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE};
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], pbAttribs, 0);
        if (eglSurface == EGL14.EGL_NO_SURFACE) return false;

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return false;

        // 显示表面挂载前，先用目标缓冲尺寸占位
        outWidth = dstWidth;
        outHeight = dstHeight;
        return true;
    }

    // ---------------- GL 资源 ----------------

    private boolean initGl() {
        easuProgram = buildProgram(Shaders.VERT, Shaders.EASU_FRAG);
        postProgram = buildProgram(Shaders.VERT, Shaders.POST_FRAG);
        if (easuProgram == 0 || postProgram == 0) return false;

        // VBO
        int[] vbos = new int[1];
        GLES30.glGenBuffers(1, vbos, 0);
        vboId = vbos[0];
        FloatBuffer vb = ByteBuffer.allocateDirect(VERTICES.length * FLOAT_SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vb.put(VERTICES).position(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, VERTICES.length * FLOAT_SIZE, vb, GLES30.GL_STATIC_DRAW);

        // OES 纹理：接收解码器输出
        int[] tex = new int[1];
        GLES30.glGenTextures(1, tex, 0);
        oesTexId = tex[0];
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId);
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

        decoderTexture = new SurfaceTexture(oesTexId);
        decoderTexture.setDefaultBufferSize(srcWidth, srcHeight);
        decoderTexture.setOnFrameAvailableListener(this);
        decoderSurface = new Surface(decoderTexture);

        // FBO：Pass1 的输出，按显示分辨率分配（4K 屏即 4K 缓冲）
        int[] fboTex = new int[1];
        GLES30.glGenTextures(1, fboTex, 0);
        fboTexId = fboTex[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexId);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, dstWidth, dstHeight, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

        int[] fbos = new int[1];
        GLES30.glGenFramebuffers(1, fbos, 0);
        fboId = fbos[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, fboTexId, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        fboAllocWidth = dstWidth;
        fboAllocHeight = dstHeight;
        passWidth = dstWidth;
        passHeight = dstHeight;
        return true;
    }

    private int buildProgram(String vert, String frag) {
        int vs = compile(GLES30.GL_VERTEX_SHADER, vert);
        int fs = compile(GLES30.GL_FRAGMENT_SHADER, frag);
        if (vs == 0 || fs == 0) return 0;
        int program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, vs);
        GLES30.glAttachShader(program, fs);
        GLES30.glLinkProgram(program);
        int[] linked = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0);
        GLES30.glDeleteShader(vs);
        GLES30.glDeleteShader(fs);
        if (linked[0] == 0) {
            GLES30.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    private int compile(int type, String source) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        int[] status = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String info = GLES30.glGetShaderInfoLog(shader);
            Log.e(AiQuality.TAG, "shader compile failed (type=" + type + "): " + info);
            GLES30.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    // ---------------- 渲染 ----------------

    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        frameAvailable = true;
        // 调试：每 100 帧打一条，确认解码器真的把帧送进了 SurfaceTexture
        if (++decoderFrameCount % 100 == 1) {
            Log.d(AiQuality.TAG, "decoder frame arrived #" + decoderFrameCount);
        }
        if (glHandler != null && !released) glHandler.post(this::draw);
    }

    private void draw() {
        if (released || !frameAvailable) return;
        frameAvailable = false;
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglContext == EGL14.EGL_NO_CONTEXT || decoderTexture == null) return;
        try {
            if (!outputAttached) {
                // 【热切换死锁修复】输出表面还没挂上时（解码器刚从屏幕切过来的等待窗口），
                // 也必须立刻 updateTexImage 消费帧：SurfaceTexture 只有约 3 个缓冲位，
                // 解码器持续送帧而消费端不取，几帧后解码器就被堵死——
                // 表现正是"画面定格在切换瞬间的帧、声音照常继续"。
                // 这里只消费不渲染，腾出缓冲让解码器继续跑，挂载完成后由补渲染上屏。
                decoderTexture.updateTexImage();
                decoderTexture.getTransformMatrix(texMatrix);
                framePendingRender = true;
                return;
            }
            framePendingRender = false;
            decoderTexture.updateTexImage();
            decoderTexture.getTransformMatrix(texMatrix);
            renderFrame();
        } catch (Exception e) {
            Log.w(AiQuality.TAG, "frame draw failed: " + e);
        }
    }

    /** 用最近一次消费的解码帧走完整渲染并上屏（挂载完成后的补渲染也走这里） */
    private void renderFrame() {
        updatePassSize();
        renderEasu();
        renderPost();
        EGL14.eglSwapBuffers(eglDisplay, eglSurface);
        logFrame();
    }

    /** 每 2 秒打一条，证明着色器真的在逐帧处理（含帧号、档位、各 pass 实际尺寸、实测帧率） */
    private void logFrame() {
        frameCount++;
        long now = System.nanoTime();
        if (lastLogNanos == 0) {
            lastLogNanos = now;
            return;
        }
        long span = now - lastLogNanos;
        if (span < 2_000_000_000L) return;
        double fps = (frameCount - loggedFrames) * 1e9 / span;
        Log.d(AiQuality.TAG, "processed | frames=" + frameCount
                + " | mode=" + AiQuality.getLevelName()
                + " | src=" + srcWidth + "x" + srcHeight
                + " | pass1(EASU)=" + passWidth + "x" + passHeight
                + " | pass2(CAS)=" + outWidth + "x" + outHeight
                + " | upscale=" + AiQuality.needUpscale()
                + " | sharp=" + (AiQuality.needSharpen() ? AiQuality.getSharpness() : 0f)
                + " | denoise=" + (AiQuality.needEnhance() ? AiQuality.getDenoise() : 0f)
                + " | fps=" + String.format(Locale.US, "%.1f", fps));
        lastLogNanos = now;
        loggedFrames = frameCount;
    }

    /**
     * 按当前档位计算 Pass1 的渲染尺寸（每帧调用，切档下一帧立即生效）：
     * <ul>
     *   <li>AI增强档 —— 渲染到显示尺寸（或伪 4K 缓冲），EASU 放大重建细节</li>
     *   <li>增强档   —— 渲染到源尺寸，1:1 只做 CAS 锐化，省电且零缩放损失</li>
     * </ul>
     * 渲染尺寸不会超过 FBO 分配尺寸，因此无需重新分配纹理。
     */
    private void updatePassSize() {
        if (AiQuality.needUpscale()) {
            passWidth = dstWidth;
            passHeight = dstHeight;
        } else {
            passWidth = Math.min(srcWidth, dstWidth);
            passHeight = Math.min(srcHeight, dstHeight);
        }
    }

    /** Pass1：OES 解码帧 → EASU 边缘自适应超分 → FBO */
    private void renderEasu() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId);
        GLES30.glViewport(0, 0, passWidth, passHeight);
        GLES30.glUseProgram(easuProgram);
        bindGeometry(easuProgram);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId);
        GLES30.glUniform1i(GLES30.glGetUniformLocation(easuProgram, "uTexture"), 0);
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(easuProgram, "uTexMatrix"), 1, false, texMatrix, 0);
        GLES30.glUniform2f(GLES30.glGetUniformLocation(easuProgram, "uTexel"), 1f / srcWidth, 1f / srcHeight);
        GLES30.glUniform2f(GLES30.glGetUniformLocation(easuProgram, "uScale"),
                (float) passWidth / srcWidth, (float) passHeight / srcHeight);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    /** Pass2：FBO → CAS 锐化 / 降噪 / 色彩 → 显示 Surface（视口用屏幕尺寸，缓冲更大时自动缩放） */
    private void renderPost() {
        GLES30.glViewport(0, 0, outWidth, outHeight);
        GLES30.glUseProgram(postProgram);
        bindGeometry(postProgram);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexId);
        GLES30.glUniform1i(GLES30.glGetUniformLocation(postProgram, "uTexture"), 0);
        // Pass2 采样的是自己写入的 FBO，uv 就是几何坐标本身，必须用单位矩阵
        // （若沿用解码 OES 变换矩阵，翻转/裁切会被应用两次导致画面错乱）
        android.opengl.Matrix.setIdentityM(texMatrix, 0);
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(postProgram, "uTexMatrix"), 1, false, texMatrix, 0);
        // 采样区只覆盖 Pass1 实际渲染到的部分（增强档只用了 FBO 左上角的源尺寸区域）
        GLES30.glUniform2f(GLES30.glGetUniformLocation(postProgram, "uUvScale"),
                (float) passWidth / dstWidth, (float) passHeight / dstHeight);
        GLES30.glUniform2f(GLES30.glGetUniformLocation(postProgram, "uTexel"), 1f / fboAllocWidth, 1f / fboAllocHeight);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(postProgram, "uSharpness"),
                AiQuality.needSharpen() ? AiQuality.getSharpness() : 0f);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(postProgram, "uDenoise"),
                AiQuality.needEnhance() ? AiQuality.getDenoise() : 0f);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(postProgram, "uEnhance"), AiQuality.needEnhance() ? 1f : 0f);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
    }

    private void bindGeometry(int program) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId);
        int stride = 4 * FLOAT_SIZE;
        int aPos = GLES30.glGetAttribLocation(program, "aPosition");
        int aTex = GLES30.glGetAttribLocation(program, "aTexCoord");
        if (aPos >= 0) {
            GLES30.glEnableVertexAttribArray(aPos);
            GLES30.glVertexAttribPointer(aPos, 2, GLES30.GL_FLOAT, false, stride, 0);
        }
        if (aTex >= 0) {
            GLES30.glEnableVertexAttribArray(aTex);
            GLES30.glVertexAttribPointer(aTex, 2, GLES30.GL_FLOAT, false, stride, 2 * FLOAT_SIZE);
        }
    }

    public void release() {
        released = true;
        if (glHandler != null) {
            glHandler.post(this::cleanup);
            glHandler.removeCallbacks(this::draw);
        }
        if (glThread != null) glThread.quitSafely();
    }

    private void cleanup() {
        destroyGl();
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        }
        if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface);
        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext);
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(eglDisplay);
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglSurface = EGL14.EGL_NO_SURFACE;
    }

    private void destroyGl() {
        if (decoderTexture != null) decoderTexture.release();
        if (decoderSurface != null) decoderSurface.release();
        decoderTexture = null;
        decoderSurface = null;
        if (easuProgram != 0) GLES30.glDeleteProgram(easuProgram);
        if (postProgram != 0) GLES30.glDeleteProgram(postProgram);
        easuProgram = 0;
        postProgram = 0;
        int[] texIds = new int[]{oesTexId, fboTexId};
        GLES30.glDeleteTextures(2, texIds, 0);
        oesTexId = 0;
        fboTexId = 0;
        int[] fbs = new int[]{fboId};
        GLES30.glDeleteFramebuffers(1, fbs, 0);
        fboId = 0;
        int[] vbos = new int[]{vboId};
        GLES30.glDeleteBuffers(1, vbos, 0);
        vboId = 0;
    }
}
