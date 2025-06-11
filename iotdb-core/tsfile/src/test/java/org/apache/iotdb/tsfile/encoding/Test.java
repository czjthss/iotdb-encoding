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

import static org.apache.iotdb.tsfile.encoding.Utils.getPeriod;
import static org.apache.iotdb.tsfile.encoding.Utils.loadTimeSeriesDataFromCsv;

public class Test {
    private static final String INPUT_DIR = "/Users/chenzijie/Documents/GitHub/data/input/compression/";

    private static double[] original;
    private static TSEncoding encodingMethod;
    private static CompressionType compressionMethod;

    // parameter: need to set for std
    private static int scale;
    // record array
    private static byte[] encoded;
    private static byte[] compressed;
    private static byte[] uncompressed;
    private static double[] decoded;

    public static void main(String[] args) throws Exception {
        int dataLen = Integer.MAX_VALUE;

        int stdBlockSize = 10;

        original = loadTimeSeriesDataFromCsv(INPUT_DIR + "AUDIO.csv", Integer.MAX_VALUE);

        TSEncoding encodingMethod = TSEncoding.MyRLE;

        TSFileDescriptor.getInstance().getConfig().setRleBlockSize(10);
        scale = 10;

        // encode
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        Encoder encoder = TSEncodingBuilder.getEncodingBuilder(TSEncoding.MyRLE).getEncoder(TSDataType.INT64);
        for (double value : original) {
            encoder.encode((long) (value * Math.pow(10, scale)), buffer);
        }
        encoder.flush(buffer);

        encoded = buffer.toByteArray();

        ICompressor compressor = ICompressor.getCompressor(CompressionType.UNCOMPRESSED);
        compressed = compressor.compress(encoded);

        decoded = new double[original.length + 1];

        IUnCompressor unCompressor = IUnCompressor.getUnCompressor(CompressionType.UNCOMPRESSED);
        uncompressed = compressed.clone();
        uncompressed = unCompressor.uncompress(uncompressed);

        // decode
        int decoded_idx = 0;
        Decoder decoder = Decoder.getDecoderByType(TSEncoding.MyRLE, TSDataType.INT64);
        ByteBuffer ebuffer = ByteBuffer.wrap(uncompressed);

        while (decoder.hasNext(ebuffer)) {
            decoded[decoded_idx++] = decoder.readLong(ebuffer) / Math.pow(10, scale);
        }

//        for (int idx = 0; idx < original.length; idx++) {
//            System.out.print(decoded[idx] + " ");
//        }
//        System.out.println();

        // test
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
    }
}
