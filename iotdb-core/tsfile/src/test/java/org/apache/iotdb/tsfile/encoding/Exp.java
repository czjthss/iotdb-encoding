package org.apache.iotdb.tsfile.encoding;

import io.airlift.slice.SizeOf;
import org.apache.iotdb.tsfile.common.conf.TSFileConfig;
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
    private static final String INPUT_DIR = "/Users/chenzijie/Documents/GitHub/data/input/compression/";
    private static final String OUTPUT_DIR = "/Users/chenzijie/Documents/GitHub/data/output/compression/";

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
    //            "ECG.csv",
//            "NOISE.csv",
    private static final String[] datasetFileList = {
//            "COSINE.csv",
//            "AUDIO.csv",
//            "GAS.csv",
//            "GPS.csv",
//            "HHAR.csv",
//            "POWER.csv",
//            "TEMP.csv",
//            "grid_value_from2020-11-29to2020-12-06_10543.csv",
//            "yinlian_value_from2020-11-10to2020-12-07_8437.csv",
//            "hangxin_c_from2019-08-11to2019-08-30_7428.csv",
//            "liantong_data_from2018-12-19to2019-01-31_8180.csv",
//            "guoshou_value_from2020-01-02to2020-01-21_7479.csv",
//            "power_5241600.csv",
//            "voltage_22825440.csv",
//            "ghi_10617120.csv",
            "weather_ghi_sfc_CMA.csv",
//            "weather_ghi_sfc_EC.csv",
    };

    // select encoding algorithms
    private static final TSEncoding[] encodingList = {
//            TSEncoding.PLAIN,
            TSEncoding.STD,
//            TSEncoding.MyRLE,
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
//            "MyRLE",
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
//            "LZMA2",
    };

    private static void store() throws Exception {
        // encoding and scaling
        long start = System.nanoTime();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Encoder encoder;
        // use scaling+LONG or DOUBLE
        if (encodingMethod == TSEncoding.STD || encodingMethod == TSEncoding.TS_2DIFF || encodingMethod == TSEncoding.RLE || encodingMethod == TSEncoding.MyRLE || encodingMethod == TSEncoding.ZIGZAG) {
            encoder = TSEncodingBuilder.getEncodingBuilder(encodingMethod).getEncoder(TSDataType.INT64);
            for (double value : original) {
                encoder.encode((long) (value * Math.pow(10, scale)), buffer);
            }
        } else {
            encoder = TSEncodingBuilder.getEncodingBuilder(encodingMethod).getEncoder(TSDataType.DOUBLE);
            for (double value : original) {
                encoder.encode(value, buffer);
            }
        }
        encoder.flush(buffer);

        encoded = buffer.toByteArray();
        ICompressor compressor = ICompressor.getCompressor(compressionMethod);
        compressed = compressor.compress(encoded);

        encode_time = System.nanoTime() - start;
    }

    private static void query() throws Exception {
        // decoding
        IUnCompressor unCompressor = IUnCompressor.getUnCompressor(compressionMethod);
        decoded = new double[original.length];
        int decoded_idx = 0;

        long start = System.nanoTime();
        uncompressed = compressed.clone();
        uncompressed = unCompressor.uncompress(uncompressed);
        ByteBuffer ebuffer = ByteBuffer.wrap(uncompressed); // uncompressed
        Decoder decoder = Decoder.getDecoderByType(encodingMethod, TSDataType.INT64);
        while (decoder.hasNext(ebuffer)) {
            decoded[decoded_idx++] = decoder.readLong(ebuffer) / Math.pow(10, scale);
        }
        for (int i = 0; i < original.length; ++i) {
            if (original[i] - decoded[i] > 1e-3) {
                System.out.println("WRONG");
                System.out.println(original[i] + " " + decoded[i]);
                break;
            }
            if (i == original.length - 1) {
                System.out.println("CORRECT");
            }
        }
//        decode_time = System.nanoTime() - start;
    }

    public static void comparison() throws Exception {
//        int dataLen = Integer.MAX_VALUE;
        int dataLen = 52416;
        int stdBlockSize = 100000;
        int ts2diffBlockSize = 12800;
        int rleBlockSize = 524160;  // 288

        int[] blockSizeArray = new int[10];
        for (int i = 6; i < 16; i++) {
            blockSizeArray[i - 6] = (int) Math.pow(10, 0.25 * i);
        }

//        for (int i = 11; i < 21; i++) {
//            blockSizeArray[i -11] = (int) Math.pow(10, 0.25 * i);
//        }

//        for (int value : blockSizeArray) {
//            stdBlockSize = value;
//            ts2diffBlockSize = value;

        for (String datafile : datasetFileList) {
            System.out.println("###################");
//            System.out.println(value);
            System.out.println(datafile);
            original = loadTimeSeriesData(INPUT_DIR + datafile, dataLen);
//                System.out.println("original: " + original.length);

//            original = loadSquareWave(1000000, 144, 10000.);
            if (datafile.equals("liantong_data_from2018-12-19to2019-01-31_8180.csv") || datafile.equals("power_5241600.csv")) {
                period = 144;
            } else if (datafile.equals("guoshou_value_from2020-01-02to2020-01-21_7479.csv") || datafile.equals("hangxin_c_from2019-08-11to2019-08-30_7428.csv")
                    || datafile.equals("yinlian_value_from2020-11-10to2020-12-07_8437.csv")) {
                period = 288;
            } else if (datafile.equals("voltage_22825440.csv") || datafile.equals("grid_value_from2020-11-29to2020-12-06_10543.csv")) {
                period = 1440;
            } else if (datafile.equals("weather_ghi_sfc_CMA.csv") || datafile.equals("weather_ghi_sfc_EC.csv")) {
                period = 96;
            } else {
                period = getPeriod(original); // 1440
            }
//            period = 144;
            scale = getScale(original);
//            scale = 1;

            TSFileDescriptor.getInstance().getConfig().setPeriodLength(period);
            TSFileDescriptor.getInstance().getConfig().setStdBlockSize(stdBlockSize);
            TSFileDescriptor.getInstance().getConfig().setRleBlockSize(rleBlockSize);
            TSFileDescriptor.getInstance().getConfig().setTs2diffBlockSize(ts2diffBlockSize);

            double ratio;
            for (int idx = 0; idx < encodingList.length; idx++) {
                // choose
                encodingMethod = encodingList[idx];
                compressionMethod = CompressionType.UNCOMPRESSED;
                store();
//                query();
                // calculate compression ratio
                ratio = (double) compressed.length / (double) (original.length * Double.BYTES);
                System.out.println(encodingNameList[idx] + "\t" + String.format("%.3f", ratio) + "\t" + encode_time / 1e6 + "\t" + decode_time / 1e6);
//                System.out.print(String.format("%.3f", ratio) + " & ");
            }

            for (int idx = 0; idx < compressionList.length; idx++) {
                // choose
                encodingMethod = TSEncoding.PLAIN;
                compressionMethod = compressionList[idx];
                store();
//                query();
                // calculate compression ratio
                ratio = (double) compressed.length / (double) (original.length * Double.BYTES);
                System.out.println(compressionNameList[idx] + "\t" + String.format("%.3f", ratio) + "\t" + encode_time / 1e6 + "\t" + decode_time / 1e6);
//                System.out.print(String.format("%.3f", encode_time / 1e6) + " & ");
            }
            System.out.println();
//            }
        }
    }

    public static void main(String[] args) throws Exception {
        comparison();
    }
}
