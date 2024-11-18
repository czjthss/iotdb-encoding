package org.apache.iotdb.tsfile.encoding.encoder;

import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.utils.BytesUtils;
import org.apache.iotdb.tsfile.utils.ReadWriteIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;

public abstract class STD5Encoder extends Encoder {
    private static final Logger logger = LoggerFactory.getLogger(STD5Encoder.class);
    protected ByteArrayOutputStream out;

    protected int blockSize;

    protected int period;
    protected byte[] encodingResidualBuffer;
    protected int encodingResidualBufferLength;
    protected byte[] encodingSeasonalBuffer;
    protected int seasonalWidth;

    protected int[] residualLengths;
    protected byte[] encodingResidualLengths;
    protected int residualLengthsWidth;

    protected int writeIndex = -1;

    protected static String TEST_PATH = "/Users/chenzijie/Documents/GitHub/data/output/compression/seasonal.txt";

    /**
     * record test message to file
     */
    public static void write2file(String info) throws Exception {
        FileWriter fileWritter = new FileWriter(TEST_PATH, true);
        BufferedWriter bw = new BufferedWriter(fileWritter);
        bw.write(info);
        bw.close();
    }

    /**
     * constructor of STDEncoder.
     */
    protected STD5Encoder() {
        super(TSEncoding.STD);
        // the number how many numbers to be packed into a block.
        blockSize = TSFileDescriptor.getInstance().getConfig().getStdBlockSize();
        period = TSFileDescriptor.getInstance().getConfig().getPeriodLength();
    }

    protected abstract void calculateSeasonalComponent();

    protected abstract void calculateBitWidthsForSeasonalBuffer();

    private int getValueWidth(int v) {
        return 32 - Integer.numberOfLeadingZeros(v);
    }

    private void calculateBitWidthsForResidualLengthBuffer() {
        residualLengthsWidth = 0;
        for (int i = 0; i < writeIndex; i++) {
            residualLengthsWidth = Math.max(residualLengthsWidth, getValueWidth(residualLengths[i]));
        }
    }

    protected abstract void zigzagEncoding();

    protected abstract void writeFirstValue() throws IOException;

    protected abstract void reset();

    /**
     * calling this method to flush all values which haven't encoded to result byte array.
     */
    @Override
    public void flush(ByteArrayOutputStream out) {
        try {
            flushBlockBuffer(out);
        } catch (IOException e) {
            logger.error("flush data to stream failed!", e);
        }
    }

    /**
     * encoding seasonal components
     */
    private void writeSeasonalWithMinWidth() {
        for (int i = 0; i < period; i++) {
            writeSeasonalToBytes(i);
        }
        int encodingLength = (int) Math.ceil((period * seasonalWidth) / 8.0);
        out.write(encodingSeasonalBuffer, 0, encodingLength);
    }

    /**
     * encoding residual length
     */
    private void writeResidualLengthWithMinWidth() {

        for (int i = 0; i < writeIndex; i++) {
            BytesUtils.longToBytes(residualLengths[i], encodingResidualLengths, residualLengthsWidth * i, residualLengthsWidth);
        }
        int encodingLength = (int) Math.ceil((writeIndex * residualLengthsWidth) / 8.0);
        out.write(encodingResidualLengths, 0, encodingLength);
    }

    /**
     * encoding residual components
     */
    private void writeResidualWithVarWidth() {
        this.out.write(encodingResidualBuffer, 0, (int) Math.ceil(encodingResidualBufferLength / 8.0));
    }

    protected abstract void writeSeasonalToBytes(int i);

    /**
     * encoding residual components
     */
    protected abstract void bitVarIntEncoding();

    private long calculateSeasonalComponentTime = 0;
    private long zigzagEncodingTime = 0;
    private long bitVarIntEncodingTime = 0;
    private long encodingResidualTime = 0;
    private long encodingResidualLengthTime = 0;

    private void flushBlockBuffer(ByteArrayOutputStream out) throws IOException {
        if (writeIndex == -1) {
            return;
        }
        this.out = out;

        long t1 = System.nanoTime();

        calculateSeasonalComponent();

        long t2 = System.nanoTime();

        zigzagEncoding();

        long t3 = System.nanoTime();

        calculateBitWidthsForSeasonalBuffer();

        // seasonal components
        writeSeasonalWithMinWidth();

        long t4 = System.nanoTime();

        // residual components
        bitVarIntEncoding();

        writeResidualWithVarWidth();

        // residual components length

        long t5 = System.nanoTime();

        calculateBitWidthsForResidualLengthBuffer();

        writeResidualLengthWithMinWidth();

        long t6 = System.nanoTime();

        ReadWriteIOUtils.write(period, this.out);
        ReadWriteIOUtils.write(writeIndex, this.out);
        ReadWriteIOUtils.write(seasonalWidth, this.out);
        ReadWriteIOUtils.write(residualLengthsWidth, this.out);
        ReadWriteIOUtils.write(encodingResidualBufferLength, this.out);
        writeFirstValue();

//        System.out.println(s1);
//        System.out.println(encodingBlockWidthsLength);
//        System.out.println(encodingSeasonalBlockBufferLength / 8 + 1);
//        System.out.println(encodingBlockBufferLength / 8 + 1);

        calculateSeasonalComponentTime += t2 - t1;
        zigzagEncodingTime += t3 - t2;
        bitVarIntEncodingTime += t4 - t3;
        encodingResidualTime += t5 - t4;
        encodingResidualLengthTime += t6 - t5;
//        System.out.println("calculateSeasonalComponentTime: " + calculateSeasonalComponentTime);
//        System.out.println("zigzagEncodingTime: " + zigzagEncodingTime);
//        System.out.println("encodingSeasonalTime: " + bitVarIntEncodingTime);
//        System.out.println("encodingResidualTime: " + encodingResidualTime);
//        System.out.println("encodindResidualLengthTime: " + encodingResidualLengthTime);

//        System.out.println("##################");

        reset();
        writeIndex = -1;
    }

    public static class IntSTDEncoder extends STD5Encoder {

        private final int[] residualBuffer;
        private final int[] seasonalBuffer;
        private int firstValue;
        private int previousValue;

        /**
         * constructor of IntDeltaEncoder which is a sub-class of DeltaBinaryEncoder.
         */
        public IntSTDEncoder() {
            super();
            residualBuffer = new int[this.blockSize];
            seasonalBuffer = new int[this.period];
            encodingResidualBuffer = new byte[blockSize * 4];
            encodingSeasonalBuffer = new byte[period * 4];
            reset();
        }

        @Override
        protected void zigzagEncoding() {
            // TODO: real zigzag calculation
        }

        @Override
        protected void calculateSeasonalComponent() {
            // TODO: real seasonal components calculation
            for (int i = 0; i < period; ++i) {
                seasonalBuffer[i] = 47;
            }
        }

        @Override
        protected void bitVarIntEncoding() {

        }

        private void calcDelta(int value) {
            int delta = value - previousValue; // calculate delta
            residualBuffer[writeIndex++] = delta;
        }

        private int getValueWidth(int v) {
            return 32 - Integer.numberOfLeadingZeros(v);
        }

        @Override
        protected void calculateBitWidthsForSeasonalBuffer() {
            seasonalWidth = 0;
            for (int i = 0; i < period; i++) {
                seasonalWidth = Math.max(seasonalWidth, getValueWidth(seasonalBuffer[i]));
            }
        }

        /**
         * input a integer.
         *
         * @param value value to encode
         * @param out   the ByteArrayOutputStream which data encode into
         */
        public void encodeValue(int value, ByteArrayOutputStream out) {
            if (writeIndex == -1) {
                writeIndex++;
                firstValue = value;
                previousValue = firstValue;
                return;
            }
            calcDelta(value);
            previousValue = value;
            if (writeIndex == blockSize) {
                flush(out);
            }
        }

        @Override
        protected void reset() {
            firstValue = 0;
            previousValue = 0;
            for (int i = 0; i < blockSize; i++) {
                encodingResidualBuffer[i] = 0;
                residualBuffer[i] = 0;
            }
        }

        @Override
        protected void writeFirstValue() throws IOException {
            ReadWriteIOUtils.write(firstValue, out);
        }

        @Override
        protected void writeSeasonalToBytes(int i) {
            BytesUtils.intToBytes(seasonalBuffer[i], encodingSeasonalBuffer, seasonalWidth * i, seasonalWidth);
        }

        @Override
        public void encode(int value, ByteArrayOutputStream out) {
            encodeValue(value, out);
        }

        @Override
        public int getOneItemMaxSize() {
            return 4;
        }

        @Override
        public long getMaxByteSize() {
            // The meaning of 24 is: index(4)+width(4)+minDeltaBase(4)+firstValue(4)
            return (long) 24 + writeIndex * 4L;
        }
    }

    public static class LongSTDEncoder extends STD5Encoder {

        private final long[] residualBuffer;
        private final long[] seasonalBuffer;
        private long firstValue;
        private long previousValue;

        /**
         * constructor of LongDeltaEncoder which is a sub-class of DeltaBinaryEncoder.
         */
        public LongSTDEncoder() {
            super();
            residualBuffer = new long[blockSize];
            encodingResidualBuffer = new byte[blockSize * 8];
            encodingResidualBufferLength = 0;

            seasonalBuffer = new long[period];
            encodingSeasonalBuffer = new byte[period * 8];
            seasonalWidth = 0;

            residualLengths = new int[blockSize];
            encodingResidualLengths = new byte[blockSize * 4];
            residualLengthsWidth = 0;

            reset();
        }

        @Override
        public void encode(long value, ByteArrayOutputStream out) {
            encodeValue(value, out);
        }

        /**
         * input a integer or long value.
         *
         * @param value value to encode
         * @param out   - the ByteArrayOutputStream which data encode into
         */
        public void encodeValue(long value, ByteArrayOutputStream out) {
            if (writeIndex == -1) {
                writeIndex++;
                firstValue = value;
                previousValue = firstValue;
                return;
            }
            calcDelta(value);
            previousValue = value;
            if (writeIndex == blockSize) {
                flush(out);
            }
        }

        private void calcDelta(long value) {
            long delta = value - previousValue; // calculate delta
            residualBuffer[writeIndex] = delta;
            writeIndex++;
        }

        @Override
        protected void writeFirstValue() throws IOException {
            out.write(BytesUtils.longToBytes(firstValue));
        }

        private int getValueWidth(long v) {
            return 64 - Long.numberOfLeadingZeros(v);
        }

        @Override
        protected void calculateBitWidthsForSeasonalBuffer() {
            seasonalWidth = 0;
            for (int i = 0; i < period; i++) {
                seasonalWidth = Math.max(seasonalWidth, getValueWidth(seasonalBuffer[i]));
            }
        }

        @Override
        protected void writeSeasonalToBytes(int i) {
            BytesUtils.longToBytes(seasonalBuffer[i], encodingSeasonalBuffer, seasonalWidth * i, seasonalWidth);
        }

        @Override
        protected void bitVarIntEncoding() {
            for (int i = 0; i < writeIndex; i++) {
                int numBits = 64 - Long.numberOfLeadingZeros(residualBuffer[i]);
                BytesUtils.longToBytes(residualBuffer[i], encodingResidualBuffer, encodingResidualBufferLength, numBits);
//                System.out.println(residualBuffer[i] + " " + numBits);
                residualLengths[i] = numBits;
                encodingResidualBufferLength += numBits;
            }
//            System.out.println(encodingResidualBufferLength);
        }

        @Override
        protected void calculateSeasonalComponent() {
            for (int i = 0; i < period; ++i) {
                seasonalBuffer[i] = 0;
            }
            // de-seasonal
            for (int i = 0; i < writeIndex; ++i) {
                residualBuffer[i] -= seasonalBuffer[i % period];
            }
        }

        int func(long x, int phase) {
            int tempStorage = 0;
            for (int i = phase; i < residualBuffer.length; i += period) {
                tempStorage += 64 - Long.numberOfLeadingZeros(zigzagEncoder(x - residualBuffer[i]));
            }
            return tempStorage + 64 - Long.numberOfLeadingZeros(zigzagEncoder(x));  // seasonal
        }


        //        @Override
        protected void calculateSeasonalComponent2() {
            long[] phase = new long[writeIndex / period + 1];

            for (int i = 0; i < period; ++i) {
                for (int j = i; j < writeIndex; j += period) {  // same phase values
                    phase[(j - i) / period] = residualBuffer[j];
                }

                seasonalBuffer[i] = LinearMedian.getMedian(phase, phase.length);
            }
            // de-seasonal
            for (int i = 0; i < writeIndex; ++i) {
                residualBuffer[i] -= seasonalBuffer[i % period];
            }
        }

        //        @Override
        protected void calculateSeasonalComponent3() {
            long bestSeasonalComponent = 0, bestCost, curCost;
            for (int phase = 0; phase < period; ++phase) {
                bestCost = 0x3f3f3f3f;
                for (int j = phase; j < writeIndex; j += period) {  // find best components
                    curCost = func(residualBuffer[j], phase);
                    if (curCost < bestCost) {
                        bestCost = curCost;
                        bestSeasonalComponent = residualBuffer[j];
                    }
                }
                seasonalBuffer[phase] = bestSeasonalComponent;
            }
            // de-seasonal
            for (int i = 0; i < writeIndex; ++i) {
                residualBuffer[i] -= seasonalBuffer[i % period];
            }
        }

        protected long zigzagEncoder(long n) {
            return (n << 1) ^ (n >> 63);
        }

        @Override
        protected void zigzagEncoding() {
            for (int i = 0; i < period; ++i) {
                seasonalBuffer[i] = zigzagEncoder(seasonalBuffer[i]);
            }
            for (int i = 0; i < writeIndex; ++i) {
                residualBuffer[i] = zigzagEncoder(residualBuffer[i]);
            }
        }

        @Override
        protected void reset() {
            firstValue = 0L;
            previousValue = 0L;

            for (int i = 0; i < blockSize; i++) {
                residualBuffer[i] = 0L;
                residualLengths[i] = 0;
            }

            for (int i = 0; i < period; i++) {
                seasonalBuffer[i] = 0L;
            }

            seasonalWidth = 0;
            residualLengthsWidth = 0;
            encodingResidualBufferLength = 0;
        }

        @Override
        public int getOneItemMaxSize() {
            return 8;
        }

        @Override
        public long getMaxByteSize() {
            // The meaning of 24 is: index(4)+width(4)+minDeltaBase(8)+firstValue(8)
            return (long) 24 + writeIndex * 8L;
        }
    }
}
