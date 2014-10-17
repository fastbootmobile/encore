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
#ifndef SRC_MAIN_JNI_NATIVEPLAYER_GLUE_H_
#define SRC_MAIN_JNI_NATIVEPLAYER_GLUE_H_

#include <jni.h>
#include "Log.h"
#include <cassert>

#define NELEM(a)  (sizeof(a) / sizeof(a)[0])

// Called when JNI initializes
jint JNI_OnLoad(JavaVM* vm, void* reserved);

// Called to register NativePlayer JNI methods
int JNI_RegisterNativePlayer(JNIEnv* env);

// Called to register NativeHub JNI methods
int JNI_RegisterNativeHub(JNIEnv* env);

// Throw an exception with the specified class and an optional message.
int JNI_ThrowException(JNIEnv* env, const char* className, const char* msg);

// Register native JNI-callable methods.
// "className" looks like "java/lang/String".
int JNI_RegisterNativeMethods(JNIEnv* env,
                             const char* className,
                             const JNINativeMethod* gMethods,
                             int numMethods);

// Returns a thread-safe version of the JNIEnv pointer (attached to the current thread). If the
// function returns "true", JNI_ReleaseEnv must be called when you're done using the environment
// pointer.
bool JNI_GetEnv(JNIEnv** env);

// When JNI_GetEnv is called and returns true, this method must be called to detach the JNIEnv
// from the current thread.
void JNI_ReleaseEnv();

#endif  // SRC_MAIN_JNI_NATIVEPLAYER_GLUE_H_
