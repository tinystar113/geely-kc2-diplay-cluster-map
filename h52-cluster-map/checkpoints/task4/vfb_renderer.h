/*
 * vfb_renderer: draw onto the Geely H52 cluster map surface (/dev/graphics/fb2 "Virtual FB").
 *
 * The surface comes from the car's /system/lib/libexternalsurfacehelper.so, the same way the stock
 * Gaode gets it. The car streams it to the cluster (screencap_neu, MJPEG) only while frames are
 * being swapped; ~1 s without a swap and the cluster returns to its normal view.
 *
 * Firmware rules (verified on the car):
 *  - the helper window is obtained at most once per process and never released
 *    (releaseNativeWindow() segfaults on this firmware);
 *  - the EGL surface/context on that window are never torn down: eglMakeCurrent(NONE) /
 *    eglDestroySurface deadlocked intermittently in the Vivante driver (gckOS_WaitSignal).
 *    To stop the cluster stream, simply stop drawing (the car stops streaming ~1 s later);
 *  - eglTerminate() is never called (the app's UI shares EGL_DEFAULT_DISPLAY).
 *
 * All functions must be called on ONE thread for the whole process: the context stays current there.
 * Return value 0 = success, negative = failure.
 */
#ifndef VFB_RENDERER_H
#define VFB_RENDERER_H

/* Once per process (later calls return 0): window + EGL display/config (visual 4 = RGB565)/
 * surface/GLES2 context, made current on the calling thread. */
int vfb_open(void);
int vfb_is_open(void);
int vfb_width(void);
int vfb_height(void);

/* New GL_TEXTURE_EXTERNAL_OES texture for a SurfaceTexture; returns its name (>0) or negative. */
int vfb_create_oes_texture(void);
void vfb_delete_texture(unsigned int texture);
/* Draw the external texture full-screen using the SurfaceTexture 4x4 transform, then swap. */
int vfb_draw_oes(unsigned int texture, const float tex_matrix[16]);

/* Test patterns (draw + swap). */
int vfb_draw_bars(int shift);
/*
 * Calibration grid on dark grey: 4 px red border; vertical/horizontal lines every 10 %;
 * line colours identify the percentage: 10/30/70/90 % white, 20/40/60/80 % cyan, 50 % yellow;
 * corner squares (5 % of height): top-left red, top-right green, bottom-left blue, bottom-right magenta.
 */
int vfb_draw_grid(void);

#endif
