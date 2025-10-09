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

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.apache.iotdb.tsfile.encoding.Utils.loadTimeSeriesDataFromCsv;
import static org.apache.iotdb.tsfile.encoding.Utils.loadTimeSeriesDataFromJson;
import static org.apache.iotdb.tsfile.encoding.Utils.RtnDataFromJson;
import static org.apache.iotdb.tsfile.encoding.Utils.loadSquareWave;
import static org.apache.iotdb.tsfile.encoding.Utils.getScale;
import static org.apache.iotdb.tsfile.encoding.Utils.getPeriod;

public class Exp {
    private static final String INPUT_DIR = "";
    private static final String OUTPUT_DIR = "";

    // need to provide
    private static double[] original;
    private static TSEncoding encodingMethod;
    private static CompressionType compressionMethod;

    // record array
    private static byte[] encoded;
    private static byte[] compressed;
    private static byte[] uncompressed;
    private static double[] decoded;

    // record results
    private static long encode_time;
    private static long decode_time;

    private static double ratio;

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

            "power_52416.json",
            "weather_ghi_sfc_CMA.json",
            "yinlian_value_from2020-11-10to2020-12-07_8437.json",
            "liantong_data_from2018-12-19to2019-01-31_8180.json",
            "TEMP.json",
            "weather_T (degC)_1159312.json",
            "traffic_0_15140472.json",
            "electricity_1_8469888.json",

//            "grid_value_from2020-11-29to2020-12-06_10543.json",
//            "guoshou_value_from2020-01-02to2020-01-21_7479.json",
//            "exchange_0_68292.json",
//            "weather_ghi_sfc_EC.csv",
    };
    private static final String[] datasetFileNameList = {
            "Power",
            "Ghi",
            "Insurance",
            "Telecom",
            "Temp",
            "Weather",
            "Traffic",
            "Electricity",

//            "Grid",
//            "Bank",
//            "Exchange",
    };

    // select encoding algorithms
    private static final TSEncoding[] encodingList = {
//            TSEncoding.PLAIN,
//            TSEncoding.MyRLE,

            TSEncoding.STD,
            TSEncoding.TS_2DIFF,
//            TSEncoding.RLE,
//            TSEncoding.SPRINTZ,
//            TSEncoding.GORILLA,
//            TSEncoding.RLBE,
//            TSEncoding.CHIMP,
//            TSEncoding.ZIGZAG,
//            TSEncoding.BUFF,

//            TSEncoding.ZIGZAG,
//            TSEncoding.DICTIONARY,
    };

    private static final String[] encodingNameList = {
//            "PLAIN",
//            "MyRLE",

            "STD",
            "TS_2DIFF",
            "RLE",
            "SPRINTZ",
            "GORILLA",
            "RLBE",
            "CHIMP",
            "ZIGZAG",
            "BUFF",
    };

    // select compression algorithms
    private static final CompressionType[] compressionList = {
//            CompressionType.UNCOMPRESSED,
//            CompressionType.LZ4,
//            CompressionType.GZIP,
//            CompressionType.SNAPPY,
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

    public static void recordRatio(String string) throws Exception {
        FileWriter fileWritter = new FileWriter(OUTPUT_DIR + "ratio.txt", true);
        BufferedWriter bw = new BufferedWriter(fileWritter);
        bw.write(string);
        bw.close();
    }

    private static void store() throws Exception {
        long start = System.nanoTime();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Encoder encoder;

        // encoding
        encoder = TSEncodingBuilder.getEncodingBuilder(encodingMethod).getEncoder(TSDataType.DOUBLE);
        for (double value : original) {
            encoder.encode(value, buffer);
        }
        encoder.flush(buffer);

        // compression
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
        Decoder decoder = Decoder.getDecoderByType(encodingMethod, TSDataType.DOUBLE);
        while (decoder.hasNext(ebuffer)) {
            decoded[decoded_idx++] = decoder.readDouble(ebuffer);
        }

//        check();

        decode_time = System.nanoTime() - start;
    }

    private static void check() {
        for (int i = 0; i < original.length; ++i) {
            if (Math.abs(original[i] - decoded[i]) > 1e-8) {
                System.out.println("WRONG");
                System.out.println(i + " " + original[i] + " " + decoded[i]);
                break;
            }
            if (i == original.length - 1) {
                System.out.println("CORRECT");
            }
        }
    }

    public static void dataset(int fileIdx) throws Exception {
        String datafile = datasetFileList[fileIdx];
        // print dataset file name
        System.out.print(datasetFileNameList[fileIdx] + " & ");
        // read dataset file
        RtnDataFromJson rtnDataFromJson = loadTimeSeriesDataFromJson(INPUT_DIR + datafile, Integer.MAX_VALUE);
        original = rtnDataFromJson.ts;
        int period = rtnDataFromJson.period;
//            period = getPeriod(original);
        int scale = getScale(original);

        for (int idx = 0; idx < original.length; idx++) {
            original[idx] = Math.round(original[idx] * Math.pow(10, scale)) / Math.pow(10, scale);
        }

        TSFileDescriptor.getInstance().getConfig().setPeriodLength(period);
        TSFileDescriptor.getInstance().getConfig().setScale(scale);
    }


    public static void running() throws Exception {
        for (TSEncoding encoding : encodingList) {
            // choose
            encodingMethod = encoding;
            compressionMethod = CompressionType.UNCOMPRESSED;
            store();
            query();
            // calculate compression ratio
            ratio = (double) compressed.length / (double) (original.length * Double.BYTES);
//                System.out.println(encodingNameList[idx] + "\t" + String.format("%.3f", ratio) + "\t" + (encode_time) / original.length / 1e3 + "\t" + decode_time / 1e6);
            System.out.print(String.format("%.3f", ratio) + " & ");
//                System.out.print(String.format("%.3f", (decode_time) / original.length / 1e3) + " & ");
            recordRatio(String.format("%.3f", ratio) + ",");
        }

        for (CompressionType compressionType : compressionList) {
            // choose
            encodingMethod = TSEncoding.PLAIN;
            compressionMethod = compressionType;
            store();
            query();
            // calculate compression ratio
            ratio = (double) compressed.length / (double) (original.length * Double.BYTES);
//                System.out.println(compressionNameList[idx] + "\t" + String.format("%.3f", ratio) + "\t" + (encode_time) / original.length / 1e3 + "\t" + decode_time / 1e6);
            System.out.print(String.format("%.3f", ratio) + " & ");
//                System.out.print(String.format("%.3f", (decode_time) / original.length / 1e3) + " & ");
            recordRatio(String.format("%.3f", ratio) + ",");
        }
        System.out.println("\\\\");
        recordRatio("\n");
    }

    public static void main_block_size() throws Exception {
//        int dataLen = Integer.MAX_VALUE;
        int stdBlockSize = 5000; // 100000
        int ts2diffBlockSize = 5000; // 12800
        int rleBlockSize = 10000;  // 288

        int[] blockSizeArray = new int[10];
        int start = 9;
        for (int i = start; i < start + 10; i++) {
            blockSizeArray[i - start] = (int) Math.pow(10, 0.25 * i);
        }
        for (int fileIdx = 0; fileIdx < datasetFileList.length; fileIdx++) {
            recordRatio("block;" + datasetFileNameList[fileIdx] + ";" + Arrays.stream(blockSizeArray).mapToObj(String::valueOf).collect(Collectors.joining(",")) + "\n");
            dataset(fileIdx);

            for (int value : blockSizeArray) {
                stdBlockSize = value;
                ts2diffBlockSize = value;
                TSFileDescriptor.getInstance().getConfig().setStdBlockSize(stdBlockSize);
                TSFileDescriptor.getInstance().getConfig().setRleBlockSize(rleBlockSize);
                TSFileDescriptor.getInstance().getConfig().setTs2diffBlockSize(ts2diffBlockSize);
                // run encoding methods
                running();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        main_block_size();
    }
}
