/* JNI bindings of vfb_renderer for com.shilapi.xcertplay.media.GeelyClusterMap. */
#include <jni.h>

#include "vfb_renderer.h"

#define CLASS "com/shilapi/xcertplay/media/GeelyClusterMap"

/* Opens the cluster surface on the first call (always the same render thread) and returns a new
 * OES texture name for a SurfaceTexture, or -1. */
static jint native_open(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    if (vfb_open() != 0) return -1;
    return vfb_create_oes_texture();
}

static jboolean native_draw(JNIEnv *env, jclass cls, jint texture, jfloatArray matrix) {
    (void)cls;
    if (matrix == NULL || (*env)->GetArrayLength(env, matrix) < 16) return JNI_FALSE;
    jfloat m[16];
    (*env)->GetFloatArrayRegion(env, matrix, 0, 16, m);
    return vfb_draw_oes((unsigned int)texture, m) == 0 ? JNI_TRUE : JNI_FALSE;
}

static void native_delete_texture(JNIEnv *env, jclass cls, jint texture) {
    (void)env; (void)cls;
    vfb_delete_texture((unsigned int)texture);
}

static const JNINativeMethod METHODS[] = {
    { "nativeOpen", "()I", (void *)native_open },
    { "nativeDraw", "(I[F)Z", (void *)native_draw },
    { "nativeDeleteTexture", "(I)V", (void *)native_delete_texture },
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_4) != JNI_OK) return JNI_ERR;
    jclass cls = (*env)->FindClass(env, CLASS);
    if (cls == NULL) return JNI_ERR;   /* loadLibrary throws; GeelyClusterMap.available() reports false */
    if ((*env)->RegisterNatives(env, cls, METHODS, sizeof METHODS / sizeof METHODS[0]) != 0) return JNI_ERR;
    return JNI_VERSION_1_4;
}
