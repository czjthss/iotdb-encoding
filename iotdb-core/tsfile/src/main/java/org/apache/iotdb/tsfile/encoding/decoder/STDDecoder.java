package org.apache.iotdb.tsfile.encoding.decoder;

import org.apache.iotdb.tsfile.encoding.encoder.DeltaBinaryEncoder;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.utils.BytesUtils;
import org.apache.iotdb.tsfile.utils.ReadWriteIOUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This class is a decoder for decoding the byte array that encoded by {@code
 * DeltaBinaryEncoder}.DeltaBinaryDecoder just supports integer and long values.<br>
 * .
 *
 * @see DeltaBinaryEncoder
 */
public abstract class STDDecoder extends Decoder {

    protected long count = 0;
    protected byte[] stdBuf;

    /**
     * the first value in one pack.
     */
    protected int readIntTotalCount = 0;

    protected int nextReadIndex = 0;

    protected int period;
    protected int seasonalWidth;
    protected int anomalyNumber;
    protected int anomalyWidth;
    protected int anomalyIndexWidth;

    /**
     * max bit length of all value in a pack.
     */
    protected int packWidth;
    /**
     * data number in this pack.
     */
    protected int packNum;

    /**
     * how many bytes data takes after encoding.
     */
    protected int encodingLength;

    public STDDecoder() {
        super(TSEncoding.STD);
    }

    protected abstract void allocateDataArray();

    /**
     * calculate the bytes length containing v bits.
     *
     * @param v - number of bits
     * @return number of bytes
     */
    protected int ceil(int v) {
        return (int) Math.ceil((double) (v) / 8.0);
    }

    @Override
    public boolean hasNext(ByteBuffer buffer) throws IOException {
        return (nextReadIndex < readIntTotalCount) || buffer.remaining() > 0;
    }

    public static class IntSTDDecoder extends STDDecoder {

        private int firstValue;
        private int[] data;
        private int previous;
        /**
         * minimum value for all difference.
         */
        private int minDeltaBase;

        public IntSTDDecoder() {
            super();
        }

        /**
         * if there's no decoded data left, decode next pack into {@code data}.
         *
         * @param buffer ByteBuffer
         * @return int
         */
        protected int readT(ByteBuffer buffer) {
            if (nextReadIndex == readIntTotalCount) {
                return loadIntBatch(buffer);
            }
            return data[nextReadIndex++];
        }

        @Override
        public int readInt(ByteBuffer buffer) {
            return readT(buffer);
        }

        /**
         * if remaining data has been run out, load next pack from InputStream.
         *
         * @param buffer ByteBuffer
         * @return int
         */
        protected int loadIntBatch(ByteBuffer buffer) {
            packNum = ReadWriteIOUtils.readInt(buffer);
            packWidth = ReadWriteIOUtils.readInt(buffer);
            firstValue = ReadWriteIOUtils.readInt(buffer);
            count++;

            encodingLength = ceil(period * seasonalWidth) + ceil(packNum * packWidth);
            stdBuf = new byte[encodingLength];
            buffer.get(stdBuf);
            allocateDataArray();

            previous = firstValue;
            readIntTotalCount = packNum;
            nextReadIndex = 0;
            readPack();
            return firstValue;
        }

        private void readPack() {
            for (int i = 0; i < packNum; i++) {
                int v = BytesUtils.bytesToInt(stdBuf, packWidth * i, packWidth);
                data[i] = previous + minDeltaBase + v;
                previous = data[i];
            }
        }

        @Override
        protected void allocateDataArray() {
            data = new int[packNum];
        }

        @Override
        public void reset() {
            // do nothing
        }
    }

    public static class LongSTDDecoder extends STDDecoder {

        private long firstValue;
        private long[] data;
        private long[] seasonal;
        private long[] anomaly;
        private int[] anomalyIndex;
        private long previous;

        public LongSTDDecoder() {
            super();
        }

        @Override
        public long readLong(ByteBuffer buffer) {
            return readT(buffer);
        }

        /**
         * if there's no decoded data left, decode next pack into {@code data}.
         *
         * @param buffer ByteBuffer
         * @return long value
         */
        protected long readT(ByteBuffer buffer) {
            if (nextReadIndex == readIntTotalCount) {
                return loadIntBatch(buffer);
            }
            return data[nextReadIndex++];
        }

        /**
         * if remaining data has been run out, load next pack from InputStream.
         *
         * @param buffer ByteBuffer
         * @return long value
         */
        protected long loadIntBatch(ByteBuffer buffer) {
            period = ReadWriteIOUtils.readInt(buffer);
            seasonalWidth = ReadWriteIOUtils.readInt(buffer);
            // anomaly
            anomalyNumber = ReadWriteIOUtils.readInt(buffer);
            anomalyWidth = ReadWriteIOUtils.readInt(buffer);
            anomalyIndexWidth = ReadWriteIOUtils.readInt(buffer);
            // number
            packNum = ReadWriteIOUtils.readInt(buffer);
            packWidth = ReadWriteIOUtils.readInt(buffer);
            firstValue = ReadWriteIOUtils.readLong(buffer);
            count++;

            encodingLength = ceil(period * seasonalWidth) + ceil(anomalyNumber * anomalyWidth) + ceil(anomalyNumber * anomalyIndexWidth) + ceil((packNum - anomalyNumber) * packWidth);
            stdBuf = new byte[encodingLength];
            buffer.get(stdBuf);
            allocateDataArray();

            previous = firstValue;
            readIntTotalCount = packNum;
            nextReadIndex = 0;
            readSeasonal();
            readAnomaly();
            readAnomalyIndex();
            readPack();
            return firstValue;
        }

        protected long zigzagDecoder(long n) {
            if ((1 & n) == 1) {
                return ~(n >> 1);
            } else {
                return n >> 1;
            }
        }

        private void readSeasonal() {
            long value;
            for (int i = 0; i < period; i++) {
                value = BytesUtils.bytesToLong(stdBuf, seasonalWidth * i, seasonalWidth);
                seasonal[i] = zigzagDecoder(value);
            }
        }

        private void readAnomaly() {
            long value;
            int base = (int) Math.ceil((period * seasonalWidth) / 8.0) * 8;
            for (int i = 0; i < anomalyNumber; i++) {
                value = BytesUtils.bytesToLong(stdBuf, base + anomalyWidth * i, anomalyWidth);
                anomaly[i] = value;
            }
        }

        private void readAnomalyIndex() {
            int value;
            int base = (int) Math.ceil((period * seasonalWidth) / 8.0) * 8 + (int) Math.ceil((anomalyNumber * anomalyWidth) / 8.0) * 8;
            for (int i = 0; i < anomalyNumber; i++) {
                value = BytesUtils.bytesToInt(stdBuf, base + anomalyIndexWidth * i, anomalyIndexWidth);
                anomalyIndex[i] = value;  // convert to int
            }
        }

        private void readPack() {
            long value;
            int base = (int) Math.ceil((period * seasonalWidth) / 8.0) * 8 + (int) Math.ceil((anomalyNumber * anomalyWidth) / 8.0) * 8 + (int) Math.ceil((anomalyNumber * anomalyIndexWidth) / 8.0) * 8;
            for (int i = 0, anomalyArrayIndex = 0; i < packNum; i++) {
                if (anomalyArrayIndex < anomalyNumber && i == anomalyIndex[anomalyArrayIndex]) {
                    value = anomaly[anomalyArrayIndex];
                    anomalyArrayIndex++;
                } else {
                    value = BytesUtils.bytesToLong(stdBuf, base + packWidth * (i - anomalyArrayIndex), packWidth); // ignore anomaly
                }
                value = zigzagDecoder(value);
//                System.out.println(value);
                data[i] = previous + value + seasonal[i % period];
                previous = data[i];
            }
        }

        @Override
        protected void allocateDataArray() {
            data = new long[packNum];
            anomaly = new long[anomalyNumber];
            anomalyIndex = new int[anomalyNumber];
            seasonal = new long[period];
        }

        @Override
        public void reset() {
            // do nothing
        }

        public static void main(String[] args) {
            long n = 11, pre, now;

            if ((1 & n) == 1) {
                now = ~(n >> 1);
            } else {
                now = n >> 1;
            }
            long m = 0;
            System.out.println((m << 1) ^ (m >> 63));


            if (n % 2 == 0)
                pre = n / 2;
            else
                pre = -(n + 1) / 2;
            System.out.println(pre + " " + now);
        }
    }
}