package com.fastbootmobile.encore.providers.bassboost;

/**
 * Created by Guigui on 25/07/2014.
 */
public class BiQuadFilter {
    private static long toFixedPoint(double in) {
        return (long) (0.5 + in * (1L << 32));
    }

    protected int mX1, mX2;
    protected int mY1, mY2;
    protected long mB0, mB1, mB2, mA1, mA2;
    protected long mB0dif, mB1dif, mB2dif, mA1dif, mA2dif;
    protected int mInterpolationSteps;

    public BiQuadFilter() {
        reset();
        setCoefficients(0, 1, 0, 0, 1, 0, 0);
    }

    public static short clamp16(int sample) {
        if (((sample >> 15) ^ (sample >> 31)) != 0) {
            sample = 0x7FFF ^ (sample >> 31);
        }
        return (short) sample;
    }


    protected void setCoefficients(int steps, double a0, double a1, double a2, double b0, double b1, double b2) {
        long A1 = -toFixedPoint(a1 / a0);
        long A2 = -toFixedPoint(a2 / a0);
        long B0 = toFixedPoint(b0 / a0);
        long B1 = toFixedPoint(b1 / a0);
        long B2 = toFixedPoint(b2 / a0);

        if (steps == 0) {
            mA1 = A1;
            mA2 = A2;
            mB0 = B0;
            mB1 = B1;
            mB2 = B2;
            mInterpolationSteps = 0;
        } else {
            mA1dif = (A1 - mA1) / steps;
            mA2dif = (A2 - mA2) / steps;
            mB0dif = (B0 - mB0) / steps;
            mB1dif = (B1 - mB1) / steps;
            mB2dif = (B2 - mB2) / steps;
            mInterpolationSteps = steps;
        }
    }

    public void setHighShelf(int steps, double center_frequency, double sampling_frequency, double gainDb, double slope, double overallGainDb) {
        double w0 = 2.0 * Math.PI * center_frequency / sampling_frequency;
        double A = Math.pow(10.0, gainDb / 40.0);
        double alpha = Math.sin(w0) / 2.0 * Math.sqrt((A + 1.0 / A) * (1.0 / slope - 1.0) + 2.0);

        double b0 = A * ((A + 1.0) + (A - 1.0) * Math.cos(w0) + 2.0 * Math.sqrt(A) * alpha);
        double b1 = -2.0 * A * ((A - 1.0) + (A + 1.0) * Math.cos(w0));
        double b2 = A * ((A + 1.0) + (A - 1.0) * Math.cos(w0) - 2.0 * Math.sqrt(A) * alpha);
        double a0 = (A + 1.0) - (A - 1.0) * Math.cos(w0) + 2.0 * Math.sqrt(A) * alpha;
        double a1 = 2.0 * ((A - 1) - (A + 1.0) * Math.cos(w0));
        double a2 = (A + 1) - (A - 1.0) * Math.cos(w0) - 2.0 * Math.sqrt(A) * alpha;

        double overallGain = Math.pow(10.0, overallGainDb / 20.0);
        b0 *= overallGain;
        b1 *= overallGain;
        b2 *= overallGain;

        setCoefficients(steps, a0, a1, a2, b0, b1, b2);
    }

    public void setBandPass(int steps, double center_frequency, double sampling_frequency, double resonance) {
        double w0 = 2.0 * Math.PI * center_frequency / sampling_frequency;
        double alpha = Math.sin(w0) / (2.0 * resonance);

        double b0 = Math.sin(w0) / 2.0;
        double b1 = 0;
        double b2 = -Math.sin(w0) / 2.0;
        double a0 = 1.0 + alpha;
        double a1 = -2.0 * Math.cos(w0);
        double a2 = 1.0 - alpha;

        setCoefficients(steps, a0, a1, a2, b0, b1, b2);
    }

    public void setHighPass(int steps, double center_frequency, double sampling_frequency, double resonance) {
        double w0 = 2.0 * Math.PI * center_frequency / sampling_frequency;
        double alpha = Math.sin(w0) / (2.0 * resonance);

        double b0 = (1.0 + Math.cos(w0)) / 2.0;
        double b1 = -(1.0 + Math.cos(w0));
        double b2 = (1.0 + Math.cos(w0)) / 2.0;
        double a0 = 1.0 + alpha;
        double a1 = -2.0 * Math.cos(w0);
        double a2 = 1.0 - alpha;

        setCoefficients(steps, a0, a1, a2, b0, b1, b2);
    }

    public void setLowPass(int steps, double center_frequency, double sampling_frequency, double resonance) {
        double w0 = 2.0 * Math.PI * center_frequency / sampling_frequency;
        double alpha = Math.sin(w0) / (2.0 * resonance);

        double b0 = (1.0 - Math.cos(w0)) / 2.0;
        double b1 = 1.0 - Math.cos(w0);
        double b2 = (1.0 - Math.cos(w0)) / 2.0;
        double a0 = 1.0 + alpha;
        double a1 = -2.0 * Math.cos(w0);
        double a2 = 1.0 - alpha;

        setCoefficients(steps, a0, a1, a2, b0, b1, b2);
    }

    public int process(int x0) {
        long y0 = mB0 * x0
                + mB1 * mX1
                + mB2 * mX2
                + mA1 * mY1
                + mA2 * mY2;
        y0 >>= 32;

        mY2 = mY1;
        mY1 = (int) y0;

        mX2 = mX1;
        mX1 = x0;

        /* Interpolate biquad parameters */
        if (mInterpolationSteps != 0) {
            mInterpolationSteps--;
            mB0 += mB0dif;
            mB1 += mB1dif;
            mB2 += mB2dif;
            mA1 += mA1dif;
            mA2 += mA2dif;
        }

        return (int) y0;
    }

    public void reset() {
        mInterpolationSteps = 0;
        mA1 = 0;
        mA2 = 0;
        mB0 = 0;
        mB1 = 0;
        mB2 = 0;
        mX1 = 0;
        mX2 = 0;
        mY1 = 0;
        mY2 = 0;
    }


}
