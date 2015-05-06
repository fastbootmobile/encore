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

#ifndef SRC_MAIN_JNI_NATIVEPLAYER_JNI_NATIVEHUB_H_
#define SRC_MAIN_JNI_NATIVEPLAYER_JNI_NATIVEHUB_H_

#include <jni.h>
#include <cstdint>

class NativeHub;

// Setup JNI fields
int JNI_NativeHub_SetupFields(JNIEnv* env);

// -----------------------------------------
// Called from Java to native code
// -----------------------------------------

// NativeHub.initialize()
jboolean om_NativeHub_initialize(JNIEnv* env, jobject thiz);

// NativeHub.shutdown()
void om_NativeHub_shutdown(JNIEnv* env, jobject thiz);

// NativeHub.setDSPChain(String[] chain) ==> NativeHub::setDSPChain(list<string> chain)
void om_NativeHub_setDSPChain(JNIEnv* env, jobject thiz, jobjectArray chain);

// NativeHub.createHostSocket(String name, boolean isDsp) ==> NativeHub::createHostSocket
jboolean om_NativeHub_createHostSocket(JNIEnv* env, jobject thiz, jstring name, jboolean is_dsp);

// NativeHub.releaseHostSocket(String name) ==> NativeHub::releaseHostSocket
void om_NativeHub_releaseHostSocket(JNIEnv* env, jobject thiz, jstring name);

// NativeHub.setSinkPointer(long handle) ==> NativeHub::setSink(INativeSink* sink)
void om_NativeHub_setSinkPointer(JNIEnv* env, jobject thiz, jlong handle);

// NativeHub.setDucking(boolean duck) ==> NativeHub::setDucking(bool duck)
void om_NativeHub_setDucking(JNIEnv* env, jobject thiz, jboolean duck);

// -----------------------------------------
// Called from native code to Java
// -----------------------------------------

void om_NativeHub_onAudioMirrorWritten(NativeHub* hub, const uint8_t* data, jint len,
        jint sampleRate, jint channels);

#endif  // SRC_MAIN_JNI_NATIVEPLAYER_JNI_NATIVEHUB_H_
