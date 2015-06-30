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

#include "jni_NativeHub.h"
#include "Log.h"
#include "NativeHub.h"
#include "Glue.h"
#include <string>
#include <list>

#define LOG_TAG "OM-jniNativeHub"

// JNI fields
jclass clazz_NativeHub;

jfieldID field_NativeHub_mHandle;
jfieldID field_NativeHub_mAudioMirrorBuffer;

// Functions
jmethodID method_NativeHub_onAudioMirrorWritten;

// -------------------------------------------------------------------------------------
NativeHub* get_hub_from_object(JNIEnv* env, jobject javaObject) {
    return reinterpret_cast<NativeHub*>
            (env->GetLongField(javaObject, field_NativeHub_mHandle));
}
// -------------------------------------------------------------------------------------
int JNI_NativeHub_SetupFields(JNIEnv* env) {
    jclass clazz;
    clazz = env->FindClass("com/fastbootmobile/encore/service/NativeHub");
    if (clazz == NULL) {
        ALOGE("Can't find com/fastbootmobile/encore/service/NativeHub");
        return -1;
    }

    field_NativeHub_mHandle = env->GetFieldID(clazz, "mHandle", "J");
    if (field_NativeHub_mHandle == NULL) {
        ALOGE("Can't find NativeHub.mHandle");
        return -1;
    }

    field_NativeHub_mAudioMirrorBuffer = env->GetFieldID(clazz, "mAudioMirrorBuffer", "[B");
    if (field_NativeHub_mAudioMirrorBuffer == NULL) {
        ALOGE("Can't find NativeHub.mAudioMirrorBuffer");
        return -1;
    }

    method_NativeHub_onAudioMirrorWritten = env->GetMethodID(clazz, "onAudioMirrorWritten", "(III)V");
    if (method_NativeHub_onAudioMirrorWritten == NULL) {
        ALOGE("Can't find NativeHub.onAudioMirrorWritten");
        return -1;
    }

    clazz_NativeHub = (jclass) env->NewGlobalRef(clazz);

    return 0;
}
// -------------------------------------------------------------------------------------
jboolean om_NativeHub_initialize(JNIEnv* env, jobject thiz) {
    bool result = false;

    NativeHub* hub = new NativeHub(reinterpret_cast<void*>(env->NewGlobalRef(thiz)));
    if (hub) {
        env->SetLongField(thiz, field_NativeHub_mHandle, (jlong) hub);
    }

    return (hub != nullptr);
}
// -------------------------------------------------------------------------------------
void om_NativeHub_shutdown(JNIEnv* env, jobject thiz) {
    NativeHub* hub = get_hub_from_object(env, thiz);
    delete hub;
    env->SetLongField(thiz, field_NativeHub_mHandle, (jlong) 0);
}
// -------------------------------------------------------------------------------------
void om_NativeHub_setDSPChain(JNIEnv* env, jobject thiz, jobjectArray chain) {
    const int length = env->GetArrayLength(chain);
    std::list<std::string> chain_list;

    for (int i = 0; i < length; ++i) {
        jstring socket_name = (jstring) env->GetObjectArrayElement(chain, i);
        if (!socket_name) {
            ALOGE("DSPChain(%d): Null pointer for element: %p", i, socket_name);
        } else {
            const char* socket_name_str = env->GetStringUTFChars(socket_name, 0);
            chain_list.push_back(socket_name_str);
            env->ReleaseStringUTFChars(socket_name, socket_name_str);
        }
    }

    NativeHub* hub = get_hub_from_object(env, thiz);
    hub->setDSPChain(chain_list);
}
// -------------------------------------------------------------------------------------
jboolean om_NativeHub_createHostSocket(JNIEnv* env, jobject thiz, jstring name, jboolean is_dsp) {
    const char* socket_name = env->GetStringUTFChars(name, 0);
    NativeHub* hub = get_hub_from_object(env, thiz);

    SocketCommon* socket = hub->createHostSocket(socket_name, is_dsp);
    env->ReleaseStringUTFChars(name, socket_name);

    return socket != nullptr;
}
// -------------------------------------------------------------------------------------
void om_NativeHub_releaseHostSocket(JNIEnv* env, jobject thiz, jstring name) {
    const char* socket_name = env->GetStringUTFChars(name, 0);
    NativeHub* hub = get_hub_from_object(env, thiz);

    // HUB pointer might be null if the socket release call happens after shutdown
    if (hub) {
        hub->releaseHostSocket(socket_name);
    }
    env->ReleaseStringUTFChars(name, socket_name);
}
// -------------------------------------------------------------------------------------
void om_NativeHub_setSinkPointer(JNIEnv* env, jobject thiz, jlong handle) {
    NativeHub* hub = get_hub_from_object(env, thiz);
    hub->setSink(reinterpret_cast<INativeSink*>(handle));
}
// -------------------------------------------------------------------------------------
void om_NativeHub_setDucking(JNIEnv* env, jobject thiz, jboolean duck) {
    NativeHub* hub = get_hub_from_object(env, thiz);
    hub->setDucking(duck);
}
// -------------------------------------------------------------------------------------
void om_NativeHub_onAudioMirrorWritten(NativeHub* hub, const uint8_t* data, jint len,
        jint sampleRate, jint channels) {
    JNIEnv* env;
    bool release_jni = JNI_GetEnv(&env);
    jobject thiz = (jobject) hub->getUserData();

    // Write the bytes to the host array
    jobject audioMirrorBufferObj = env->GetObjectField(thiz, field_NativeHub_mAudioMirrorBuffer);
    if (audioMirrorBufferObj) {
        jbyteArray arr = reinterpret_cast<jbyteArray>(audioMirrorBufferObj);
        env->SetByteArrayRegion(arr, 0, len, reinterpret_cast<const jbyte*>(data));
        env->DeleteLocalRef(audioMirrorBufferObj);

        // Notify Java new bytes are available
        env->CallVoidMethod(thiz, method_NativeHub_onAudioMirrorWritten, len, sampleRate, channels);
    }

    if (release_jni) {
        JNI_ReleaseEnv();
    }
}
// -------------------------------------------------------------------------------------
