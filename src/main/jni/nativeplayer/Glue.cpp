/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */
#include "Glue.h"
#include "jni_NativePlayer.h"
#include "jni_NativeHub.h"

#define NDEBUG
#define LOG_TAG "OM-Glue"

JavaVM* g_jvm = NULL;

// -------------------------------------------------------------------------------------

static JNINativeMethod gMethodsNativePlayer[] = {
    {"nativeInitialize", "()Z",
            reinterpret_cast<void*>(om_NativePlayer_initialize)},
    {"nativeSetAudioFormat", "(III)Z",
            reinterpret_cast<void*>(om_NativePlayer_setAudioFormat)},
    {"nativeEnqueue", "([BI)I",
            reinterpret_cast<void*>(om_NativePlayer_enqueue)},
    {"nativeEnqueueShort", "([SI)I",
            reinterpret_cast<void*>(om_NativePlayer_enqueueShort)},
    {"nativeGetBufferedCount", "()I",
            reinterpret_cast<void*>(om_NativePlayer_getBufferedCount)},
    {"nativeGetUnderflowCount", "()I",
            reinterpret_cast<void*>(om_NativePlayer_getUnderflowCount)},
    {"nativeGetTotalWrittenSamples", "()J",
            reinterpret_cast<void*>(om_NativePlayer_getTotalWrittenSamples)},
    {"nativeFlush", "()V",
            reinterpret_cast<void*>(om_NativePlayer_flush)},
    {"nativeSetPaused", "(Z)V",
            reinterpret_cast<void*>(om_NativePlayer_setPaused)},
    {"nativeShutdown", "()V",
            reinterpret_cast<void*>(om_NativePlayer_shutdown)},
};

static JNINativeMethod gMethodsNativeHub[] = {
    {"nativeInitialize", "()Z",
            reinterpret_cast<void*>(om_NativeHub_initialize)},
    {"nativeSetDSPChain", "([Ljava/lang/String;)V",
            reinterpret_cast<void*>(om_NativeHub_setDSPChain)},
    {"nativeCreateHostSocket", "(Ljava/lang/String;Z)Z",
            reinterpret_cast<void*>(om_NativeHub_createHostSocket)},
    {"nativeReleaseHostSocket", "(Ljava/lang/String;)V",
            reinterpret_cast<void*>(om_NativeHub_releaseHostSocket)},
    {"nativeSetSinkPointer", "(J)V",
            reinterpret_cast<void*>(om_NativeHub_setSinkPointer)},
    {"nativeSetDucking", "(Z)V",
            reinterpret_cast<void*>(om_NativeHub_setDucking)},
    {"nativeShutdown", "()V",
            reinterpret_cast<void*>(om_NativeHub_shutdown)},
};

// -------------------------------------------------------------------------------------
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    ALOGI("JNI_OnLoad");
    g_jvm = vm;

    JNIEnv* env = NULL;
    jint result = -1;

    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        ALOGE("ERROR: GetEnv failed");
        goto bail;
    }
    assert(env != NULL);

    if (JNI_RegisterNativePlayer(env) < 0) {
        ALOGE("ERROR: NativePlayer native registration failed");
        goto bail;
    }

    if (JNI_RegisterNativeHub(env) < 0) {
        ALOGE("ERROR: NativeHub native registration failed");
        goto bail;
    }

    /* success -- return valid version number */
    result = JNI_VERSION_1_6;

bail:
    return result;
}
// -------------------------------------------------------------------------------------
int JNI_ThrowException(JNIEnv* env, const char* className, const char* msg) {
    jclass exceptionClass = env->FindClass(className);
    if (exceptionClass == NULL) {
        ALOGE("Unable to find exception class %s", className);
        return -1;
    }

    if (env->ThrowNew(exceptionClass, msg) != JNI_OK) {
       ALOGE("Failed throwing '%s' '%s'", className, msg);
    }
    return 0;
}
// -------------------------------------------------------------------------------------
int JNI_RegisterNativeMethods(JNIEnv* env,
                             const char* className,
                             const JNINativeMethod* methods,
                             int numMethods) {
    jclass clazz;

    ALOGI("Registering %s natives", className);
    clazz = env->FindClass(className);
    if (clazz == NULL) {
        ALOGE("Native registration unable to find class '%s'", className);
        return -1;
    }
    if (env->RegisterNatives(clazz, methods, numMethods) < 0) {
        ALOGE("RegisterNatives failed for '%s'", className);
        return -1;
    }
    return 0;
}
// -------------------------------------------------------------------------------------
bool JNI_GetEnv(JNIEnv** env) {
    bool attached = false;

    // Try to get the current environment pointer
    int ret = g_jvm->GetEnv(reinterpret_cast<void**>(env), JNI_VERSION_1_6);

    if (ret != JNI_OK) {
        if (ret == JNI_EDETACHED) {
            ret = g_jvm->AttachCurrentThread(env, NULL);

            if (ret != JNI_OK) {
                ALOGE("GetEnv: AttachCurrentThread failed! %d", ret);
            } else {
                attached = true;
            }
        }
    }

    return attached;
}
// -------------------------------------------------------------------------------------
void JNI_ReleaseEnv() {
    g_jvm->DetachCurrentThread();
}
// -------------------------------------------------------------------------------------
int JNI_RegisterNativePlayer(JNIEnv* env) {
    ALOGD("JNI_RegisterNativePlayer");
    if (JNI_NativePlayer_SetupFields(env) < 0) {
        ALOGE("Cannot setup NativePlayer fields");
        return -1;
    }

    return JNI_RegisterNativeMethods(env, "com/fastbootmobile/encore/service/NativePlayer",
            gMethodsNativePlayer, NELEM(gMethodsNativePlayer));
}
// -------------------------------------------------------------------------------------
int JNI_RegisterNativeHub(JNIEnv* env) {
    ALOGD("JNI_RegisterNativeHub");
    if (JNI_NativeHub_SetupFields(env) < 0) {
        ALOGE("Cannot setup NativeHub fields");
        return -1;
    }

    return JNI_RegisterNativeMethods(env, "com/fastbootmobile/encore/service/NativeHub",
            gMethodsNativeHub, NELEM(gMethodsNativeHub));
}
// -------------------------------------------------------------------------------------
