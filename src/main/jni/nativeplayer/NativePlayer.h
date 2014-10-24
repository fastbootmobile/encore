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
#ifndef SRC_MAIN_JNI_NATIVEPLAYER_NATIVEPLAYER_H_
#define SRC_MAIN_JNI_NATIVEPLAYER_NATIVEPLAYER_H_

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <cstdint>
#include <thread>
#include <list>
#include <utility>
#include <atomic>
#include <mutex>
#include "INativeSink.h"

// Maximum number of buffered samples (a second at 44100Hz)
#define BUFFER_MAX_COUNT 44100

// Minimum buffered data to start playing/resume from underrun
#define BUFFER_MIN_PLAYBACK 16384

// Enqueued buffer minimal size
#define ENQUEUED_BUFFER_SIZE 8192

class NativePlayer : public INativeSink {
 public:
    // ctor
    NativePlayer();

    // dtor
    ~NativePlayer();

    // Creates and initializes the OpenSL engine
    bool createEngine();

    // Changes the active sample rate and channels count
    bool setAudioFormat(uint32_t sample_rate, uint32_t sample_format, uint32_t channels);

    // Enqueue buffer data if possible to the player
    // @returns The number of samples written (0 means the buffer is full)
    virtual uint32_t enqueue(const void* data, uint32_t len);

    // Returns the number of samples in the buffer (not yet enqueued for playback)
    int32_t getBufferedCount() const;

    // Returns the number of stutters/dropouts (buffer underflows) since last flush (or start if
    // none)
    int32_t getUnderflowCount() const;

    // Returns the number of samples written since the last flush operation (or start if none)
    int64_t getTotalWrittenSamples() const;

    // Flush the audio output
    void flush();

    // Returns the active sample rate
    int32_t getSampleRate() const;

    // Returns the active number of channels
    int32_t getChannels() const;

 private:
    bool createAudioPlayer();
    void setPlayState(SLuint32 state);

    static void bufferPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void* context);

 private:
    SLObjectItf m_pEngineObj;
    SLEngineItf m_pEngine;
    SLObjectItf m_pOutputMixObj;

    SLObjectItf m_pPlayerObj;
    SLPlayItf m_pPlayer;
    SLVolumeItf m_pPlayerVol;
    SLAndroidSimpleBufferQueueItf m_pBufferQueue;

    std::atomic<uint32_t> m_iSampleRate;
    std::atomic<uint32_t> m_iSampleFormat;
    std::atomic<uint32_t> m_iChannels;
    std::atomic<int32_t> m_iBufferedSamples;
    std::atomic<int64_t> m_iWrittenSamples;
    std::atomic<int32_t> m_iUnderflowCount;

    std::list<std::pair<void*, uint32_t>> m_AudioBuffers;
    std::mutex m_QueueMutex;
    void* m_pPreviousBuffer;
};

#endif  // SRC_MAIN_JNI_NATIVEPLAYER_NATIVEPLAYER_H_
