LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# Import providerlib JNI modules
NDK_MODULE_PATH := $(LOCAL_PATH)/../../../../../providerlib/src/main/jni

$(call import-add-path, $(LOCAL_PATH)/../../../../../providerlib/src/main/jni)
$(call import-module, protobuf)
$(call import-module, nativesocket)
