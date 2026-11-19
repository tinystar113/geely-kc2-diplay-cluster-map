/*
 * vfbtest: open the H52 cluster map surface the same way the stock Gaode does
 * (/system/lib/libexternalsurfacehelper.so -> neusoft_createDisplaySurface_vfb -> fb2)
 * and draw moving colour bars on it with EGL/GLES2 for N seconds, then release it.
 *
 * Usage (adb shell): /data/local/tmp/vfbtest [seconds]
 * Read-only for the system: it only draws on the cluster surface and releases it.
 */
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <unistd.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>

typedef int (*get_surface_fn)(ANativeWindow **out);
typedef void (*release_fn)(ANativeWindow *win);

static double now_s(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec / 1e9;
}

int main(int argc, char **argv) {
    int seconds = argc > 1 ? atoi(argv[1]) : 30;

    void *lib = dlopen("/system/lib/libexternalsurfacehelper.so", RTLD_NOW);
    if (!lib) { printf("dlopen failed: %s\n", dlerror()); return 1; }
    get_surface_fn get_surface =
        (get_surface_fn)dlsym(lib, "_Z31getExternalDisplayNativeSurfacePP13ANativeWindow");
    release_fn release = (release_fn)dlsym(lib, "_Z19releaseNativeWindowP13ANativeWindow");
    if (!get_surface) { printf("getExternalDisplayNativeSurface not found\n"); return 2; }

    ANativeWindow *win = NULL;
    int direct = argc > 2 && argv[2][0] == 'd';
    if (!direct) {
        int rc = get_surface(&win);
        printf("getExternalDisplayNativeSurface rc=%d window=%p\n", rc, (void *)win);
    }
    if (!win) {
        /* Fallback: the libui function the helper wraps (extern "C", like android_createDisplaySurface). */
        void *ui = dlopen("/system/lib/libui.so", RTLD_NOW);
        typedef ANativeWindow *(*create_fn)(void);
        create_fn create = ui ? (create_fn)dlsym(ui, "neusoft_createDisplaySurface_vfb") : NULL;
        printf("fallback neusoft_createDisplaySurface_vfb=%p\n", (void *)create);
        if (create) win = create();
        printf("fallback window=%p\n", (void *)win);
        release = NULL; /* not created by the helper */
    }
    if (!win) return 3;
    printf("window size %dx%d format %d\n", ANativeWindow_getWidth(win),
           ANativeWindow_getHeight(win), ANativeWindow_getFormat(win));

    EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (!eglInitialize(dpy, NULL, NULL)) { printf("eglInitialize failed 0x%x\n", eglGetError()); return 4; }

    EGLint want[] = { EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                      EGL_RED_SIZE, 5, EGL_GREEN_SIZE, 6, EGL_BLUE_SIZE, 5, EGL_NONE };
    EGLConfig configs[32];
    EGLint count = 0;
    eglChooseConfig(dpy, want, configs, 32, &count);
    printf("matching EGL configs: %d\n", count);
    EGLint visual = ANativeWindow_getFormat(win);
    EGLSurface surf = EGL_NO_SURFACE;
    EGLConfig cfg = 0;
    /* Prefer the config whose native visual matches the window, as FramebufferNativeWindow needs. */
    for (int pass = 0; pass < 2 && surf == EGL_NO_SURFACE; pass++) {
        for (int i = 0; i < count; i++) {
            EGLint id = 0;
            eglGetConfigAttrib(dpy, configs[i], EGL_NATIVE_VISUAL_ID, &id);
            if (pass == 0 && id != visual) continue;
            surf = eglCreateWindowSurface(dpy, configs[i], win, NULL);
            if (surf != EGL_NO_SURFACE) { cfg = configs[i]; printf("using config %d visual %d\n", i, id); break; }
        }
    }
    if (surf == EGL_NO_SURFACE) { printf("eglCreateWindowSurface failed 0x%x\n", eglGetError()); return 5; }

    EGLint ctx_attr[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    EGLContext ctx = eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctx_attr);
    if (!eglMakeCurrent(dpy, surf, surf, ctx)) { printf("eglMakeCurrent failed 0x%x\n", eglGetError()); return 6; }

    EGLint w = 0, h = 0;
    eglQuerySurface(dpy, surf, EGL_WIDTH, &w);
    eglQuerySurface(dpy, surf, EGL_HEIGHT, &h);
    printf("drawing on %dx%d for %d s\n", w, h, seconds);

    static const float colors[4][3] = { {1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 1, 0} };
    double start = now_s();
    int frames = 0;
    glEnable(GL_SCISSOR_TEST);
    while (now_s() - start < seconds) {
        int shift = (int)(now_s() - start);           /* bars rotate once per second */
        for (int b = 0; b < 4; b++) {
            const float *c = colors[(b + shift) % 4];
            glScissor(b * w / 4, 0, w / 4 + 1, h);
            glClearColor(c[0], c[1], c[2], 1);
            glClear(GL_COLOR_BUFFER_BIT);
        }
        glScissor(w / 4, h / 3, w / 2, h / 3);          /* black box in the middle */
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        eglSwapBuffers(dpy, surf);
        frames++;
        usleep(33000);
    }
    printf("frames drawn: %d\n", frames);

    setvbuf(stdout, NULL, _IONBF, 0);
    printf("cleanup: unbind\n");
    eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    printf("cleanup: destroy surface/context\n");
    eglDestroySurface(dpy, surf);
    eglDestroyContext(dpy, ctx);
    /* No eglTerminate: the run on the car crashed during cleanup; release the window
       while the EGL display is still alive, and let process exit tear EGL down. */
    if (argc > 3 && argv[3][0] == 'n') {
        printf("cleanup: NOT releasing window; idling 12 s to see if the stream stops\n");
        sleep(12);
        printf("idle done\n");
        return 0;
    }
    printf("cleanup: release window\n");
    if (release) release(win);
    printf("released\n");
    return 0;
}
