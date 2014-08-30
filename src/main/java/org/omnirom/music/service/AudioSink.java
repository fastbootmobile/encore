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
    public int write(byte[] frames, int numframes);

    /**
     * Returns the number of samples written since the last call to flushSamples() has been made
     * (or since the first written sample).
     * @return A number of short samples written
     */
    public int getWrittenSamples();

    /**
     * Clears the pending audio data and reset the written samples counter
     */
    public void flushSamples();

    /**
     * Returns a buffer of samples containing the most recent data that has been written to (drawable
     * by) the sink to calculate the current RMS audio level.
     * @return A short array of samples
     */
    public short[] getRmsSamples();
}
