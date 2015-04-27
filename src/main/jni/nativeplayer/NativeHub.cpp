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

#include "NativeHub.h"
#include "INativeSink.h"
#include "Log.h"
#include "jni_NativeHub.h"
#include <algorithm>
#include <string>
#include <list>

#define LOG_TAG "NativeHub"

// -------------------------------------------------------------------------------------
NativeHub::NativeHub(void* userdata) : m_pSink(nullptr), m_pLastProviderSocket(nullptr),
        m_iSampleRate(44100), m_iChannels(2), m_pUserData(userdata), m_iBuffersInDSP(0) {
}
// -------------------------------------------------------------------------------------
NativeHub::~NativeHub() {
    int remaining_dsp = m_DSPSockets.size();
    int remaining_providers = m_ProviderSockets.size();

    ALOGD("NativeHub releasing (%d dsp sockets left, %d provider sockets left)",
            remaining_dsp, remaining_providers);

    std::lock_guard<std::recursive_mutex> lock(m_ChainMutex);
    for (auto it = m_DSPSockets.begin(); it != m_DSPSockets.end(); ++it) {
        delete it->second;
    }

    m_DSPSockets.clear();

    for (auto it = m_ProviderSockets.begin(); it != m_ProviderSockets.end(); ++it) {
        delete it->second;
    }

    m_ProviderSockets.clear();
}
// -------------------------------------------------------------------------------------
void NativeHub::setSink(INativeSink* sink) {
    m_pSink = sink;
    m_pSink->setAudioFormat(m_iSampleRate, 16, m_iChannels);
    m_pSink->setHostHub(this);
}
// -------------------------------------------------------------------------------------
void NativeHub::setDSPChain(const std::list<std::string>& chain) {
    std::lock_guard<std::recursive_mutex> lock(m_ChainMutex);
    m_DSPChain = chain;
}
// -------------------------------------------------------------------------------------
SocketHost* NativeHub::createHostSocket(const std::string& name, bool is_dsp) {
    std::lock_guard<std::recursive_mutex> lock(m_ChainMutex);

    SocketHost* host = new SocketHost(name);
    if (!host->initialize()) {
        ALOGE("Unable to initialize host socket %s", name.c_str());
        delete host;
        return nullptr;
    }

    host->setCallback(this);

    if (is_dsp) {
        m_DSPSockets[name] = host;
    } else {
        m_ProviderSockets[name] = host;
    }

    return host;
}
// -------------------------------------------------------------------------------------
void NativeHub::releaseHostSocket(const std::string& name) {
    std::lock_guard<std::recursive_mutex> lock(m_ChainMutex);
    bool is_dsp = false;
    SocketHost* host = m_ProviderSockets[name];

    if (!host) {
        host = m_DSPSockets[name];
        is_dsp = true;
    }

    if (host) {
        delete host;
        if (is_dsp) {
            m_DSPSockets.erase(name);
        } else {
            m_ProviderSockets.erase(name);
        }
    } else {
        ALOGE("Cannot release host socket '%s' as it doesn't seem allocated", name.c_str());
    }
}
// -------------------------------------------------------------------------------------
void* NativeHub::getUserData() const {
    return m_pUserData;
}
// -------------------------------------------------------------------------------------
void NativeHub::setDucking(bool duck) {
    if (m_pSink) {
        m_pSink->setVolume(duck ? 0.5f : 1.0f);
    }
}
// -------------------------------------------------------------------------------------
SocketHost* NativeHub::findSocketByName(const std::string& name) {
    SocketHost* host = m_ProviderSockets[name];
    if (!host) {
        host = m_DSPSockets[name];
    }
}
// -------------------------------------------------------------------------------------
void NativeHub::writeAudioToDsp(int chain_index, const uint8_t* data, const uint32_t len) {
    std::lock_guard<std::recursive_mutex> lock(m_ChainMutex);

    // Before even writing to DSP, we check if the sink has free space so that we don't
    // process it worthlessly (and avoid glitches because Biquad filters dislike having
    // unwanted data).
    uint32_t free_buffers = (uint32_t) m_pSink->getFreeBuffersCount() - (uint32_t) m_iBuffersInDSP;
    if (m_pSink && free_buffers < len) {
        writeAudioResponse(0);
    } else {
        bool success = false;
        auto iter = m_DSPChain.begin();
        std::advance(iter, chain_index);

        while (iter != m_DSPChain.end() && !success) {
            std::string name = (*iter);
            SocketHost* next_socket = m_DSPSockets[name];

            if (next_socket->writeAudioData(data, len, false) == -1) {
                // Some error occurred while writing to this DSP, forward to the next valid one
                ++iter;
            } else {
                success = true;
                m_iBuffersInDSP += len;
            }
        }

        // If we're weren't successful at feeding a DSP plugin, feed the sink directly
        if (!success) {
            writeAudioToSink(data, len);
        }
    }
}
// -------------------------------------------------------------------------------------
void NativeHub::writeAudioToSink(const uint8_t* data, const uint32_t len) {
    if (m_pSink) {
        writeAudioResponse(m_pSink->enqueue(data, len));
    } else {
        writeAudioResponse(0);
    }
}
// -------------------------------------------------------------------------------------
void NativeHub::writeAudioResponse(const uint32_t written) {
    if (m_pLastProviderSocket) {
        m_pLastProviderSocket->writeAudioResponse(written);
    } else {
        ALOGE("CANNOT WRITE AUDIO RESPONSE: NO LAST PROVIDER SOCKET!!!!");
    }
}
// -------------------------------------------------------------------------------------
void NativeHub::onRequest(SocketCommon* socket, const omnimusic::Request_RequestType type) {
    switch (type) {
    case omnimusic::Request_RequestType_BUFFER_INFO:
        if (m_pSink) {
            socket->writeBufferInfo(m_pSink->getBufferedCount(), m_pSink->getUnderflowCount());
        } else {
            socket->writeBufferInfo(0, 0);
        }
        break;

    case omnimusic::Request_RequestType_FORMAT_INFO:
        if (m_pSink) {
            socket->writeFormatInfo(m_pSink->getChannels(), m_pSink->getSampleRate());
        } else {
            // Default values
            socket->writeFormatInfo(m_iChannels, m_iSampleRate);
        }
        break;
    }
}
// -------------------------------------------------------------------------------------
void NativeHub::onBufferInfo(SocketCommon* socket, const int32_t samples, const int32_t stutter) {
}
// -------------------------------------------------------------------------------------
void NativeHub::onFormatInfo(SocketCommon* socket, const int32_t sample_rate,
        const int32_t channels) {
    m_iSampleRate = sample_rate;
    m_iChannels = channels;

    if (m_pSink) {
        m_pSink->setAudioFormat(sample_rate, 16, channels);
    }

    // Notify DSP plugins of format info
    for (auto it = m_DSPChain.begin(); it != m_DSPChain.end(); ++it) {
        SocketCommon* socket = m_DSPSockets[*it];
        if (m_pSink) {
            socket->writeFormatInfo(m_pSink->getChannels(), m_pSink->getSampleRate());
        } else {
            // Default values
            socket->writeFormatInfo(m_iChannels, m_iSampleRate);
        }
    }
}
// -------------------------------------------------------------------------------------
void NativeHub::onAudioData(SocketCommon* socket, const uint8_t* data, const uint32_t len) {
    std::lock_guard<std::recursive_mutex> lock(m_ChainMutex);
    bool is_dsp = (m_ProviderSockets[socket->getSocketName()] == nullptr);

    if (is_dsp && m_DSPSockets[socket->getSocketName()] == nullptr) {
        // Drop sample, don't know where it comes from
        return;
    }

    if (len > 0) {
        if (is_dsp) {
            // Audio from a DSP plugin, route it to the next element (or to the sink if no more)
            int index = std::distance(m_DSPChain.begin(),
                    std::find(m_DSPChain.begin(), m_DSPChain.end(), socket->getSocketName()));
            m_iBuffersInDSP -= len;
            if (index < m_DSPChain.size() - 1) {
                writeAudioToDsp(index + 1, data, len);
            } else {
                // End of the chain, feed to sink
                writeAudioToSink(data, len);
            }
        } else {
            m_pLastProviderSocket = socket;

            // Audio from provider, route it to the first plugin in the DSP chain (or sink if none)
            if (m_DSPChain.size() > 0) {
                writeAudioToDsp(0, data, len);
            } else {
                // No DSP plugins, feed to sink
                writeAudioToSink(data, len);
            }
        }
    } else if (!is_dsp) {
        m_pLastProviderSocket = socket;

        // Length = 0 means flush!
        if (m_pSink) {
            m_pSink->flush();
            m_iBuffersInDSP = 0;
        }
        writeAudioResponse(0);
    }
}
// -------------------------------------------------------------------------------------
void NativeHub::onAudioResponse(SocketCommon* socket, const uint32_t written) {
}
// -------------------------------------------------------------------------------------
