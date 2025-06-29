/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.tsfile.encoding.decoder;

import org.apache.iotdb.tsfile.exception.encoding.TsFileDecodingException;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.utils.Binary;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

public abstract class Decoder {

    private static final String ERROR_MSG = "Decoder not found: %s , DataType is : %s";

    private TSEncoding type;

    public Decoder(TSEncoding type) {
        this.type = type;
    }

    public void setType(TSEncoding type) {
        this.type = type;
    }

    public TSEncoding getType() {
        return type;
    }

    public static Decoder getDecoderByType(TSEncoding encoding, TSDataType dataType) {
        switch (encoding) {
            case PLAIN:
                return new PlainDecoder();
            case RLE:
                switch (dataType) {
                    case BOOLEAN:
                    case INT32:
                        return new IntRleDecoder();
                    case INT64:
                    case VECTOR:
                        return new LongRleDecoder();
                    case FLOAT:
                    case DOUBLE:
                        return new FloatDecoder(TSEncoding.valueOf(encoding.toString()), dataType);
                    default:
                        throw new TsFileDecodingException(String.format(ERROR_MSG, encoding, dataType));
                }
            case TS_2DIFF:
                switch (dataType) {
                    case INT32:
                        return new DeltaBinaryDecoder.IntDeltaDecoder();
                    case INT64:
                    case VECTOR:
                        return new DeltaBinaryDecoder.LongDeltaDecoder();
                    case FLOAT:
                    case DOUBLE:
                        return new FloatDecoder(TSEncoding.valueOf(encoding.toString()), dataType);
                    default:
                        throw new TsFileDecodingException(String.format(ERROR_MSG, encoding, dataType));
                }
            case GORILLA_V1:
                switch (dataType) {
                    case FLOAT:
                        return new SinglePrecisionDecoderV1();
                    case DOUBLE:
                        return new DoublePrecisionDecoderV1();
                    default:
                        throw new TsFileDecodingException(String.format(ERROR_MSG, encoding, dataType));
                }
            case REGULAR:
                switch (dataType) {
                    case INT32:
                        return new RegularDataDecoder.IntRegularDecoder();
                    case INT64:
                    case VECTOR:
                        return new RegularDataDecoder.LongRegularDecoder();
                    default:
                        throw new TsFileDecodingException(String.format(ERROR_MSG, encoding, dataType));
                }
            case GORILLA:
                switch (dataType) {
                    case FLOAT:
                        return new SinglePrecisionDecoderV2();
                    case DOUBLE:
                        return new DoublePrecisionDecoderV2();
                    case INT32:
                        return new IntGorillaDecoder();
                    case INT64:
                    case VECTOR:
                        return new LongGorillaDecoder();
                    default:
                        throw new TsFileDecodingException(String.format(ERROR_MSG, encoding, dataType));
                }
            case DICTIONARY:
                return new DictionaryDecoder();
            case ZIGZAG:
                switch (dataType) {
                    case INT32:
                        return new IntZigzagDecoder();
                    case INT64:
                        return new LongZigzagDecoder();
                    case FLOAT:
                        return new IntZigzagDecoder.FloatZigzagDecoder();
                    case DOUBLE:
                        return new LongZigzagDecoder.DoubleZigzagDecoder();
                    default:
                        throw new TsFileDecodingException(String.format(ERROR_MSG, encoding, dataType));
                }
            case CHIMP:
                switch (dataType) {
                    case FLOAT:
                        return new SinglePrecisionChimpDecoder();
                    case DOUBLE:
                        return new DoublePrecisionChimpDecoder();
                    case INT32:
                        return new IntChimpDecoder();
                    case INT64:
                    case VECTOR:
                        return new LongChimpDecoder();
                    default:
                        throw new TsFileDecodingException(String.format(ERROR_MSG, encoding, dataType));
                }
            case SPRINTZ:
                switch (dataType) {
                    case INT32:
                        return new IntSprintzDecoder();
                    case INT64:
                        return new LongSprintzDecoder();
                    case FLOAT:
                        return new FloatSprintzDecoder();
                    case DOUBLE:
                        return new DoubleSprintzDecoder();
                    default:
                        throw new TsFileDecodingException(String.format(ERROR_MSG, encoding, dataType));
                }
            case RLBE:
                switch (dataType) {
                    case INT32:
                        return new IntRLBEDecoder();
                    case INT64:
                        return new LongRLBEDecoder();
                    case FLOAT:
                        return new FloatRLBEDecoder();
                    case DOUBLE:
                        return new DoubleRLBEDecoder();
                    default:
                        throw new TsFileDecodingException(String.format(ERROR_MSG, encoding, dataType));
                }
            case BUFF:
                switch (dataType) {
                    case INT32:
                        return new IntBUFFDecoder();
                    case INT64:
                        return new LongBUFFDecoder();
                    case FLOAT:
                        return new FloatBUFFDecoder();
                    case DOUBLE:
                        return new DoubleBUFFDecoder();
                    default:
                        throw new TsFileDecodingException(String.format(ERROR_MSG, encoding, dataType));
                }
            case STD:
                switch (dataType) {
                    case INT32:
                        return new STDDecoder.IntSTDDecoder();
                    case INT64:
                        return new STDDecoder.LongSTDDecoder();
                    case FLOAT:
                        return new STDDecoder.FloatSTDDecoder();
                    case DOUBLE:
                        return new STDDecoder.DoubleSTDDecoder();
                    default:
                        throw new TsFileDecodingException(String.format(ERROR_MSG, encoding, dataType));
                }
            case MyRLE:
                switch (dataType) {
                    case INT32:
                        return new MyRLEDecoder.IntRLEDecoder();
                    case INT64:
                        return new MyRLEDecoder.LongRLEDecoder();
                    default:
                        throw new TsFileDecodingException(String.format(ERROR_MSG, encoding, dataType));
                }
            default:
                throw new TsFileDecodingException(String.format(ERROR_MSG, encoding, dataType));
        }
    }

    public int readInt(ByteBuffer buffer) {
        throw new TsFileDecodingException("Method readInt is not supported by Decoder");
    }

    public boolean readBoolean(ByteBuffer buffer) {
        throw new TsFileDecodingException("Method readBoolean is not supported by Decoder");
    }

    public short readShort(ByteBuffer buffer) {
        throw new TsFileDecodingException("Method readShort is not supported by Decoder");
    }

    public long readLong(ByteBuffer buffer) {
        throw new TsFileDecodingException("Method readLong is not supported by Decoder");
    }

    public float readFloat(ByteBuffer buffer) {
        throw new TsFileDecodingException("Method readFloat is not supported by Decoder");
    }

    public double readDouble(ByteBuffer buffer) {
        throw new TsFileDecodingException("Method readDouble is not supported by Decoder");
    }

    public Binary readBinary(ByteBuffer buffer) {
        throw new TsFileDecodingException("Method readBinary is not supported by Decoder");
    }

    public BigDecimal readBigDecimal(ByteBuffer buffer) {
        throw new TsFileDecodingException("Method readBigDecimal is not supported by Decoder");
    }

    public abstract boolean hasNext(ByteBuffer buffer) throws IOException;

    public abstract void reset();
}
