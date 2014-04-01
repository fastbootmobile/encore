package org.omnirom.music.service;

/**
 * Interface that allows the app to sink the audio data to various destinations
 */
public interface AudioSink {

    /**
     * Releases the audio sink and all its allocated resources. The audio sink may not be reused
     * then, until a new one is allocated.
     */
    public void release();

    /**
     * Setups the audio sink with the provided sample rate and channels
     * @param samplerate The sample rate, in number of samples per second
     * @param channels The number of channels, generally 1 for mono and 2 for stereo
     * @return true if everything went fine
     */
    public boolean setup(int samplerate, int channels);

    /**
     * Writes audio frames to this sink
     * @param frames Frames to write
     * @param numframes Number of frames
     * @return The number of bytes actually written
     */
    public int write(short[] frames, int numframes);
}
