# Recommended NDK version: android-r10c
NDK_TOOLCHAIN_VERSION := 4.9
APP_ABI         := armeabi,armeabi-v7a,arm64-v8a,x86,x86_64,mips64
APP_STL         := c++_shared
APP_CFLAGS      := --std=c++11
APP_PLATFORM    := android-16

#APP_OPTIM      := release
APP_OPTIM       := debug

##
# Build notes:
#  - <atomic> is broken on mips (32 bits), so we drop this platform (there's like 1 mips device
#    anyway, right?
#  - I'd love to use Clang but <atomic> linking fails
##