#include "vfb_renderer.h"

#include <android/log.h>
#include <android/native_window.h>
#include <dlfcn.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <stddef.h>

#define TAG "DiPlay-H52-VFB"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define HELPER_LIB "/system/lib/libexternalsurfacehelper.so"
#define HELPER_GET "_Z31getExternalDisplayNativeSurfacePP13ANativeWindow"

static ANativeWindow *g_window;          /* cached for the process lifetime, never released */
static EGLDisplay g_display = EGL_NO_DISPLAY;
static EGLSurface g_surface = EGL_NO_SURFACE;
static EGLContext g_context = EGL_NO_CONTEXT;
static int g_width, g_height;
static GLuint g_program;                 /* belongs to g_context; reset on close */
static GLint g_attr_pos, g_attr_uv, g_uni_matrix, g_uni_tex;

static int obtain_window(void) {
    if (g_window) return 0;
    void *lib = dlopen(HELPER_LIB, RTLD_NOW);
    if (!lib) { LOGE("dlopen %s: %s", HELPER_LIB, dlerror()); return -1; }
    typedef int (*get_fn)(ANativeWindow **);
    get_fn get = (get_fn)dlsym(lib, HELPER_GET);
    if (!get) { LOGE("helper symbol missing"); return -1; }
    ANativeWindow *win = NULL;
    get(&win);
    if (!win) { LOGE("helper returned no window"); return -1; }
    g_window = win;
    LOGI("cluster window %dx%d format %d", ANativeWindow_getWidth(win), ANativeWindow_getHeight(win),
         ANativeWindow_getFormat(win));
    return 0;
}

int vfb_open(void) {
    if (g_context != EGL_NO_CONTEXT) return 0;
    if (obtain_window() != 0) return -1;

    g_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (!eglInitialize(g_display, NULL, NULL)) { LOGE("eglInitialize 0x%x", eglGetError()); return -1; }

    const EGLint want[] = { EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                            EGL_RED_SIZE, 5, EGL_GREEN_SIZE, 6, EGL_BLUE_SIZE, 5, EGL_NONE };
    EGLConfig configs[32];
    EGLint count = 0;
    eglChooseConfig(g_display, want, configs, 32, &count);
    const EGLint visual = ANativeWindow_getFormat(g_window);
    EGLConfig chosen = NULL;
    /* FramebufferNativeWindow needs the config whose native visual matches the window. */
    for (int i = 0; i < count && g_surface == EGL_NO_SURFACE; i++) {
        EGLint id = 0;
        eglGetConfigAttrib(g_display, configs[i], EGL_NATIVE_VISUAL_ID, &id);
        if (id != visual) continue;
        g_surface = eglCreateWindowSurface(g_display, configs[i], g_window, NULL);
        if (g_surface != EGL_NO_SURFACE) chosen = configs[i];
    }
    if (g_surface == EGL_NO_SURFACE) { LOGE("no window surface (visual %d, %d configs)", visual, count); return -1; }

    const EGLint ctx_attr[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    g_context = eglCreateContext(g_display, chosen, EGL_NO_CONTEXT, ctx_attr);
    if (g_context == EGL_NO_CONTEXT || !eglMakeCurrent(g_display, g_surface, g_surface, g_context)) {
        LOGE("context/makeCurrent 0x%x", eglGetError());
        vfb_close();
        return -1;
    }
    eglQuerySurface(g_display, g_surface, EGL_WIDTH, &g_width);
    eglQuerySurface(g_display, g_surface, EGL_HEIGHT, &g_height);
    LOGI("open %dx%d", g_width, g_height);
    return 0;
}

void vfb_close(void) {
    if (g_display == EGL_NO_DISPLAY) return;
    eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (g_surface != EGL_NO_SURFACE) eglDestroySurface(g_display, g_surface);
    if (g_context != EGL_NO_CONTEXT) eglDestroyContext(g_display, g_context);
    g_surface = EGL_NO_SURFACE;
    g_context = EGL_NO_CONTEXT;
    g_program = 0;
    LOGI("closed");
    /* no eglTerminate, no window release: see vfb_renderer.h */
}

int vfb_is_open(void) { return g_context != EGL_NO_CONTEXT; }
int vfb_width(void) { return g_width; }
int vfb_height(void) { return g_height; }

static int swap(void) {
    if (!eglSwapBuffers(g_display, g_surface)) { LOGE("swap 0x%x", eglGetError()); return -1; }
    return 0;
}

static void fill(int x, int y, int w, int h, float r, float g, float b) {
    glScissor(x, y, w, h);
    glClearColor(r, g, b, 1);
    glClear(GL_COLOR_BUFFER_BIT);
}

int vfb_draw_bars(int shift) {
    if (!vfb_is_open()) return -1;
    static const float colors[4][3] = { {1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 1, 0} };
    glEnable(GL_SCISSOR_TEST);
    for (int b = 0; b < 4; b++) {
        const float *c = colors[(b + shift) % 4];
        fill(b * g_width / 4, 0, g_width / 4 + 1, g_height, c[0], c[1], c[2]);
    }
    fill(g_width / 4, g_height / 3, g_width / 2, g_height / 3, 0, 0, 0);
    glDisable(GL_SCISSOR_TEST);
    return swap();
}

int vfb_draw_grid(void) {
    if (!vfb_is_open()) return -1;
    const int w = g_width, h = g_height, t = 3, border = 4, corner = h / 20;
    glEnable(GL_SCISSOR_TEST);
    fill(0, 0, w, h, 0.15f, 0.15f, 0.15f);
    for (int p = 10; p <= 90; p += 10) {
        float r, g, b;
        if (p == 50) { r = 1; g = 1; b = 0; }
        else if (p % 20 == 0) { r = 0; g = 1; b = 1; }
        else { r = 1; g = 1; b = 1; }
        fill(w * p / 100 - t / 2, 0, t, h, r, g, b);                 /* vertical, p % from left */
        fill(0, h - h * p / 100 - t / 2, w, t, r, g, b);             /* horizontal, p % from top */
    }
    fill(0, 0, w, border, 1, 0, 0);
    fill(0, h - border, w, border, 1, 0, 0);
    fill(0, 0, border, h, 1, 0, 0);
    fill(w - border, 0, border, h, 1, 0, 0);
    fill(0, h - corner, corner, corner, 1, 0, 0);                    /* top-left red */
    fill(w - corner, h - corner, corner, corner, 0, 1, 0);           /* top-right green */
    fill(0, 0, corner, corner, 0, 0, 1);                             /* bottom-left blue */
    fill(w - corner, 0, corner, corner, 1, 0, 1);                    /* bottom-right magenta */
    glDisable(GL_SCISSOR_TEST);
    return swap();
}

int vfb_create_oes_texture(void) {
    if (!vfb_is_open()) return -1;
    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, tex);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return tex > 0 ? (int)tex : -1;
}

static GLuint shader(GLenum type, const char *src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, NULL);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetShaderInfoLog(s, sizeof log, NULL, log);
        LOGE("shader: %s", log);
        glDeleteShader(s);
        return 0;
    }
    return s;
}

static int ensure_program(void) {
    if (g_program) return 0;
    static const char *vs =
        "attribute vec2 aPos;\n"
        "attribute vec2 aUv;\n"
        "uniform mat4 uTex;\n"
        "varying vec2 vUv;\n"
        "void main() { gl_Position = vec4(aPos, 0.0, 1.0); vUv = (uTex * vec4(aUv, 0.0, 1.0)).xy; }\n";
    static const char *fs =
        "#extension GL_OES_EGL_image_external : require\n"
        "precision mediump float;\n"
        "uniform samplerExternalOES uSampler;\n"
        "varying vec2 vUv;\n"
        "void main() { gl_FragColor = texture2D(uSampler, vUv); }\n";
    GLuint v = shader(GL_VERTEX_SHADER, vs), f = shader(GL_FRAGMENT_SHADER, fs);
    if (!v || !f) return -1;
    GLuint p = glCreateProgram();
    glAttachShader(p, v);
    glAttachShader(p, f);
    glLinkProgram(p);
    glDeleteShader(v);
    glDeleteShader(f);
    GLint ok = 0;
    glGetProgramiv(p, GL_LINK_STATUS, &ok);
    if (!ok) { LOGE("program link failed"); glDeleteProgram(p); return -1; }
    g_program = p;
    g_attr_pos = glGetAttribLocation(p, "aPos");
    g_attr_uv = glGetAttribLocation(p, "aUv");
    g_uni_matrix = glGetUniformLocation(p, "uTex");
    g_uni_tex = glGetUniformLocation(p, "uSampler");
    return 0;
}

int vfb_draw_oes(unsigned int texture, const float tex_matrix[16]) {
    if (!vfb_is_open() || ensure_program() != 0) return -1;
    /* Full-screen quad as a triangle strip; uv (0,0) = bottom-left, as SurfaceTexture expects. */
    static const GLfloat pos[] = { -1, -1, 1, -1, -1, 1, 1, 1 };
    static const GLfloat uv[] = { 0, 0, 1, 0, 0, 1, 1, 1 };
    glViewport(0, 0, g_width, g_height);
    glUseProgram(g_program);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture);
    glUniform1i(g_uni_tex, 0);
    glUniformMatrix4fv(g_uni_matrix, 1, GL_FALSE, tex_matrix);
    glVertexAttribPointer(g_attr_pos, 2, GL_FLOAT, GL_FALSE, 0, pos);
    glVertexAttribPointer(g_attr_uv, 2, GL_FLOAT, GL_FALSE, 0, uv);
    glEnableVertexAttribArray(g_attr_pos);
    glEnableVertexAttribArray(g_attr_uv);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    return swap();
}
