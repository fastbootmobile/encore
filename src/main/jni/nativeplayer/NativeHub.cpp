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
#include <algorithm>

#define LOG_TAG "NativeHub"

// -------------------------------------------------------------------------------------
NativeHub::NativeHub() : m_pSink(nullptr), m_pLastProviderSocket(nullptr), m_iSampleRate(44100),
        m_iChannels(2) {
}
// -------------------------------------------------------------------------------------
NativeHub::~NativeHub() {
}
// -------------------------------------------------------------------------------------
void NativeHub::setSink(INativeSink* sink) {
    m_pSink = sink;
    m_pSink->setAudioFormat(m_iSampleRate, 16, m_iChannels);
}
// -------------------------------------------------------------------------------------
void NativeHub::setDSPChain(const std::list<std::string>& chain) {
    m_DSPChain = chain;
}
// -------------------------------------------------------------------------------------
SocketHost* NativeHub::createHostSocket(const std::string& name, bool is_dsp) {
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
SocketHost* NativeHub::findSocketByName(const std::string& name) {
    SocketHost* host = m_ProviderSockets[name];
    if (!host) {
        host = m_DSPSockets[name];
    }
}
// -------------------------------------------------------------------------------------
void NativeHub::writeAudioToDsp(int chain_index, const uint8_t* data, const uint32_t len) {
    auto iter = m_DSPChain.begin();
    std::advance(iter, chain_index);
    std::string name = (*iter);

    SocketHost* next_socket = m_DSPSockets[name];
    next_socket->writeAudioData(data, len, false);
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
void NativeHub::onFormatInfo(SocketCommon* socket, const int32_t sample_rate, const int32_t channels) {
    m_iSampleRate = sample_rate;
    m_iChannels = channels;

    if (m_pSink) {
        m_pSink->setAudioFormat(sample_rate, 16, channels);
    }
}
// -------------------------------------------------------------------------------------
void NativeHub::onAudioData(SocketCommon* socket, const uint8_t* data, const uint32_t len) {
    bool is_dsp = (m_ProviderSockets[socket->getSocketName()] == nullptr);

    ALOGE("onAudioData(%d bytes)", len);

    if (len > 0) {
        if (is_dsp) {
            // Audio from a DSP plugin, route it to the next element (or to the sink if no more)
            int index = std::distance(m_DSPChain.begin(),
                    std::find(m_DSPChain.begin(), m_DSPChain.end(), socket->getSocketName()));
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
        }
        writeAudioResponse(0);
    }
}
// -------------------------------------------------------------------------------------
void NativeHub::onAudioResponse(SocketCommon* socket, const uint32_t written) {
}
// -------------------------------------------------------------------------------------
