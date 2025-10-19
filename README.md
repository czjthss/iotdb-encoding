# Seasonal Time-series Compression with Decomposition (STCD)


The STCD algorithm has been integrated into Apache IoTDB:

- `iotdb-core/tsfile/src/main/java/org/apache/iotdb/tsfile/encoding/encoder/STDEncoder.java` contains the compression code.
- `iotdb-core/tsfile/src/main/java/org/apache/iotdb/tsfile/encoding/decoder/STDDecoder.java` contains the decompression code.

## Usage

Dev Environment:

- Java 8
- Maven

## Run

- For practical use, compile IoTDB and select the `STD` encoding method when storing data. Refer to `README_IOTDB` for more details.
- For experiments, run the main function at `iotdb-core/tsfile/src/test/java/org/apache/iotdb/tsfile/encoding/Exp.java`.
