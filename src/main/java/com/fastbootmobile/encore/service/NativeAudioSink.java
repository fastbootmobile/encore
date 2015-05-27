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
 * Implementation of the native (OpenSL, JNI) audio sink
 */
public class NativeAudioSink implements AudioSink {

    private NativePlayer mPlayer;

    public NativeAudioSink() {
        mPlayer = new NativePlayer();
    }

    public NativePlayer getPlayer() {
        return mPlayer;
    }

    @Override
    public void release() {
        mPlayer.shutdown();
    }

    @Override
    public boolean setup(int samplerate, int channels) {
        // Assume 16 bits depth
        return mPlayer.setAudioFormat(samplerate, channels, 16);
    }

    @Override
    public int write(byte[] frames, int numframes) {
        return mPlayer.enqueue(frames, numframes);
    }

    @Override
    public long getWrittenSamples() {
        return mPlayer.getTotalWrittenSamples();
    }

    @Override
    public int getDropouts() {
        return mPlayer.getUnderflowCount();
    }

    @Override
    public void flushSamples() {
        mPlayer.flush();
    }

    @Override
    public short[] getRmsSamples() {
        return new short[0];
    }

    @Override
    public void setPaused(boolean pause) {
        mPlayer.setPaused(pause);
    }
}
