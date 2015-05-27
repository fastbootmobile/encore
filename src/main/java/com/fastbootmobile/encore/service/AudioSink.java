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

package com.fastbootmobile.encore.service;

/**
 * Interface that allows the app to sink the audio data to various destinations
 */
public interface AudioSink {

    /**
     * Releases the audio sink and all its allocated resources. The audio sink may not be reused
     * then, until a new one is allocated.
     */
    void release();

    /**
     * Setups the audio sink with the provided sample rate and channels
     * @param samplerate The sample rate, in number of samples per second
     * @param channels The number of channels, generally 1 for mono and 2 for stereo
     * @return true if everything went fine
     */
    boolean setup(int samplerate, int channels);

    /**
     * Writes audio frames to this sink
     * @param frames Frames to write
     * @param numframes Number of frames
     * @return The number of bytes actually written
     */
    int write(byte[] frames, int numframes);

    /**
     * Returns the number of samples written since the last call to flushSamples() has been made
     * (or since the first written sample).
     * @return A number of short samples written
     */
    long getWrittenSamples();

    /**
     * Returns the number of dropouts/stutters (buffer underflow) that occurred since the last flush
     * @return Number of dropouts
     */
    int getDropouts();

    /**
     * Clears the pending audio data and reset the written samples counter
     */
    void flushSamples();

    /**
     * Returns a buffer of samples containing the most recent data that has been written to (drawable
     * by) the sink to calculate the current RMS audio level.
     * @return A short array of samples
     */
    short[] getRmsSamples();

    /**
     * Sets whether or not the playback should be paused
     * @param pause true to pause, false to resume
     */
    void setPaused(boolean pause);
}
