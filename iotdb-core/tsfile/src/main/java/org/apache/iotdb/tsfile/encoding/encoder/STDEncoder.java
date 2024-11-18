package org.apache.iotdb.tsfile.encoding.encoder;

import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.utils.BytesUtils;
import org.apache.iotdb.tsfile.utils.ReadWriteIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class STDEncoder extends Encoder {
    private static final Logger logger = LoggerFactory.getLogger(DeltaBinaryEncoder.class);
    protected ByteArrayOutputStream out;

    protected int blockSize;

    protected int period;
    protected byte[] encodingBlockBuffer;
    protected byte[] encodingSeasonalBlockBuffer;

    protected byte[] encodingAnomalyBuffer;
    protected byte[] encodingAnomalyIndexBuffer;

    protected int writeIndex = -1;
    protected int writeWidth = 0;
    protected int seasonalWidth = 0;

    protected int anomalyNumber = 0;
    protected int anomalyWidth = 0;
    protected int anomalyIndexWidth = 0;


    /**
     * constructor of STDEncoder.
     */
    protected STDEncoder() {
        super(TSEncoding.STD);
        // the number how many numbers to be packed into a block.
        blockSize = TSFileDescriptor.getInstance().getConfig().getStdBlockSize();
        period = TSFileDescriptor.getInstance().getConfig().getPeriodLength();
    }

    protected abstract void writeFirstValue() throws IOException;

    protected abstract void writeSeasonalToBytes(int i);

    protected abstract void writeAnomalyToBytes(int i);

    protected abstract void writeAnomalyIndexToBytes(int i);

    protected abstract void reset();

    protected abstract void calculateSeasonalComponent();

    protected abstract void zigzagEncoding();

    protected abstract void calculateBitWidthsForSTDBlockBuffer();

    protected abstract void calculateBitWidthsForSeasonalBlockBuffer();

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
     * write all data into {@code encodingBlockBuffer}.
     */

    private void writeHeaderToBytes() throws IOException {
        // period information
        ReadWriteIOUtils.write(period, out);
        ReadWriteIOUtils.write(seasonalWidth, out);
        // anomaly information
        ReadWriteIOUtils.write(anomalyNumber, out);
        ReadWriteIOUtils.write(anomalyWidth, out);
        ReadWriteIOUtils.write(anomalyIndexWidth, out);
        // data information
        ReadWriteIOUtils.write(writeIndex, out);
        ReadWriteIOUtils.write(writeWidth, out);
        writeFirstValue();
    }

    private void writeSeasonalWithMinWidth() {
        for (int i = 0; i < period; i++) {
            writeSeasonalToBytes(i);
        }
        int encodingLength = (int) Math.ceil((period * seasonalWidth) / 8.0);
        out.write(encodingSeasonalBlockBuffer, 0, encodingLength);
    }

    private void writeAnomalyWithMinWidth() {
        for (int i = 0; i < anomalyNumber; i++) {
            writeAnomalyToBytes(i);
        }
        int encodingLength = (int) Math.ceil((anomalyNumber * anomalyWidth) / 8.0);
        out.write(encodingAnomalyBuffer, 0, encodingLength);
    }

    private void writeAnomalyIndexWithMinWidth() {
        for (int i = 0; i < anomalyNumber; i++) {
            writeAnomalyIndexToBytes(i);
        }
        int encodingLength = (int) Math.ceil((anomalyNumber * anomalyIndexWidth) / 8.0);
        out.write(encodingAnomalyIndexBuffer, 0, encodingLength);
    }

    protected abstract void writeDataWithMinWidth();

    private void flushBlockBuffer(ByteArrayOutputStream out) throws IOException {
        if (writeIndex == -1) {
            return;
        }

        calculateSeasonalComponent();
        zigzagEncoding();

        this.out = out;
        // calculate width
        calculateBitWidthsForSTDBlockBuffer();
        calculateBitWidthsForSeasonalBlockBuffer();
        // store
        writeHeaderToBytes();

        int s1 = out.size();

        writeSeasonalWithMinWidth();

        int s2 = out.size();

        // anomaly
        writeAnomalyWithMinWidth();

        int s3 = out.size();

        writeAnomalyIndexWithMinWidth();

        int s4 = out.size();

        // data
        writeDataWithMinWidth();

        int s5 = out.size();

        reset();
        writeIndex = -1;
//        System.out.println(s1);
//        System.out.println(s2 - s1);
//        System.out.println(s3 - s2);
//        System.out.println(s4 - s3);
//        System.out.println(s5 - s4);
//        System.out.println("############");
    }

    public static class IntSTDEncoder extends STDEncoder {

        private final int[] stdBlockBuffer;
        private final int[] seasonalBlockBuffer;
        private int[] anomalyBuffer;
        private int[] anomalyIndexBuffer;
        private int firstValue;
        private int previousValue;

        /**
         * constructor of IntDeltaEncoder which is a sub-class of DeltaBinaryEncoder.
         */
        public IntSTDEncoder() {
            super();
            stdBlockBuffer = new int[this.blockSize];
            seasonalBlockBuffer = new int[this.period];
            encodingBlockBuffer = new byte[blockSize * 4];
            encodingSeasonalBlockBuffer = new byte[period * 4];
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
                seasonalBlockBuffer[i] = 47;
            }
        }

        @Override
        protected void calculateBitWidthsForSTDBlockBuffer() {
            writeWidth = 0;
            for (int i = 0; i < writeIndex; i++) {
                writeWidth = Math.max(writeWidth, getValueWidth(stdBlockBuffer[i]));
            }
        }

        @Override
        protected void calculateBitWidthsForSeasonalBlockBuffer() {
            seasonalWidth = 0;
            for (int i = 0; i < period; i++) {
                seasonalWidth = Math.max(seasonalWidth, getValueWidth(seasonalBlockBuffer[i]));
            }
        }

        @Override
        protected void writeDataWithMinWidth() {

        }

        private void calcDelta(int value) {
            int delta = value - previousValue; // calculate delta
            stdBlockBuffer[writeIndex++] = delta;
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
                encodingBlockBuffer[i] = 0;
                stdBlockBuffer[i] = 0;
            }
        }

        private int getValueWidth(int v) {
            return 32 - Integer.numberOfLeadingZeros(v);
        }

        @Override
        protected void writeSeasonalToBytes(int i) {
            BytesUtils.intToBytes(seasonalBlockBuffer[i], encodingSeasonalBlockBuffer, seasonalWidth * i, seasonalWidth);
        }

        @Override
        protected void writeAnomalyToBytes(int i) {

        }

        @Override
        protected void writeAnomalyIndexToBytes(int i) {

        }

        @Override
        protected void writeFirstValue() throws IOException {
            ReadWriteIOUtils.write(firstValue, out);
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

    public static class LongSTDEncoder extends STDEncoder {

        private final long[] stdBlockBuffer;
        private final long[] seasonalBlockBuffer;
        private long[] anomalyBuffer;
        private long[] anomalyIndexBuffer;
        private long firstValue;
        private long previousValue;

        /**
         * constructor of LongDeltaEncoder which is a sub-class of DeltaBinaryEncoder.
         */
        public LongSTDEncoder() {
            super();
            stdBlockBuffer = new long[blockSize];
            seasonalBlockBuffer = new long[period];
            encodingBlockBuffer = new byte[blockSize * 8];
            encodingSeasonalBlockBuffer = new byte[period * 8];
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
            seasonalBlockBuffer[writeIndex % period] += delta;
            stdBlockBuffer[writeIndex] = delta;
            writeIndex++;
        }

        @Override
        protected void writeSeasonalToBytes(int i) {
            BytesUtils.longToBytes(seasonalBlockBuffer[i], encodingSeasonalBlockBuffer, seasonalWidth * i, seasonalWidth);
        }

        @Override
        protected void writeAnomalyToBytes(int i) {
            BytesUtils.longToBytes(anomalyBuffer[i], encodingAnomalyBuffer, anomalyWidth * i, anomalyWidth);
        }

        @Override
        protected void writeAnomalyIndexToBytes(int i) {
            BytesUtils.longToBytes(anomalyIndexBuffer[i], encodingAnomalyIndexBuffer, anomalyIndexWidth * i, anomalyIndexWidth);
        }

        @Override
        protected void writeFirstValue() throws IOException {
            out.write(BytesUtils.longToBytes(firstValue));
        }

        @Override
        protected void writeDataWithMinWidth() {
            for (int i = 0, anomalyArrayIndex = 0; i < writeIndex; i++) {
                if (anomalyArrayIndex < anomalyNumber && i == anomalyIndexBuffer[anomalyArrayIndex]) {
                    anomalyArrayIndex++; // do not record
                } else {
                    BytesUtils.longToBytes(stdBlockBuffer[i], encodingBlockBuffer, writeWidth * (i - anomalyArrayIndex), writeWidth);
                }
            }
            int encodingLength = (int) Math.ceil(((writeIndex - anomalyNumber) * writeWidth) / 8.0);
            out.write(encodingBlockBuffer, 0, encodingLength);
        }

//        @Override
//        protected void calculateSeasonalComponent() {
//            int periodNum = writeIndex / period;
//            for (int i = 0; i < period; ++i) {
//                if (i < writeIndex - periodNum * period) // exceeding period values
//                    seasonalBlockBuffer[i] /= (periodNum + 1);
//                else
//                    seasonalBlockBuffer[i] /= periodNum;
//            }
//            // de-seasonal
//            for (int i = 0; i < writeIndex; ++i) {
//                stdBlockBuffer[i] -= seasonalBlockBuffer[i % period];
//            }
//        }

//        @Override
//        protected void calculateSeasonalComponent() {
//        }

        @Override
        protected void calculateSeasonalComponent() {
            long[] seasonalMin = new long[period];
            long[] seasonalMax = new long[period];

            for (int i = 0; i < writeIndex; ++i) {
                if (i < period) {
                    seasonalMin[i] = stdBlockBuffer[i];
                    seasonalMax[i] = stdBlockBuffer[i];
                } else if (stdBlockBuffer[i] < seasonalMin[i % period]) {
                    seasonalMin[i % period] = stdBlockBuffer[i];
                } else if (stdBlockBuffer[i] > seasonalMax[i % period]) {
                    seasonalMax[i % period] = stdBlockBuffer[i];
                }
            }

            // range and seasonal
            long range;
            for (int i = 0; i < period; ++i) {
                range = (seasonalMax[i] - seasonalMin[i]) / 2;
                seasonalBlockBuffer[i] = seasonalMax[i] - range;
            }

            // de-seasonal
            for (int i = 0; i < writeIndex; ++i) {
                stdBlockBuffer[i] -= seasonalBlockBuffer[i % period];
            }
        }

        protected long zigzagEncoder(long n) {
            return (n << 1) ^ (n >> 63);
        }

        @Override
        protected void zigzagEncoding() {
            for (int i = 0; i < period; ++i) {
                seasonalBlockBuffer[i] = zigzagEncoder(seasonalBlockBuffer[i]);
            }
            for (int i = 0; i < writeIndex; ++i) {
                stdBlockBuffer[i] = zigzagEncoder(stdBlockBuffer[i]);
            }
        }


        @Override
        protected void calculateBitWidthsForSTDBlockBuffer() {
            // generate width array
            int[] widthArray = new int[65];
            for (int i = 0; i < writeIndex; i++) {
                widthArray[getValueWidth(stdBlockBuffer[i])]++;
            }

            // calculate best width
            int cumNumber = 0;
            int curCost, minCost = Integer.MAX_VALUE;
            for (int curWidth = 64; curWidth > 0; curWidth--) {
                // anomaly width
                if (anomalyWidth == 0 && widthArray[curWidth] != 0) {
                    anomalyWidth = curWidth;
                }
                // storage cost
                curCost = curWidth * (writeIndex - cumNumber) + ((anomalyWidth + getValueWidth(writeIndex)) * cumNumber);
                if (curCost < minCost) {
                    minCost = curCost;
                    writeWidth = curWidth;
                    anomalyNumber = cumNumber;  // anomaly number is
                }
                cumNumber += widthArray[curWidth];
            }

            // initial buffer
            anomalyBuffer = new long[anomalyNumber];
            anomalyIndexBuffer = new long[anomalyNumber];
            encodingAnomalyBuffer = new byte[anomalyNumber * 8];
            encodingAnomalyIndexBuffer = new byte[anomalyNumber * 8];

            // record anomaly
            int anomalyRecordIndex = 0;
            anomalyIndexWidth = 0;
            for (int i = 0; i < writeIndex; i++) {
                if (getValueWidth(stdBlockBuffer[i]) > writeWidth) {  // exceed bit-width
                    anomalyBuffer[anomalyRecordIndex] = stdBlockBuffer[i];
                    anomalyIndexBuffer[anomalyRecordIndex] = i;
                    anomalyIndexWidth = Math.max(anomalyIndexWidth, getValueWidth(i)); // anomaly index width
                    anomalyRecordIndex++;
                }
            }
        }

        @Override
        protected void calculateBitWidthsForSeasonalBlockBuffer() {
            seasonalWidth = 0;
            for (int i = 0; i < period; i++) {
                seasonalWidth = Math.max(seasonalWidth, getValueWidth(seasonalBlockBuffer[i]));
            }
        }

        private int getValueWidth(long v) {
            return 64 - Long.numberOfLeadingZeros(v);
        }

        @Override
        protected void reset() {
            firstValue = 0L;
            previousValue = 0L;
            for (int i = 0; i < blockSize; i++) {
                encodingBlockBuffer[i] = 0;
                stdBlockBuffer[i] = 0L;
            }
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
