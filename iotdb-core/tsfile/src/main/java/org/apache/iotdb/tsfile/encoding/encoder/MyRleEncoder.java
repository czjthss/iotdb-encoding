package org.apache.iotdb.tsfile.encoding.encoder;

import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.utils.BytesUtils;
import org.apache.iotdb.tsfile.utils.ReadWriteIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class MyRleEncoder extends Encoder {
    private static final Logger logger = LoggerFactory.getLogger(DeltaBinaryEncoder.class);

    protected boolean firstValue;  // if it is the first value
    protected final int minRleRepeatNumber = 8;

    protected ByteArrayOutputStream out;
    protected byte[] encodingDataBuffer;
    protected byte[] encodingLengthBuffer;
    protected byte[] encodingIndexBuffer;

    protected int lengthNum = 0;
    protected int lengthWidth;
    protected int indexWidth;

    protected int dataNum = 0;
    protected int dataWidth;

    protected int blockSize;

    /**
     * constructor of STDEncoder.
     */
    protected MyRleEncoder() {
        super(TSEncoding.RLE);
        blockSize = TSFileDescriptor.getInstance().getConfig().getRleBlockSize();
    }

    protected abstract void writeLengthToBytes(int i);

    protected abstract void writeIndexToBytes(int i);

    protected abstract void writeDataToBytes(int i);

    protected abstract void reset();

    protected abstract void calculateBitWidthsForRLEBlockBuffer();

    protected abstract void calculateBitWidthsForLengthBlockBuffer();

    protected abstract void calculateBitWidthsForIndexBlockBuffer();

    protected abstract void recordValue();

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
        // length information
        ReadWriteIOUtils.write(lengthNum, out);
        ReadWriteIOUtils.write(lengthWidth, out);
        // index information
        ReadWriteIOUtils.write(indexWidth, out);
        // data information
        ReadWriteIOUtils.write(dataNum, out);
        ReadWriteIOUtils.write(dataWidth, out);
    }

    private void writeDataWithMinWidth() {
        for (int i = 0; i < dataNum; i++) {
            writeDataToBytes(i);
        }
        int encodingLength = (int) Math.ceil((dataNum * dataWidth) / 8.0);
        out.write(encodingDataBuffer, 0, encodingLength);
    }

    private void writeLengthWithMinWidth() {
        for (int i = 0; i < lengthNum; i++) {
            writeLengthToBytes(i);
        }
        int encodingLength = (int) Math.ceil((lengthNum * lengthWidth) / 8.0);
        out.write(encodingLengthBuffer, 0, encodingLength);
    }

    private void writeIndexWithMinWidth() {
        for (int i = 0; i < lengthNum; i++) {
            writeIndexToBytes(i);
        }
        int encodingLength = (int) Math.ceil((lengthNum * indexWidth) / 8.0);
        out.write(encodingIndexBuffer, 0, encodingLength);
    }

    private void flushBlockBuffer(ByteArrayOutputStream out) throws IOException {
        if (firstValue) {  // if first value is not appear
            return;
        }
        recordValue();  // record

        this.out = out;
        // calculate width
        calculateBitWidthsForRLEBlockBuffer();

        calculateBitWidthsForLengthBlockBuffer();

        calculateBitWidthsForIndexBlockBuffer();
        // store
        writeHeaderToBytes();

        int s1 = out.size();

        writeLengthWithMinWidth();

        int s2 = out.size();

        // anomaly
        writeIndexWithMinWidth();

        int s3 = out.size();

        // data
        writeDataWithMinWidth();

        int s5 = out.size();

        reset();
//        System.out.println(s1);
//        System.out.println(s2 - s1);
//        System.out.println(s3 - s2);
//        System.out.println(s4 - s3);
//        System.out.println(s5 - s4);
//        System.out.println("############");
    }

    public static class IntRLEEncoder extends MyRleEncoder {
        private boolean firstValue;
        private int dataIndex = 0;
        private int preValue;
        private int curCount;
        private final int[] rleBlockBuffer;
        private final int[] lengthBlockBuffer;

        private final int[] indexBlockBuffer;

        /**
         * constructor of IntDeltaEncoder which is a sub-class of DeltaBinaryEncoder.
         */
        public IntRLEEncoder() {
            super();
            rleBlockBuffer = new int[blockSize];
            lengthBlockBuffer = new int[blockSize];
            indexBlockBuffer = new int[blockSize];
            encodingDataBuffer = new byte[blockSize * 4];
            encodingLengthBuffer = new byte[blockSize * 4];
            encodingIndexBuffer = new byte[blockSize * 4];
            reset();
        }

        @Override
        public void encode(int value, ByteArrayOutputStream out) {
            encodeValue(value, out);
        }

        /**
         * input a integer.
         *
         * @param value value to encode
         * @param out   the ByteArrayOutputStream which data encode into
         */
        public void encodeValue(int value, ByteArrayOutputStream out) {
            dataIndex++; // index++
            // exception: first value
            if (firstValue) {
                firstValue = false;
                preValue = value;
                curCount = 0;
            }
            if (preValue == value) {  // rle
                curCount++;
            } else {  // if not equal
                recordValue();
                // update
                preValue = value;
                curCount = 1;
            }

            if (dataIndex == blockSize) {
                flush(out);
            }
        }

        /**
         * record rle length and index
         */
        @Override
        protected void recordValue() {
            if (curCount < minRleRepeatNumber) {  // if repeat number < minRleRepeatNumber
                for (int i = 0; i < curCount; ++i) {
                    rleBlockBuffer[dataNum++] = preValue;
                }
                // record
                lengthBlockBuffer[lengthNum] = curCount;
                indexBlockBuffer[lengthNum++] = dataNum;
                rleBlockBuffer[dataNum++] = preValue;
            }
        }

        @Override
        protected void writeLengthToBytes(int i) {
            BytesUtils.intToBytes(lengthBlockBuffer[i], encodingLengthBuffer, lengthWidth * i, lengthWidth);
        }

        @Override
        protected void writeIndexToBytes(int i) {
            BytesUtils.intToBytes(indexBlockBuffer[i], encodingIndexBuffer, indexWidth * i, indexWidth);
        }

        @Override
        protected void writeDataToBytes(int i) {
            BytesUtils.intToBytes(rleBlockBuffer[i], encodingDataBuffer, dataWidth * i, dataWidth);
        }

        @Override
        protected void calculateBitWidthsForRLEBlockBuffer() {
            dataWidth = 0;
            for (int i = 0; i < dataNum; i++) {
                dataWidth = Math.max(dataWidth, getValueWidth(rleBlockBuffer[i]));
            }
        }

        @Override
        protected void calculateBitWidthsForLengthBlockBuffer() {
            lengthWidth = 0;
            for (int i = 0; i < lengthNum; i++) {
                lengthWidth = Math.max(lengthWidth, getValueWidth(lengthBlockBuffer[i]));
            }
        }

        @Override
        protected void calculateBitWidthsForIndexBlockBuffer() {
            indexWidth = 0;
            for (int i = 0; i < lengthNum; i++) {
                indexWidth = Math.max(indexWidth, getValueWidth(indexBlockBuffer[i]));
            }
        }

        private int getValueWidth(int v) {
            return 32 - Integer.numberOfLeadingZeros(v);
        }

        private int getValueWidth(long v) {
            return 64 - Long.numberOfLeadingZeros(v);
        }

        @Override
        protected void reset() {
            for (int i = 0; i < blockSize; i++) {
                rleBlockBuffer[i] = 0;
                lengthBlockBuffer[i] = 0;
                indexBlockBuffer[i] = 0;
            }
            firstValue = true;
            lengthNum = 0;
            dataNum = 0;
            dataIndex = 0;
        }

        @Override
        public int getOneItemMaxSize() {
            return 4;
        }

        @Override
        public long getMaxByteSize() {
            // The meaning of 24 is: index(4)+width(4)+minDeltaBase(4)+firstValue(4)
            return (long) 24 + dataNum * 4L;
        }
    }

    public static class LongRLEEncoder extends MyRleEncoder {
        private int dataIndex = 0;
        private long preValue;
        private int curCount;
        private final long[] rleBlockBuffer;
        private final int[] lengthBlockBuffer;
        private final int[] indexBlockBuffer;

        /**
         * constructor of LongDeltaEncoder which is a sub-class of DeltaBinaryEncoder.
         */
        public LongRLEEncoder() {
            super();
            rleBlockBuffer = new long[blockSize];
            lengthBlockBuffer = new int[blockSize];
            indexBlockBuffer = new int[blockSize];
            encodingDataBuffer = new byte[blockSize * 8];
            encodingLengthBuffer = new byte[blockSize * 4];
            encodingIndexBuffer = new byte[blockSize * 4];
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
            dataIndex++; // index++
            // exception: first value
            if (firstValue) {
                firstValue = false;
                preValue = value;
                curCount = 0;
            }
            if (preValue == value) {  // rle
                curCount++;
            } else {  // if not equal
                recordValue();
                // update
                preValue = value;
                curCount = 1;
            }

            if (dataIndex == blockSize) {
                flush(out);
            }
        }

        /**
         * record rle length and index
         */
        @Override
        protected void recordValue() {
            if (curCount < minRleRepeatNumber) {  // if repeat number < minRleRepeatNumber
                for (int i = 0; i < curCount; ++i) {
                    rleBlockBuffer[dataNum++] = preValue;
                }
            } else {  // if repeat number >= minRleRepeatNumber
                // record
                lengthBlockBuffer[lengthNum] = curCount;
                indexBlockBuffer[lengthNum++] = dataNum;
                rleBlockBuffer[dataNum++] = preValue;
            }
        }

        @Override
        protected void writeLengthToBytes(int i) {
            BytesUtils.intToBytes(lengthBlockBuffer[i], encodingLengthBuffer, lengthWidth * i, lengthWidth);
        }

        @Override
        protected void writeIndexToBytes(int i) {
            BytesUtils.intToBytes(indexBlockBuffer[i], encodingIndexBuffer, indexWidth * i, indexWidth);
        }

        @Override
        protected void writeDataToBytes(int i) {
            BytesUtils.longToBytes(rleBlockBuffer[i], encodingDataBuffer, dataWidth * i, dataWidth);
        }


        @Override
        protected void calculateBitWidthsForRLEBlockBuffer() {
            dataWidth = 0;
            for (int i = 0; i < dataNum; i++) {
                dataWidth = Math.max(dataWidth, getValueWidth(rleBlockBuffer[i]));
            }
        }

        @Override
        protected void calculateBitWidthsForLengthBlockBuffer() {
            lengthWidth = 0;
            for (int i = 0; i < lengthNum; i++) {
                lengthWidth = Math.max(lengthWidth, getValueWidth(lengthBlockBuffer[i]));
            }
        }

        @Override
        protected void calculateBitWidthsForIndexBlockBuffer() {
            indexWidth = 0;
            for (int i = 0; i < lengthNum; i++) {
                indexWidth = Math.max(indexWidth, getValueWidth(indexBlockBuffer[i]));
            }
        }

        private int getValueWidth(int v) {
            return 32 - Integer.numberOfLeadingZeros(v);
        }

        private int getValueWidth(long v) {
            return 64 - Long.numberOfLeadingZeros(v);
        }

        @Override
        protected void reset() {
            for (int i = 0; i < blockSize; i++) {
                rleBlockBuffer[i] = 0L;
                lengthBlockBuffer[i] = 0;
                indexBlockBuffer[i] = 0;
            }
            firstValue = true;
            lengthNum = 0;
            dataNum = 0;
            dataIndex = 0;
        }

        @Override
        public int getOneItemMaxSize() {
            return 8;
        }

        @Override
        public long getMaxByteSize() {
            // The meaning of 24 is: index(4)+width(4)+minDeltaBase(8)+firstValue(8)
            return (long) 24 + dataNum * 8L;
        }
    }
}
