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

#include "jni_NativePlayer.h"
#include "Log.h"
#include "NativePlayer.h"
#include "Glue.h"

#define LOG_TAG "OM-jniNativePlayer"

// JNI fields
jclass clazz_NativePlayer;

jfieldID field_NativePlayer_mHandle;

// Functions

// -------------------------------------------------------------------------------------
NativePlayer* get_layer_from_object(JNIEnv* env, jobject javaObject) {
    return reinterpret_cast<NativePlayer*>
            (env->GetLongField(javaObject, field_NativePlayer_mHandle));
}
// -------------------------------------------------------------------------------------
int JNI_NativePlayer_SetupFields(JNIEnv* env) {
    jclass clazz;
    clazz = env->FindClass("org/omnirom/music/service/NativePlayer");
    if (clazz == NULL) {
        ALOGE("Can't find org/omnirom/music/service/NativePlayer");
        return -1;
    }

    field_NativePlayer_mHandle = env->GetFieldID(clazz, "mHandle", "J");
    if (field_NativePlayer_mHandle == NULL) {
        ALOGE("Can't find NativePlayer.mHandle");
        return -1;
    }

    clazz_NativePlayer = (jclass) env->NewGlobalRef(clazz);

    return 0;
}
// -------------------------------------------------------------------------------------
jboolean om_NativePlayer_initialize(JNIEnv* env, jobject thiz) {
    bool result = false;

    NativePlayer* player = new NativePlayer();
    if (player) {
        env->SetLongField(thiz, field_NativePlayer_mHandle, (jlong) player);
        result = player->createEngine();
    }

    return (player != NULL) && result;
}
// -------------------------------------------------------------------------------------
jboolean om_NativePlayer_setAudioFormat(JNIEnv* env, jobject thiz, jint sample_rate, jint channels,
                                            jint depth) {
    NativePlayer* player = get_layer_from_object(env, thiz);
    return player->setAudioFormat(sample_rate, depth, channels);
}
// -------------------------------------------------------------------------------------
jint om_NativePlayer_enqueue(JNIEnv* env, jobject thiz, jbyteArray samples, jint length) {
    NativePlayer* player = get_layer_from_object(env, thiz);
    jbyte* samples_bytes = reinterpret_cast<jbyte*>(env->GetByteArrayElements(samples, 0));

    jint written = player->enqueue(reinterpret_cast<uint8_t*>(samples_bytes), length);

    env->ReleaseByteArrayElements(samples, samples_bytes, 0);

    return written;
}
// -------------------------------------------------------------------------------------
jint om_NativePlayer_enqueueShort(JNIEnv* env, jobject thiz, jshortArray samples, jint length) {
    NativePlayer* player = get_layer_from_object(env, thiz);
    if (length) {
        int16_t* samples_bytes = env->GetShortArrayElements(samples, 0);

        jint written = player->enqueue(samples_bytes, length * 2);

        // ALOGE("Wrote %d bytes to player buffer", length * 2);

        env->ReleaseShortArrayElements(samples, samples_bytes, 0);

        return written;
    } else {
        return 0;
    }
}
// -------------------------------------------------------------------------------------
