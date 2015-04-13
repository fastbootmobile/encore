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

#ifndef SRC_MAIN_JNI_NATIVEPLAYER_JNI_NATIVEPLAYER_H_
#define SRC_MAIN_JNI_NATIVEPLAYER_JNI_NATIVEPLAYER_H_

#include <jni.h>

class NativePlayer;

// Setup JNI fields
int JNI_NativePlayer_SetupFields(JNIEnv* env);

// -----------------------------------------
// Called from Java to native code
// -----------------------------------------

// NativePlayer.nativeInitialize ==> NativePlayer::createEngine
jboolean om_NativePlayer_initialize(JNIEnv* env, jobject thiz);

// NativePlayer.nativeShutdown ==> NativePlayer::~NativePlayer
void om_NativePlayer_shutdown(JNIEnv* env, jobject thiz);

// NativePlayer.nativeSetAudioFormat ==> NativePlayer::setAudioFormat
jboolean om_NativePlayer_setAudioFormat(JNIEnv* env, jobject thiz, jint sample_rate, jint channels,
                                            jint depth);

// NativePlayer.nativeEnqueue ==> NativePlayer::enqueue
jint om_NativePlayer_enqueue(JNIEnv* env, jobject thiz, jbyteArray samples, jint length);
jint om_NativePlayer_enqueueShort(JNIEnv* env, jobject thiz, jshortArray samples, jint length);

// NativePlayer.nativeGetBufferedCount ==> NativePlayer::getBufferedCount
jint om_NativePlayer_getBufferedCount(JNIEnv* env, jobject thiz);

// NativePlayer.nativeGetTotalWrittenSamples ==> NativePlayer::getTotalWrittenSamples
jlong om_NativePlayer_getTotalWrittenSamples(JNIEnv* env, jobject thiz);

// NativePlayer.nativeGetUnderflowCount ==> NativePlayer::getUnderflowCount
jint om_NativePlayer_getUnderflowCount(JNIEnv* env, jobject thiz);

// NativePlayer.nativeFlush ==> NativePlayer::flush
void om_NativePlayer_flush(JNIEnv* env, jobject thiz);

// NativePlayer.nativeSetPaused ==> NativePlayer::setPaused
void om_NativePlayer_setPaused(JNIEnv* env, jobject thiz, jboolean pause);

#endif  // SRC_MAIN_JNI_NATIVEPLAYER_JNI_NATIVEPLAYER_H_
