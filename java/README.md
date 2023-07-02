# Fill Memory

This directory contains a simple Java script which will consume up to 1 GB,
in chunks of 10MB/allocation round.

Note that the chunk size is not accurate but only a rough estimate.
That is due to the overhead of the JVM, thus there are more bytes consumed than the output states.

You can adjust the limit as well as the chunk size at the top of `FillMemory.jar`.

```java
private final static int maxLimitInMiB = 50;
private final static int mbToAllocatePerRound = 25;
```

## Build

Build `FillMemory.jar` using the script `build.sh`.

```bash
./build.sh
```

The output file is `build/FillMemory.jar`.

It will build with your installed java version.
To check which version you have installed, run

```bash
$ java -version
openjdk version "17.0.7" 2023-04-18 LTS
OpenJDK Runtime Environment Corretto-17.0.7.7.1 (build 17.0.7+7-LTS)
OpenJDK 64-Bit Server VM Corretto-17.0.7.7.1 (build 17.0.7+7-LTS, mixed mode, sharing)
```

On a personal note, I highly recommend [sdkman!](https://sdkman.io/) for managing java versions.

## Run the Memory Tool

Run the `java` command with respective parameters to execute the `FillMemory.jar`.

```bash
# -verbose:gc   -   Enables info messages about the GC lifecycle
# -Xmx{size}m   -   Configures the JVM to have a max heap size of {size} Megabytes
java -verbose:gc -Xmx500m -jar build/FillMemory.jar
```

## Run the Memory Tool in Docker

With below snippet, you can start a Docker container running Java 17.
Note, if you built the JAR with a different version, you need to replace the tag with that version.
It limits the container memory size to 100MB, and with the default configuration a OOM is happening.

```bash
docker container run -it \
    -m 100m  --memory-swap 100m \
    -v $(pwd)/build/FillMemory.jar:/tmp/fill-memory/run.jar \
    --rm amazoncorretto:17 \
    java -verbose:gc -jar /tmp/fill-memory/run.jar
```