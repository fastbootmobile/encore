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

#include "NativePlayer.h"
#include "NativeHub.h"
#include "jni_NativeHub.h"
#include "Log.h"
#include "Glue.h"
#include "../libresample/resample.h"
#include <string>
#include <utility>
#include <cmath>
#include <pthread.h>

#define LOG_TAG "OM-NativePlayer"

// -------------------------------------------------------------------------------------
NativePlayer::NativePlayer() : m_pEngineObj(nullptr), m_pEngine(nullptr),
        m_pOutputMixObj(nullptr), m_pPlayerObj(nullptr), m_pPlayer(nullptr), m_pPlayerVol(nullptr),
        m_pBufferQueue(nullptr), m_iSampleRate(-1), m_iChannels(-1), m_iSampleFormat(-1),
        m_iWrittenSamples(0), m_iUnderflowCount(0), m_pActiveBuffer(nullptr),
        m_pPlayingBuffer(nullptr), m_iActiveBufferIndex(0), m_fVolume(1.0f),
        m_pNativeHub(nullptr), m_bUseResampler(false), m_LastBuffersCheckUfCount(0) {
}
// -------------------------------------------------------------------------------------
NativePlayer::~NativePlayer() {
    // Destroy the buffer queue audio player object
    if (m_pPlayerObj) {
        (*m_pPlayerObj)->Destroy(m_pPlayerObj);
        m_pPlayerObj = nullptr;
        m_pBufferQueue = nullptr;
        m_pPlayerVol = nullptr;
    }

    // Destroy the output mix object
    if (m_pOutputMixObj) {
        (*m_pOutputMixObj)->Destroy(m_pOutputMixObj);
        m_pOutputMixObj = nullptr;
    }

    // Destroy the engine object
    if (m_pEngineObj) {
        (*m_pEngineObj)->Destroy(m_pEngineObj);
        m_pEngineObj = nullptr;
        m_pEngine = nullptr;
    }

    ALOGD("Released NativePlayer...");
}
// -------------------------------------------------------------------------------------
bool NativePlayer::createEngine() {
    SLresult result;

    ALOGD("Creating engine...");

    // Create the engine
    result = slCreateEngine(&m_pEngineObj, 0, nullptr, 0, nullptr, nullptr);
    if (result != SL_RESULT_SUCCESS) {
        ALOGE("Cannot slCreateEngine: %d", result);
        return false;
    }

    result = (*m_pEngineObj)->Realize(m_pEngineObj, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        ALOGE("Cannot realize engine: %d", result);
        return false;
    }

    // Retrieve the engine interface
    result = (*m_pEngineObj)->GetInterface(m_pEngineObj, SL_IID_ENGINE, &m_pEngine);
    if (result != SL_RESULT_SUCCESS) {
        ALOGE("Cannot get engine interface: %d", result);
        return false;
    }

    // Create the initial output mix
    const SLInterfaceID ids[] = { };
    const SLboolean req[] = { };

    result = (*m_pEngine)->CreateOutputMix(m_pEngine, &m_pOutputMixObj, 0, ids, req);
    if (result != SL_RESULT_SUCCESS) {
        ALOGE("Cannot create output mix: %d", result);
        return false;
    }

    result = (*m_pOutputMixObj)->Realize(m_pOutputMixObj, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        ALOGE("Cannot realize output mix: %d", result);
        return false;
    }

    ALOGD("Engine created, creating audio player");

    // Create the audio player with the default parameters (44100Hz, 2 channels, 16 bits)
    bool bool_result = setAudioFormat(44100, 16, 2);

    ALOGD("Initialization done!");

    // All went well!
    return bool_result;
}
// -------------------------------------------------------------------------------------
bool NativePlayer::createAudioPlayer() {
    SLresult result;

    // Configure audio source
    SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {
        SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
        2
    };

    // Convert our numerical values to OpenSL enumerations
    SLuint16 sample_format;
    SLuint32 sampling_rate;

    switch (m_iSampleFormat) {
        case 8:
            sample_format = SL_PCMSAMPLEFORMAT_FIXED_8;
            break;
        case 16:
            sample_format = SL_PCMSAMPLEFORMAT_FIXED_16;
            break;
        case 24:
            sample_format = SL_PCMSAMPLEFORMAT_FIXED_24;
            break;
        default:
            ALOGE("Invalid sample format %d!", m_iSampleFormat.load());
            return false;
    }

    sampling_rate = m_iSampleRate * 1000;

    ALOGI("Initializing NativePlayer: %d Hz, %d channels, %d bits", m_iSampleRate.load(),
            m_iChannels.load(), sample_format);

    // Assign the format structure
    SLDataFormat_PCM format_pcm;
    format_pcm.formatType = SL_DATAFORMAT_PCM;
    format_pcm.numChannels = m_iChannels;
    format_pcm.samplesPerSec = sampling_rate;
    format_pcm.bitsPerSample = sample_format;
    format_pcm.containerSize = SL_PCMSAMPLEFORMAT_FIXED_16;
    format_pcm.channelMask = (SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT);
    format_pcm.endianness = SL_BYTEORDER_LITTLEENDIAN;

    SLDataSource audio_src = {&loc_bufq, &format_pcm};

    SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, m_pOutputMixObj};
    SLDataSink audio_snk = {&loc_outmix, nullptr};

    // Create the audio player
    const SLInterfaceID ids[2] = {SL_IID_VOLUME, SL_IID_BUFFERQUEUE};
    const SLboolean req[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
    result = (*m_pEngine)->CreateAudioPlayer(m_pEngine, &m_pPlayerObj, &audio_src, &audio_snk,
            2, ids, req);
    if (result != SL_RESULT_SUCCESS) {
        ALOGE("Cannot create audio player: %d", result);
        return false;
    }

    result = (*m_pPlayerObj)->Realize(m_pPlayerObj, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        ALOGE("Cannot realize audio player: %d", result);
        return false;
    }

    result = (*m_pPlayerObj)->GetInterface(m_pPlayerObj, SL_IID_PLAY, &m_pPlayer);
    if (result != SL_RESULT_SUCCESS) {
        ALOGE("Cannot get audio player interface: %d", result);
        return false;
    }

    // Get the buffer queue interface
    result = (*m_pPlayerObj)->GetInterface(m_pPlayerObj, SL_IID_BUFFERQUEUE, &m_pBufferQueue);
    if (result != SL_RESULT_SUCCESS) {
        ALOGE("Cannot get buffer queue interface: %d", result);
        return false;
    }

    // register callback on the buffer queue
    result = (*m_pBufferQueue)->RegisterCallback(m_pBufferQueue, NativePlayer::bufferPlayerCallback,
            this);
    if (result != SL_RESULT_SUCCESS) {
        ALOGE("Cannot register buffer callback: %d", result);
        return false;
    }

    // get the volume interface
    result = (*m_pPlayerObj)->GetInterface(m_pPlayerObj, SL_IID_VOLUME, &m_pPlayerVol);
    if (result != SL_RESULT_SUCCESS) {
        ALOGE("Cannot get player volume interface: %d", result);
        return false;
    }
}
// -------------------------------------------------------------------------------------
bool NativePlayer::setAudioFormat(uint32_t sample_rate, uint32_t sample_format, uint32_t channels) {
    if (m_iChannels != channels || m_iSampleRate != sample_rate
            || m_iSampleFormat != sample_format) {
        // Safety checks
        switch (channels) {
            case 1:
            case 2:
                // Those values are supported
                break;

            default:
                ALOGE("Unsupported channel count: %d", channels);
                return false;
        }

        switch (sample_format) {
            case 8:
            case 16:
            case 24:
                // These values are supported. Realistically, no device currently support 32 bits
                // playback, and barely a few devices support 24 bits true rendering.
                break;

            default:
                ALOGE("Unsupported sample format: %d bits", sample_format);
                return false;
        }

        if (m_pActiveBuffer) {
            free(m_pActiveBuffer);
        }
        if (m_pPlayingBuffer) {
            free(m_pPlayingBuffer);
        }

        m_iBufferMinPlayback = sample_rate * channels / 4;
        m_iBufferMaxSize = sample_rate * channels;

        switch (sample_rate) {
            // Crappy quality starts here v
            case 8000:
            case 11025:
            case 12000:
            case 16000:
            case 22050:
            case 24000:
            case 32000:
            // Crappy quality ends here ^
            case 44100:
            case 48000:
                // These values are supported. Again, barely any phone supports 48KHz true rendering
                // so we don't bother allowing sampling rates > 48KHz.
                m_bUseResampler = false;
                break;

            default:
                ALOGD("Using sample rate 48KHz, resampling from %d Hz", sample_rate);
                m_bUseResampler = true;
                m_fResampleRatio = 48000.f / ((float) sample_rate);
                sample_rate = 48000;
                break;
        }

        m_LastBuffersCheckTime = std::chrono::system_clock::now();
        m_LastBuffersCheckUfCount = m_iUnderflowCount;

        m_pActiveBuffer = reinterpret_cast<uint8_t*>(malloc(m_iBufferMaxSize));
        m_pPlayingBuffer = reinterpret_cast<uint8_t*>(malloc(m_iBufferMaxSize));

        // Seems valid, let's apply them
        m_iChannels = channels;
        m_iSampleRate = sample_rate;
        m_iSampleFormat = sample_format;

        // Update the player engine
        return createAudioPlayer();
    } else {
        // Everything is set already
        return true;
    }
}
// -------------------------------------------------------------------------------------
void NativePlayer::setHostHub(NativeHub* hub) {
    m_pNativeHub = hub;
}
// -------------------------------------------------------------------------------------
uint32_t NativePlayer::enqueue(const void* data, uint32_t len) {
    std::lock_guard<std::mutex> lock(m_QueueMutex);

    SLAndroidSimpleBufferQueueState qstate;
    (*m_pBufferQueue)->GetState(m_pBufferQueue, &qstate);

    int32_t buffers_available = m_iBufferMaxSize - m_iActiveBufferIndex;

    // Start playing when we have at least a few samples
    SLuint32 playerState;
    (*m_pPlayer)->GetPlayState(m_pPlayer, &playerState);

    if (playerState != SL_PLAYSTATE_PLAYING && m_iActiveBufferIndex >= m_iBufferMinPlayback) {
        // set the player's state to playing
        // ALOGD("Bufferred %d/%d bytes, resuming", m_iActiveBufferIndex, m_iBufferMaxSize);
        setPlayState(SL_PLAYSTATE_PLAYING);
    }

    // If we have to resample, do it now on all the samples
    if (m_bUseResampler) {
        if (m_iChannels == 1) {
            len = resampleBuffersFast(m_fResampleRatio, (HWORD*) data, (HWORD*) data, len);
        } else {
            // Process each channel separately
            uint8_t* left = (uint8_t*) malloc(len);
            uint8_t* right = (uint8_t*) malloc(len);
            uint8_t* data_bytes = (uint8_t*) data;

            // Resample 24 bits to 16 bits
            // Move from 3 bits per channel to 2 bits per channel
            int left_idx = 0;
            for (int i = 0; i < len; i += 6) {
                // store back
                // [L 1] [R 1] [L 2] [R 2] [L 3] [R 3] ==> [L1][L2][L3]  [R1][R2][R3]
                //   0 ==> L[0]
                //   1 ==> R[0]
                //   2 ==> L[1]
                //      ...
                uint32_t intLeft = ((uint32_t)data_bytes[i] << 16) | ((uint32_t)data_bytes[i+2] << 8) | ((uint32_t)data_bytes[i+4]);
                intLeft = ((double) intLeft) / 16777215.0 * 65535.0;
                uint16_t castLeft = (uint16_t) intLeft;

                uint32_t intRight = ((uint32_t)data_bytes[i+1] << 16) | ((uint32_t)data_bytes[i+3] << 8) | ((uint32_t)data_bytes[i+5]);
                intRight = ((double) intRight) / 16777215.0 * 65535.0;
                uint16_t castRight = (uint16_t) intRight;

                left[left_idx] = (unsigned char)((castLeft >> 8) & 0xff);
                left[left_idx + 1] = (unsigned char)((castLeft) & 0xff);

                right[left_idx] = (unsigned char)((castRight >> 8) & 0xff);
                right[left_idx + 1] = (unsigned char)((castRight) & 0xff);

                left_idx += 2;
            }

            // Resample each channel
            uint32_t original_len = left_idx;
            len = resampleBuffersFast(m_fResampleRatio, (HWORD*) left, (HWORD*) left, original_len);
            len = resampleBuffersFast(m_fResampleRatio, (HWORD*) right, (HWORD*) right, original_len);
            ALOGE("Output resample right: %d bytes", len);

            // Combine back
            for (int i = 0; i < len; ++i) {
                data_bytes[i * 2] = left[i];
                data_bytes[i * 2 + 1] = right[i];
            }

            len = len * 2;

            free(left);
            free(right);
        }
    }

    if (buffers_available >= len) {
        // If there's room for a buffer
        if (qstate.count == 0) {
            // We have no buffer pending, enqueue it directly
            SLresult result = (*m_pBufferQueue)->Enqueue(m_pBufferQueue, data, len);
            // ALOGE("Enqueued directly %d bytes", len);
            m_iWrittenSamples += len;
            if (m_pNativeHub) {
                om_NativeHub_onAudioMirrorWritten(m_pNativeHub,
                    reinterpret_cast<const uint8_t*>(data), len);
            }
        } else {
            // Queue in our internal buffer queue, and enqueue to the sink in the buffer callback
            memcpy(&(m_pActiveBuffer[m_iActiveBufferIndex]), data, len);
            m_iActiveBufferIndex += len;
            // ALOGE("Bufferred %d/%d", m_iActiveBufferIndex, m_iBufferMaxSize);
        }

        return len;
    } else if (len > m_iBufferMaxSize) {
        ALOGE("RECEIVED BUFFER IS LARGER THAN MAX BUFFER SIZE; DROPPING %d bytes", len);
        return len;
    } else {
        // Buffers full, retry later
        return 0;
    }
}
// -------------------------------------------------------------------------------------
int32_t NativePlayer::getBufferedCount() const {
    return m_iActiveBufferIndex;
}
// -------------------------------------------------------------------------------------
int32_t NativePlayer::getUnderflowCount() const {
    return m_iUnderflowCount;
}
// -------------------------------------------------------------------------------------
int64_t NativePlayer::getTotalWrittenSamples() const {
    return m_iWrittenSamples;
}
// -------------------------------------------------------------------------------------
void NativePlayer::flush() {
    std::lock_guard<std::mutex> lock(m_QueueMutex);
    (*m_pBufferQueue)->Clear(m_pBufferQueue);
    m_iWrittenSamples = 0;
    m_iUnderflowCount = 0;
    m_LastBuffersCheckUfCount = 0;
    m_iActiveBufferIndex = 0;
    setPlayState(SL_PLAYSTATE_PAUSED);
    ALOGI("Flushed");
}
// -------------------------------------------------------------------------------------
void NativePlayer::bufferPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void* context) {
    NativePlayer* p = reinterpret_cast<NativePlayer*>(context);
    std::lock_guard<std::mutex> lock(p->m_QueueMutex);

    JNIEnv* env;
    bool release = JNI_GetEnv(&env);

    SLresult result;

    // Get queue state
    SLAndroidSimpleBufferQueueState qstate;
    (*p->m_pBufferQueue)->GetState(p->m_pBufferQueue, &qstate);

    if (qstate.count == 0) {
        // If we have audio buffers to play, play them
        if (p->m_iActiveBufferIndex > 0) {
            memcpy(p->m_pPlayingBuffer, p->m_pActiveBuffer, p->m_iActiveBufferIndex);

            result = (*p->m_pBufferQueue)->Enqueue(p->m_pBufferQueue, p->m_pPlayingBuffer,
                    p->m_iActiveBufferIndex);

            // ALOGE("Enqueued %d bytes", p->m_iActiveBufferIndex);

            if (result == SL_RESULT_SUCCESS) {
                if (p->m_pNativeHub) {
                    om_NativeHub_onAudioMirrorWritten(p->m_pNativeHub,
                        reinterpret_cast<const uint8_t*>(p->m_pPlayingBuffer),
                        p->m_iActiveBufferIndex);
                }
                p->m_iWrittenSamples += p->m_iActiveBufferIndex;
                p->m_iActiveBufferIndex = 0;
            } else {
                ALOGW("Enqueue via callback failed (%d), will retry", result);
                p->setPlayState(SL_PLAYSTATE_PAUSED);
            }
        } else {
            // No more buffers to play, we pause the playback and wait for buffers in enqueue
            p->setPlayState(SL_PLAYSTATE_PAUSED);
            p->m_iUnderflowCount++;
            ALOGW("Buffer underrun");

            // If we've got at least two underruns in less than 10 seconds, raise the min buffers
            std::chrono::system_clock::time_point now = std::chrono::system_clock::now();
            int len_ms = std::chrono::duration_cast<std::chrono::milliseconds>(now - p->m_LastBuffersCheckTime).count();
            if (p->m_iUnderflowCount - p->m_LastBuffersCheckUfCount > 2) {
                if (len_ms < 10000) {
                    // We raise the min buffer size, max 2 seconds
                    p->m_iBufferMinPlayback = std::min(p->m_iBufferMinPlayback + 2048,
                        p->m_iSampleRate * p->m_iChannels * 2);
                    p->m_iBufferMaxSize = std::max<uint32_t>(p->m_iSampleRate * 2, p->m_iBufferMinPlayback * 2);

                    free(p->m_pActiveBuffer);
                    free(p->m_pPlayingBuffer);

                    p->m_pActiveBuffer = reinterpret_cast<uint8_t*>(malloc(p->m_iBufferMaxSize));
                    p->m_pPlayingBuffer = reinterpret_cast<uint8_t*>(malloc(p->m_iBufferMaxSize));

                    ALOGD("Raising min buffer size to %d", p->m_iBufferMinPlayback);
                }
                p->m_LastBuffersCheckUfCount = p->m_iUnderflowCount.load();
                p->m_LastBuffersCheckTime = now;
            }
        }
    }

    if (release) JNI_ReleaseEnv();
}
// -------------------------------------------------------------------------------------
void NativePlayer::setPlayState(SLuint32 state) {
    SLresult result;
    result = (*m_pPlayer)->SetPlayState(m_pPlayer, state);
    if (result != SL_RESULT_SUCCESS) {
        ALOGE("Cannot set player play state: %d", result);
    }
}
// -------------------------------------------------------------------------------------
int32_t NativePlayer::getSampleRate() const {
    return m_iSampleRate;
}
// -------------------------------------------------------------------------------------
int32_t NativePlayer::getChannels() const {
    return m_iChannels;
}
// -------------------------------------------------------------------------------------
void NativePlayer::setVolume(float volume) {
    SLresult result;
    m_fVolume = volume;

    float attenuation = volume < 0.01f ? -96.0f : 20 * log10(volume);

    (*m_pPlayerVol)->SetVolumeLevel(m_pPlayerVol, (SLmillibel) (attenuation * 100));
}
// -------------------------------------------------------------------------------------

