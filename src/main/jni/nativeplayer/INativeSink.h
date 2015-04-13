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
#ifndef SRC_MAIN_JNI_NATIVEPLAYER_INATIVESINK_H_
#define SRC_MAIN_JNI_NATIVEPLAYER_INATIVESINK_H_

class NativeHub;

class INativeSink {
 public:
    // Set native hub host
    virtual void setHostHub(NativeHub* hub) = 0;

    // Set audio format
    virtual bool setAudioFormat(uint32_t sample_rate, uint32_t sample_format,
            uint32_t channels) = 0;

    // Enqueue buffer data if possible to the player
    // @returns The number of samples written (0 means the buffer is full)
    virtual uint32_t enqueue(const void* data, uint32_t len) = 0;

    // Returns the number of samples in the buffer
    virtual int32_t getBufferedCount() const = 0;

    // Returns the number of free samples in the buffer
    virtual int32_t getFreeBuffersCount() const = 0;

    // Returns the total number of written samples
    virtual int64_t getTotalWrittenSamples() const = 0;

    // Returns the number of underflow/dropouts since the last flush (or start)
    virtual int32_t getUnderflowCount() const = 0;

    // Flush the buffers and output
    virtual void flush() = 0;

    // Returns the active sample rate
    virtual int32_t getSampleRate() const = 0;

    // Returns the active number of channels
    virtual int32_t getChannels() const = 0;

    // Sets the active volume
    virtual void setVolume(float volume) = 0;

    // Pauses or resume the playback
    virtual void setPaused(bool pause) = 0;
};


#endif  // SRC_MAIN_JNI_NATIVEPLAYER_INATIVESINK_H_
