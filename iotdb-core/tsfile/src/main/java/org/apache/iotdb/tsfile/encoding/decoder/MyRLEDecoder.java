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
public abstract class MyRLEDecoder extends Decoder {
    protected byte[] rleBlockBuf;

    /**
     * the first value in one pack.
     */
    protected int readTotalCount;

    protected int nextReadIndex = 0;

    protected int lengthNum;
    protected int lengthWidth;
    protected int indexWidth;

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

    public MyRLEDecoder() {
        super(TSEncoding.MyRLE);
    }

    protected abstract void allocateDataArray();

    /**
     * calculate the bytes length containing v bits.
     *
     * @param v - number of bits
     * @return number of bytes
     */
    protected int ceil(int v) {
        return (int) Math.ceil(v / 8.0);
    }

    @Override
    public boolean hasNext(ByteBuffer buffer) throws IOException {
        return (nextReadIndex < readTotalCount) || buffer.remaining() > 0;
    }

    public static class IntRLEDecoder extends MyRLEDecoder {
        private int[] data;
        private int[] length;
        private int[] index;

        public IntRLEDecoder() {
            super();
        }

        /**
         * if there's no decoded data left, decode next pack into {@code data}.
         *
         * @param buffer ByteBuffer
         * @return int
         */
        protected int readT(ByteBuffer buffer) {
            if (nextReadIndex == readTotalCount) {
                loadIntBatch(buffer);
            }
            return data[nextReadIndex++];
        }

        @Override
        public int readInt(ByteBuffer buffer) {
            readT(buffer);
            return 0;
        }

        /**
         * if remaining data has been run out, load next pack from InputStream.
         *
         * @param buffer ByteBuffer
         * @return int
         */
        protected int loadIntBatch(ByteBuffer buffer) {
            return 0;
        }

        private void readPack() {

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

    public static class LongRLEDecoder extends MyRLEDecoder {
        private long[] output;
        private long[] data;
        private int[] length;
        private int[] index;

        public LongRLEDecoder() {
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
            if (nextReadIndex == readTotalCount) {
                loadIntBatch(buffer);
            }
            return output[nextReadIndex++];
        }

        /**
         * if remaining data has been run out, load next pack from InputStream.
         *
         * @param buffer ByteBuffer
         */
        protected void loadIntBatch(ByteBuffer buffer) {
            // length
            lengthNum = ReadWriteIOUtils.readInt(buffer);
            lengthWidth = ReadWriteIOUtils.readInt(buffer);
            // index
            indexWidth = ReadWriteIOUtils.readInt(buffer);
            // number
            packNum = ReadWriteIOUtils.readInt(buffer);
            packWidth = ReadWriteIOUtils.readInt(buffer);

            nextReadIndex = 0;

            encodingLength = ceil(lengthNum * lengthWidth) + ceil(lengthNum * indexWidth) + ceil(packNum * packWidth);
            rleBlockBuf = new byte[encodingLength];
            buffer.get(rleBlockBuf);
            allocateDataArray();

            // load array
            readLength();
            readIndex();
            readPack();

            // reconstruct rle array
            reconstruct();
        }

        private void readLength() {
            for (int i = 0; i < lengthNum; i++) {
                length[i] = BytesUtils.bytesToInt(rleBlockBuf, lengthWidth * i, lengthWidth);
            }
        }

        private void readIndex() {
            int base = ceil(lengthNum * lengthWidth) * 8;
            for (int i = 0; i < lengthNum; i++) {
                index[i] = BytesUtils.bytesToInt(rleBlockBuf, base + indexWidth * i, indexWidth);
            }
        }

        private void readPack() {
            int base = ceil(lengthNum * lengthWidth) * 8 + ceil(lengthNum * indexWidth) * 8;
            for (int i = 0; i < packNum; i++) {
                data[i] = BytesUtils.bytesToLong(rleBlockBuf, base + packWidth * i, packWidth);
            }
        }

        private void reconstruct() {
            int outputIdx = 0;
            for (int dataIdx = 0, lengthIdx = 0; dataIdx < packNum; dataIdx++) {
                if (index.length > lengthIdx && dataIdx == index[lengthIdx]) {
                    for (int i = 0; i < length[lengthIdx]; i++) {
                        output[outputIdx++] = data[dataIdx];
                    }
                    lengthIdx++;
                } else {
                    output[outputIdx++] = data[dataIdx];
                }
            }
            readTotalCount = outputIdx;
        }

        @Override
        protected void allocateDataArray() {
            output = new long[TSFileDescriptor.getInstance().getConfig().getRleBlockSize()];
            data = new long[packNum];
            length = new int[lengthNum];
            index = new int[lengthNum];
        }

        @Override
        public void reset() {
            // do nothing
        }
    }
}