package org.apache.iotdb.tsfile.encoding;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.util.*;

public class Utils {
    public static double[] convertListToArray(List<Double> list) {
        double[] array = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i); // Unboxing the Double to double
        }
        return array;
    }


    public static double[] loadSquareWave(int size, int period, double scale) {
        double[] ts = new double[size];
        double trend_now, seasonal_now, residual_now, value;

        for (int time = 0; time < size; time++) {
            trend_now = -(double) time * 0.003;
            seasonal_now = (time % period) < (period / 2) ? scale : -scale;
            residual_now = 0;
            // data
            value = trend_now + seasonal_now + residual_now;
            BigDecimal b = new BigDecimal(value);
            ts[time] = b.setScale(3, BigDecimal.ROUND_HALF_UP).doubleValue();
        }
        return ts;
    }

    public static double[] loadTimeSeriesData(String filename, int dataLen) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(filename));
        ArrayList<Double> tsList = new ArrayList<>();

        sc.nextLine();  // skip table header
        for (int k = dataLen; k > 0 && sc.hasNextLine(); --k) {  // the size of td_clean is dataLen
            String[] line_str = sc.nextLine().split(",");
            // ts
            double v = Double.parseDouble(line_str[1]);
            tsList.add(v);
            // standardize_prepare
        }
        // standardize
        return convertListToArray(tsList);
    }

    public static int getScale(double[] ts) {
        int cntNum = 100;
        int[] pointNumberArray = new int[cntNum];
        int pointNumber;
        double decimal;

        for (int idx = 0; idx < cntNum; idx++) {
            double value = ts[idx];
            // pointNumber count
            for (pointNumber = 0; pointNumber < 64; pointNumber++) {
                decimal = value - (long) value;
                if (decimal < 0.00001 || decimal > 0.99999) {
                    break;
                }
                value = value * 10.;
            }
            pointNumberArray[idx] = pointNumber;
        }
        Arrays.sort(pointNumberArray);

        return pointNumberArray[cntNum / 2];
    }

    private static int lastPowerOfTwo(int n) {
        // Find the closest power of 2 that is <= n
        int pos = 1;
        while (pos < n) {
            pos *= 2;
        }
        return pos / 2;
    }


    public static int getPeriod(double[] original) {
        // Find the closest power of 2 that is <= n
        int inputLength = lastPowerOfTwo(original.length);
        double[] ts = new double[inputLength];
        System.arraycopy(original, 0, ts, 0, inputLength);

        // Create the transformer for FFT.
        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] fft = transformer.transform(ts, TransformType.FORWARD);

        double maxAmplitude = 0;
        int periodIndex = -1;

        // Start at index 1 to skip the DC component (index 0).
        for (int i = fft.length / 10000; i < fft.length / 2 + 1; i++) {   // Only need to iterate up to Nyquist frequency
            double amplitude = fft[i].abs();
            if (amplitude > maxAmplitude) {
                maxAmplitude = amplitude;
                periodIndex = i;
            }
        }

        // Calculate the actual period from the peak index
        return fft.length / periodIndex;
    }


    public static boolean checkCorrectness(double[] original, double[] decoded) {
        int cnt = 0;
        for (int idx = 0; idx < original.length; ++idx) {
            if (original[idx] - decoded[idx] > 1e-2) {
                cnt++;
                System.out.println("Error: " + idx + "-th Number is not equal. The original value: " + original[idx] + ". The decoded value: " + decoded[idx]);
            }
        }
        return cnt == 0;
    }

    public static void main(String[] args) {
        double[] ts = loadSquareWave(200, 20, 100.0);
        for (double value : ts) {
            System.out.print(value + " ");
        }
        System.out.println();
    }
}
