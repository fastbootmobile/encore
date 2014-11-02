LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

PROVIDERLIB_PATH := $(LOCAL_PATH)/../../../../../providerlib
PROVIDERLIB_JNI_PATH := $(PROVIDERLIB_PATH)/src/main/jni

# Module name and files
LOCAL_MODULE := libnativeplayerjni
LOCAL_SRC_FILES := \
    Glue.cpp \
    NativeHub.cpp \
    NativePlayer.cpp \
    jni_NativeHub.cpp \
    jni_NativePlayer.cpp \
    ../libresample/resamplesubs.cpp

LOCAL_C_INCLUDES := $(PROVIDERLIB_JNI_PATH)/protobuf/src \
    $(PROVIDERLIB_JNI_PATH)/nativesocket

# Optimization CFLAGS
LOCAL_CFLAGS := -ffast-math -O3 -funroll-loops

# Workaround for bug 61571 https://gcc.gnu.org/bugzilla/show_bug.cgi?id=61571
LOCAL_CFLAGS += -fno-strict-aliasing

LOCAL_LDLIBS := -lm -llog -lOpenSLES
LOCAL_STATIC_LIBRARIES := libnativesocket
LOCAL_SHARED_LIBRARIES := libprotobuf

include $(BUILD_SHARED_LIBRARY)
