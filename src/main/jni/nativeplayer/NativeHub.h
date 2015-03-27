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
#ifndef SRC_MAIN_JNI_NATIVEPLAYER_NATIVEHUB_H_
#define SRC_MAIN_JNI_NATIVEPLAYER_NATIVEHUB_H_

#include <list>
#include <string>
#include <unordered_map>
#include <mutex>
#include "../nativesocket/SocketCallbacks.h"
#include "../nativesocket/SocketCommon.h"
#include "../nativesocket/SocketHost.h"

class INativeSink;

class NativeHub : public SocketCallbacks {
 public:
    // ctor
    explicit NativeHub(void* userdata);

    // dtor
    ~NativeHub();

    // Initializes the default sink
    bool initialize();

    // Sets the active audio sink
    void setSink(INativeSink* sink);

    // Sets the active DSP chain order
    void setDSPChain(const std::list<std::string>& chain);

    // Creates a host socket for the provider or DSP plugin
    SocketHost* createHostSocket(const std::string& name, bool is_dsp);

    // Releases an host socket previously created and closes it
    void releaseHostSocket(const std::string& name);

    // Returns userdata pointer
    void* getUserData() const;

    // Sets ducking status
    void setDucking(bool duck);

 protected:
    SocketHost* findSocketByName(const std::string& name);
    void writeAudioToDsp(int chain_index, const uint8_t* data, const uint32_t len);
    void writeAudioToSink(const uint8_t* data, const uint32_t len);
    void writeAudioResponse(const uint32_t written);

 public:
    // SocketCallbacks implementation
    /**
     * Called when the other end requests a particular information
     * @param socket The originating socket
     * @param type The RequestType of the request
     */
    void onRequest(SocketCommon* socket, const omnimusic::Request_RequestType type);

    /**
     * When a BufferInfo message has arrived
     * @param socket The originating socket
     * @param sample The number of samples currently in the buffer
     * @param stutter The number of stutters/dropouts since the start of the session
     */
    void onBufferInfo(SocketCommon* socket, const int32_t samples, const int32_t stutter);

    /**
     * When a FormatInfo message arrived
     * @param socket The originating socket
     * @param sample_rate The sample rate, in Hz
     * @param channels The number of channels
     */
    void onFormatInfo(SocketCommon* socket, const int32_t sample_rate, const int32_t channels);

    /**
     * When audio data arrives. Data will be free()'d once the callback returns, so you must
     * manually copy it if you need it longer.
     * @param socket The originating socket
     * @param data A pointer to the audio data
     * @param len The length of the data
     */
    void onAudioData(SocketCommon* socket, const uint8_t* data, const uint32_t len);

    /**
     * When audio host socket responded after audio data was written.
     * @param socket The originating socket
     * @param written The number of samples written on the last writeAudioData call. A number of
     *                zero indicates that the host/sink buffer is full, and that you should wait
     *                until sending more data.
     */
    void onAudioResponse(SocketCommon* socket, const uint32_t written);

 private:
    INativeSink* m_pSink;
    std::list<std::string> m_DSPChain;
    std::unordered_map<std::string, SocketHost*> m_DSPSockets;
    std::unordered_map<std::string, SocketHost*> m_ProviderSockets;
    SocketCommon* m_pLastProviderSocket;
    int32_t m_iSampleRate;
    int32_t m_iChannels;
    int32_t m_iBuffersInDSP;
    void* m_pUserData;
    std::recursive_mutex m_ChainMutex;
};


#endif  // SRC_MAIN_JNI_NATIVEPLAYER_NATIVEHUB_H_
