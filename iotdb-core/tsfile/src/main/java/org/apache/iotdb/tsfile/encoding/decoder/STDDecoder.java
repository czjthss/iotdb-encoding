package org.apache.iotdb.tsfile.encoding.decoder;

import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
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
    /**
     * the first value in one pack.
     */
    protected int readIntTotalCount = 0;

    protected int nextReadIndex = 0;

    // header
    protected int period;
    protected int seasonalWidth;
    protected int residualLengthsWidth;
    protected int encodingResidualBufferLength;
    // buffer
    protected byte[] seasonalBuffer;
    protected byte[] residualLengthBuffer;
    protected byte[] residualBuffer;

    /**
     * max bit length of all value in a pack.
     */
    protected int packWidth;
    /**
     * data number in this pack.
     */
    protected int packNum;

    public STDDecoder() {
        super(TSEncoding.STD);
    }

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

//            stdBuf = new byte[ceil(period * seasonalWidth) + ceil(packNum * packWidth)];
//            buffer.get(stdBuf);
//            allocateDataArray();

            previous = firstValue;
            readIntTotalCount = packNum;
            nextReadIndex = 0;
            readPack();
            return firstValue;
        }

        private void readPack() {
            for (int i = 0; i < packNum; i++) {
//                int v = BytesUtils.bytesToInt(stdBuf, packWidth * i, packWidth);
//                data[i] = previous + minDeltaBase + v;
//                previous = data[i];
            }
        }

        @Override
        public void reset() {
            // do nothing
        }
    }

    public static class LongSTDDecoder extends STDDecoder {

        private long firstValue;
        private long[] data;
        private int[] residualLength;
        private long[] seasonal;
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
            // header
            period = ReadWriteIOUtils.readInt(buffer);
            packNum = ReadWriteIOUtils.readInt(buffer);
            seasonalWidth = ReadWriteIOUtils.readInt(buffer);
            residualLengthsWidth = ReadWriteIOUtils.readInt(buffer);
            encodingResidualBufferLength = ReadWriteIOUtils.readInt(buffer);
            firstValue = ReadWriteIOUtils.readLong(buffer);

            // seasonal buffer
            seasonalBuffer = new byte[ceil(period * seasonalWidth)];
            buffer.get(seasonalBuffer);

            // residual length
            residualLengthBuffer = new byte[ceil(packNum * residualLengthsWidth)];
            buffer.get(residualLengthBuffer);

            // residual
            residualBuffer = new byte[ceil(encodingResidualBufferLength)];
            buffer.get(residualBuffer);

            // allocate data array
            data = new long[packNum];
            residualLength = new int[packNum];
            seasonal = new long[period];

            previous = firstValue;
            readIntTotalCount = packNum;
            nextReadIndex = 0;

            // read
            readSeasonal();
            readResidualLength();
            readVarIntPack();
            return firstValue;
        }


        private void readSeasonal() {
            long value;
            for (int i = 0; i < period; i++) {
                value = BytesUtils.bytesToLong(seasonalBuffer, seasonalWidth * i, seasonalWidth);
                seasonal[i] = zigzagDecoder(value);
            }
        }

        private void readResidualLength() {
            int value;
            for (int i = 0; i < packNum; i++) {
                value = BytesUtils.bytesToInt(residualLengthBuffer, residualLengthsWidth * i, residualLengthsWidth);
                residualLength[i] = value;
            }
        }

        private void readVarIntPack() {
            long value;
            int pos = 0;
            int width;
            for (int i = 0; i < packNum; i++) {
                width = residualLength[i];
                value = BytesUtils.bytesToLong(residualBuffer, pos, width);
                value = zigzagDecoder(value);
                pos += width;

                data[i] = previous + value + seasonal[i % period];
                previous = data[i];
            }
        }

        protected long zigzagDecoder(long n) {
            if ((1 & n) == 1) {
                return ~(n >> 1);
            } else {
                return n >> 1;
            }
        }

        @Override
        public void reset() {
            // do nothing
        }
    }

    public static class FloatSTDDecoder extends IntSTDDecoder {
        private final int scale;

        public FloatSTDDecoder() {
            super();
            scale = TSFileDescriptor.getInstance().getConfig().getScale();
        }

        @Override
        public float readFloat(ByteBuffer buffer) {
            return (float) (readInt(buffer) / Math.pow(10, scale));
        }
    }

    public static class DoubleSTDDecoder extends LongSTDDecoder {
        private final int scale;

        public DoubleSTDDecoder() {
            super();
            scale = TSFileDescriptor.getInstance().getConfig().getScale();
        }

        @Override
        public double readDouble(ByteBuffer buffer) {
            return readLong(buffer) / Math.pow(10, scale);
        }
    }
}