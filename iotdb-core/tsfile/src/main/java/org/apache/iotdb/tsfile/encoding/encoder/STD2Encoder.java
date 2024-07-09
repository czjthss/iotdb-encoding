package org.apache.iotdb.tsfile.encoding.encoder;

import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.utils.BytesUtils;
import org.apache.iotdb.tsfile.utils.ReadWriteIOUtils;
import org.apache.iotdb.tsfile.encoding.bitpacking.LongPacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public abstract class STD2Encoder extends Encoder {
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

    protected int[] stdSeasonalWidths;
    protected int[] stdBlockWidths;
    protected byte[] encodingSeasonalWidths;
    protected byte[] encodingBlockWidths;
    protected int encodingSeasonalWidthsLength;
    protected int encodingBlockWidthsLength;

    protected int encodingSeasonalBlockBufferLength;
    protected int encodingBlockBufferLength;


    /**
     * constructor of STDEncoder.
     */
    protected STD2Encoder() {
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

    protected abstract void byteVarIntEncoding();

    protected abstract void bitVarIntEncoding();

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

    protected void rleEncoding() throws IOException {
        ByteArrayOutputStream rleOut = new ByteArrayOutputStream();
        IntRleEncoder encoder = new IntRleEncoder();
        for (int val : stdSeasonalWidths) {
            encoder.encode(val, rleOut);
        }
        encoder.flush(rleOut);
        System.arraycopy(rleOut.toByteArray(), 0, encodingSeasonalWidths, 0, rleOut.size());
        encodingSeasonalWidthsLength = rleOut.size();
        rleOut.reset();
        for (int val : stdBlockWidths) {
            encoder.encode(val, rleOut);
        }

//        for (int i = 0; i < 100; ++i) {
//            System.out.print(stdBlockWidths[i] + " ");
//        }
//        System.out.println();
//        System.out.println();

        encoder.flush(rleOut);
        System.arraycopy(rleOut.toByteArray(), 0, encodingBlockWidths, 0, rleOut.size());
        encodingBlockWidthsLength = rleOut.size();
    }

    private void flushBlockBuffer(ByteArrayOutputStream out) throws IOException {
        if (writeIndex == -1) {
            return;
        }

        long t1 = System.nanoTime();

        calculateSeasonalComponent();

        long t2 = System.nanoTime();

        zigzagEncoding();

        long t3 = System.nanoTime();

        this.out = out;
//        // calculate width
//        calculateBitWidthsForSTDBlockBuffer();
//        calculateBitWidthsForSeasonalBlockBuffer();
//        // store
//        writeHeaderToBytes();
//        writeSeasonalWithMinWidth();
//        // anomaly
//        writeAnomalyWithMinWidth();
//        writeAnomalyIndexWithMinWidth();
//        // data
//        writeDataWithMinWidth();

        byteVarIntEncoding();

        long t4 = System.nanoTime();

        rleEncoding();

        long t5 = System.nanoTime();

        ReadWriteIOUtils.write(period, this.out);
        ReadWriteIOUtils.write(writeIndex, this.out);
        ReadWriteIOUtils.write(encodingSeasonalWidthsLength, this.out);
        ReadWriteIOUtils.write(encodingBlockWidthsLength, this.out);
        ReadWriteIOUtils.write(encodingSeasonalBlockBufferLength, this.out);
        ReadWriteIOUtils.write(encodingBlockBufferLength, this.out);
        writeFirstValue();

        long t6 = System.nanoTime();

        int s1 = this.out.size();

        this.out.write(encodingSeasonalWidths, 0, encodingSeasonalWidthsLength);
        this.out.write(encodingBlockWidths, 0, encodingBlockWidthsLength);
        this.out.write(encodingSeasonalBlockBuffer, 0, encodingSeasonalBlockBufferLength);
        this.out.write(encodingBlockBuffer, 0, encodingBlockBufferLength);

        long t7 = System.nanoTime();

//        System.out.println(s1);
//        System.out.println(encodingSeasonalWidthsLength);
//        System.out.println(encodingBlockWidthsLength);
//        System.out.println(encodingSeasonalBlockBufferLength);
//        System.out.println(encodingBlockBufferLength);
//


//        System.out.println("t2-t1: " + (t2 - t1));
//        System.out.println("t3-t2: " + (t3 - t2));
//        System.out.println("t4-t3: " + (t4 - t3));
//        System.out.println("t5-t4: " + (t5 - t4));
//        System.out.println("t6-t5: " + (t6 - t5));
//        System.out.println("t7-t6: " + (t7 - t6));
//
//        System.out.println("##################");

//        System.out.println(t7 - t1);

        reset();
        writeIndex = -1;
    }

    public static class IntSTDEncoder extends STD2Encoder {

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
        protected void byteVarIntEncoding() {

        }

        @Override
        protected void bitVarIntEncoding() {

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

    public static class LongSTDEncoder extends STD2Encoder {

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
            stdSeasonalWidths = new int[period];
            stdBlockWidths = new int[blockSize];
            encodingSeasonalWidths = new byte[period * 4];
            encodingBlockWidths = new byte[blockSize * 4];
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

        protected byte[] writeUnsignedVarLongToBytes(Long value) {
            byte[] buf = new byte[10];
            int pos = 0;
            while ((value & 0xFFFFFF00L) != 0L) {
                buf[pos++] = (byte) (value & 0xFF);
                value >>>= 8;
            }
            buf[pos++] = (byte) (value & 0xFF);
            return Arrays.copyOfRange(buf, 0, pos);
        }

        @Override
        protected void byteVarIntEncoding() {
            for (int i = 0; i < period; i++) {
                byte[] bytes = writeUnsignedVarLongToBytes(seasonalBlockBuffer[i]);
                System.arraycopy(bytes, 0, encodingSeasonalBlockBuffer, i * bytes.length, bytes.length);
                stdSeasonalWidths[i] = bytes.length;
                encodingSeasonalBlockBufferLength += bytes.length;
            }

            for (int i = 0; i < writeIndex; i++) {
                byte[] bytes = writeUnsignedVarLongToBytes(stdBlockBuffer[i]);
                System.arraycopy(bytes, 0, encodingBlockBuffer, i * bytes.length, bytes.length);
                stdBlockWidths[i] = bytes.length;
                encodingBlockBufferLength += bytes.length;
            }
        }

        @Override
        protected void bitVarIntEncoding() {
            // TODO: implement bitVarIntEncoding
        }

        @Override
        protected void calculateSeasonalComponent() {
            int periodNum = writeIndex / period;
            for (int i = 0; i < period; ++i) {
                if (i < writeIndex - periodNum * period) // exceeding period values
                    seasonalBlockBuffer[i] /= (periodNum + 1);
                else
                    seasonalBlockBuffer[i] /= periodNum;
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
                encodingBlockWidths[i] = 0;
                stdBlockWidths[i] = 0;
            }
            for (int i = 0; i < period; i++) {
                encodingSeasonalBlockBuffer[i] = 0;
                seasonalBlockBuffer[i] = 0L;
                encodingSeasonalWidths[i] = 0;
                stdSeasonalWidths[i] = 0;
            }
            encodingBlockBufferLength = 0;
            encodingSeasonalBlockBufferLength = 0;
            encodingBlockWidthsLength = 0;
            encodingSeasonalWidthsLength = 0;
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
