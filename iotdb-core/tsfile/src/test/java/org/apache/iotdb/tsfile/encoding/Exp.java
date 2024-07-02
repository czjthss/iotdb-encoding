package org.apache.iotdb.tsfile.encoding;

import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.compress.ICompressor;
import org.apache.iotdb.tsfile.compress.IUnCompressor;
import org.apache.iotdb.tsfile.encoding.decoder.Decoder;
import org.apache.iotdb.tsfile.encoding.encoder.Encoder;
import org.apache.iotdb.tsfile.encoding.encoder.TSEncodingBuilder;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import static org.apache.iotdb.tsfile.encoding.Utils.loadTimeSeriesData;
import static org.apache.iotdb.tsfile.encoding.Utils.loadSquareWave;
import static org.apache.iotdb.tsfile.encoding.Utils.getScale;
import static org.apache.iotdb.tsfile.encoding.Utils.getPeriod;
import static org.apache.iotdb.tsfile.encoding.Utils.checkCorrectness;

public class Exp {
    private static final String INPUT_DIR = "F:/data/";
    private static final String OUTPUT_DIR = "F:/data/";

    // need to provide
    private static double[] original;
    private static TSEncoding encodingMethod;
    private static CompressionType compressionMethod;

    // parameter: need to set for std
    private static int scale;
    private static int period;
    // record array
    private static byte[] encoded;
    private static byte[] compressed;
    private static byte[] uncompressed;
    private static double[] decoded;

    // record time cost
    private static long encode_time;
    private static long decode_time;

    // dataset
    private static final String[] datasetFileList = {
//            "COSINE.csv",
//            "AUDIO.csv",
//            "ECG.csv",
//            "GAS.csv",
            "GPS.csv",
//            "HHAR.csv",
//            "NOISE.csv",
            "POWER.csv",
            "TEMP.csv",
//            "power_5241600.csv",
//            "voltage_22825440.csv",
//            "ghi_10617120.csv"
    };

    // select encoding algorithms
    private static final TSEncoding[] encodingList = {
//            TSEncoding.PLAIN,
            TSEncoding.STD,
            TSEncoding.TS_2DIFF,
            TSEncoding.RLE,
            TSEncoding.SPRINTZ,
            TSEncoding.GORILLA,
            TSEncoding.RLBE,
            TSEncoding.CHIMP,
            TSEncoding.ZIGZAG,

//            TSEncoding.BUFF,
//            TSEncoding.ZIGZAG,
//            TSEncoding.DICTIONARY,
    };

    private static final String[] encodingNameList = {
//            "PLAIN",
            "STD",
            "TS_2DIFF",
            "RLE",
            "SPRINTZ",
            "GORILLA",
            "RLBE",
            "CHIMP",
            "ZIGZAG",
    };

    // select compression algorithms
    private static final CompressionType[] compressionList = {
//            CompressionType.UNCOMPRESSED,
            CompressionType.LZ4,
//            CompressionType.GZIP,
            CompressionType.SNAPPY,
//            CompressionType.ZSTD,
//            CompressionType.LZMA2,
    };

    private static final String[] compressionNameList = {
//            "UNCOMPRESSED",
            "LZ4",
//            "GZIP",
            "SNAPPY",
//            "ZSTD",
            "LZMA2",
    };

    private static void store() throws Exception {
        // encoding and scaling
        long start = System.nanoTime();
        Encoder encoder = TSEncodingBuilder.getEncodingBuilder(encodingMethod).getEncoder(TSDataType.INT64);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (double value : original) {
            encoder.encode((long) (value * Math.pow(10, scale)), buffer);
        }
        encoder.flush(buffer);
        encode_time = System.nanoTime() - start;

        encoded = buffer.toByteArray();
        ICompressor compressor = ICompressor.getCompressor(compressionMethod);
        compressed = compressor.compress(encoded);
    }

    private static void query() throws Exception {
        // decoding
        IUnCompressor unCompressor = IUnCompressor.getUnCompressor(compressionMethod);
        decoded = new double[original.length];
        int decoded_idx = 0;

        uncompressed = compressed.clone();
        uncompressed = unCompressor.uncompress(uncompressed);

        long start = System.nanoTime();
        ByteBuffer ebuffer = ByteBuffer.wrap(uncompressed);
        Decoder decoder = Decoder.getDecoderByType(encodingMethod, TSDataType.INT64);
        while (decoder.hasNext(ebuffer)) {
            decoded[decoded_idx++] = decoder.readLong(ebuffer) / Math.pow(10, scale);
        }
        decode_time = System.nanoTime() - start;
    }

    public static void comparison() throws Exception {
        int dataLen = Integer.MAX_VALUE;
        int stdBlockSize = 100000;

        for (String datafile : datasetFileList) {
            System.out.println("###################");
            System.out.println(datafile);
            original = loadTimeSeriesData(INPUT_DIR + datafile, dataLen);
//            original = loadSquareWave(1000000, 144, 10000.);
            period = getPeriod(original); // 1440
//            period = 144;
            scale = getScale(original);

            TSFileDescriptor.getInstance().getConfig().setPeriodLength(period);
            TSFileDescriptor.getInstance().getConfig().setStdBlockSize(stdBlockSize);

            double ratio;
            for (int idx = 0; idx < encodingList.length; idx++) {
                // choose
                encodingMethod = encodingList[idx];
                compressionMethod = CompressionType.UNCOMPRESSED;
                store();
                query();
                // calculate compression ratio
                ratio = (double) compressed.length / (double) (original.length * Double.BYTES);
                System.out.println(encodingNameList[idx] + "\t" + ratio + "\t" + encode_time / 1e6 + "\t" + decode_time / 1e6);
            }

//            for (int idx = 0; idx < compressionList.length; idx++) {
//                // choose
//                encodingMethod = TSEncoding.PLAIN;
//                compressionMethod = compressionList[idx];
//                store();
//                query();
//                // calculate compression ratio
//                ratio = (double) compressed.length / (double) (original.length * Double.BYTES);
//                System.out.println(compressionNameList[idx] + "\t" + ratio + "\t" + encode_time / 1e6 + "\t" + decode_time / 1e6);
//            }
        }
    }

    public static void main(String[] args) throws Exception {
        comparison();
    }
}
