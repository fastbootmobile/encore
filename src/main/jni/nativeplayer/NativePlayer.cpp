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
#include "Log.h"
#include <string>

#define LOG_TAG "OM-NativePlayer"

// -------------------------------------------------------------------------------------
NativePlayer::NativePlayer() : m_pEngineObj(nullptr), m_pEngine(nullptr),
        m_pOutputMixObj(nullptr), m_pPlayerObj(nullptr), m_pPreviousBuffer(nullptr),
        m_pPlayer(nullptr), m_pPlayerVol(nullptr), m_pBufferQueue(nullptr), m_iSampleRate(44100),
        m_iChannels(2), m_iSampleFormat(16) {
}
// -------------------------------------------------------------------------------------
NativePlayer::~NativePlayer() {
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
    bool bool_result = createAudioPlayer();

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
        switch (m_iChannels) {
            case 1:
            case 2:
                // Those values are supported
                break;

            default:
                ALOGE("Unsupported channel count: %d", m_iChannels.load());
                return false;
        }

        switch (m_iSampleFormat) {
            case 8:
            case 16:
            case 24:
                // These values are supported. Realistically, no device currently support 32 bits
                // playback, and barely a few devices support 24 bits true rendering.
                break;

            default:
                ALOGE("Unsupported sample format: %d bits", m_iSampleFormat.load());
                return false;
        }

        switch (m_iSampleRate) {
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
                break;

            default:
                ALOGE("Unsupported sample rate: %d Hz", m_iSampleRate.load());
                return false;
        }

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
uint32_t NativePlayer::enqueue(const void* data, uint32_t len) {
    std::lock_guard<std::mutex> lock(m_QueueMutex);
    SLAndroidSimpleBufferQueueState qstate;
    (*m_pBufferQueue)->GetState(m_pBufferQueue, &qstate);

    uint32_t buffers_available = BUFFER_MAX_COUNT - m_AudioBuffers.size();

    // Start playing when we have at least 3 buffers
    // TODO: Buffers can have a variable size. Maybe it would be better to measure total buffer
    // length ?
    SLuint32 playerState;
    (*m_pPlayer)->GetPlayState(m_pPlayer, &playerState);

    if (playerState != SL_PLAYSTATE_PLAYING && m_AudioBuffers.size() >= 3) {
        // set the player's state to playing
        setPlayState(SL_PLAYSTATE_PLAYING);
    }


    if (buffers_available > 0) {
        // If there's room for a buffer
        if (qstate.count == 0 && m_AudioBuffers.size() == 0) {
            // We have no buffer pending, enqueue it directly
            SLresult result = (*m_pBufferQueue)->Enqueue(m_pBufferQueue, data, len);
        } else {
            // Queue in our internal buffer queue, and enqueue to the sink in the buffer callback
            // We use std::string purely as a container
            void* data_copy = malloc(len);
            memcpy(data_copy, data, len);

            m_AudioBuffers.push_back(std::make_pair(data_copy, len));
        }
        return len;
    } else {
        ALOGW("Buffer queue full");
        return 0;
    }
}
// -------------------------------------------------------------------------------------
void NativePlayer::bufferPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void* context) {
    NativePlayer* p = reinterpret_cast<NativePlayer*>(context);
    std::lock_guard<std::mutex> lock(p->m_QueueMutex);

    SLresult result;

    // Check if we can free a previously played buffer
    if (p->m_pPreviousBuffer) {
        free(p->m_pPreviousBuffer);
        p->m_pPreviousBuffer = nullptr;
    }

    // Get queue state
    SLAndroidSimpleBufferQueueState qstate;
    (*p->m_pBufferQueue)->GetState(p->m_pBufferQueue, &qstate);

    if (qstate.count == 0) {
        // If we have audio buffers to play, play them
        if (p->m_AudioBuffers.size() > 0) {
            std::pair<void*, uint32_t> buffer = p->m_AudioBuffers.front();
            void* data = buffer.first;
            const uint32_t size = buffer.second;

            result = (*p->m_pBufferQueue)->Enqueue(p->m_pBufferQueue, data, size);
            if (result == SL_RESULT_SUCCESS) {
                p->m_AudioBuffers.pop_front();

                // We will free the data once it's done playing
                p->m_pPreviousBuffer = data;
            } else {
                ALOGW("Enqueue via callback failed (%d), will retry", result);
            }
        } else {
            // No more buffers to play, we pause the playback and wait for 3 buffers in enqueue
            p->setPlayState(SL_PLAYSTATE_PAUSED);
            ALOGW("Buffer underrun");
        }
    }
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
