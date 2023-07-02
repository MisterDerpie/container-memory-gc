# README

## What is this about?

This repository contains Java and Go (Golang) code for consuming memory in certain patterns.
Besides consuming the memory, the Java program logs its JVM memory consumption, and the
Go program logs data about the heap used by it.

## Why?

The question we wanted to know an answer to is

> Are Go binaries or JVMs aware of the memory restrictions when running inside a container?

and a more fine grained question

> Will the GC be force-invoked when reaching the container limit, to prevent Out-Of-Memory (OOM)?

Running a program inside a container, it is possible to specify limits for their resource consumption.
In Linux, this can be achieved via so called Control Groups, see [man7.org/cgroups](https://man7.org/linux/man-pages/man7/cgroups.7.html).

> Control groups, usually referred to as cgroups, are a Linux
kernel feature which allow processes to be organized into
hierarchical groups whose usage of various types of resources can
then be limited and monitored.

A user may specify a limit of 100MB to a container and would thus expect their programs to only ever be aware of these 100MB.
That is not the case, in fact, from within a container, the memory of the host is still visible.
To prove that, a very easy experiment can be conducted, using [stress-ng](https://manpages.ubuntu.com/manpages/xenial/man1/stress-ng.1.html):

```txt
$ docker container run -it -m 100m --rm alpine /bin/ash
/ # cat /proc/meminfo
MemTotal:       32725452 kB
MemFree:        25121288 kB
MemAvailable:   29299076 kB
...

/ # apk update && apk add stress-ng
...
/ # stress-ng -vm 1 --vm-bytes 50M -t 1s
stress-ng: debug: [543] RAM total: 31.2G, RAM free: 22.9G, swap free: 2.0G
...
stress-ng: info:  [543] successful run completed in 1.04s
/ # stress-ng -vm 1 --vm-bytes 200M -t 1s
stress-ng: debug: [547] RAM total: 31.2G, RAM free: 23.0G, swap free: 2.0G
...
stress-ng: debug: [547] 1 stressor started
...
stress-ng: debug: [548] stress-ng-vm: child died: signal 9 'SIGKILL' (instance 0)
stress-ng: debug: [548] stress-ng-vm: assuming killed by OOM killer, restarting again (instance 0)
...
stress-ng: info:  [547] successful run completed in 1.00s
```

As stated, we're seeing the host memory from `/proc/info` and `stress-ng` reports the host's memory too.

## Tests

The tests are conducted using two programs that allocate memory.

In the Java program (inside the subfolder `java/`), increasing chunks of memory are allocated in a for-loop.
With each iteration, the chunk of allocated memory increases, and the previously requested chunk becomes orphaned.
The program was compiled using Amazon Corretto 17 (`OpenJDK Runtime Environment Corretto-17.0.7.7.1 (build 17.0.7+7-LTS)OpenJDK Runtime Environment Corretto-17.0.7.7.1 (build 17.0.7+7-LTS`).

For the Go program (inside the subfolder `golang/`), the core of requesting chunks of memory in a for-loop stays the same.
What differs though is that the chunk is not increasing with each iteration, but randomly chosen in the interval of `[1, maxSizeInMiB + 1]`.
It moreover supports two CLI parameters, `-f` to force the GC to kick in before allocation, and `-d` to disable automatic GC runs.
It was built using Go 1.20.5 (`go version go1.20.5 linux/amd64`).

Each program was then started using Docker with a limited amount of memory.
This can be achieved by specifying the `-m` parameter in conjunction with `--memory-swap`, limits the memory and disables swapping.
The Go binary was furthermore invoked with `-f`, `-d` and `-f -d`.

### Java

```bash
$ docker container run -it \
    -m {limit}m --memory-swap {limit}m \
    -v $(pwd)/build/FillMemory.jar:/tmp/fill-memory/run.jar \
    --rm amazoncorretto:17 \
    java -verbose:gc -jar /tmp/fill-memory/run.jar
```

### Go

```bash
$ docker container run -it \
    -e GODEBUG=gctrace=1 \
    -m {limit}m --memory-swap {limit}m \
    -v $(pwd)/container-memory-gc:/tmp/fill-memory/container-memory-gc \
    --rm alpine \
    /tmp/fill-memory/container-memory-gc -f -d
```

## Results

Reciting the question from the top

> Are Go binaries or JVMs aware of the memory restrictions when running inside a container?

the answers in short are:

- Java: Yes
- Go: Maybe, there is no data indicating it, but also no data refuting it.

> Will the GC be force-invoked when reaching the container limit, to prevent Out-Of-Memory (OOM)?

- Java: Yes, that follows as the JVM is aware of the container's memory limit.
As pointed out below, the default limit of the JVM heap is less than the memory allocated to the container. 
- Go: No

For the long answers, continue reading.

### Java

As the Java program was the first one built, it will be covered first.
The test started with the following configuration:

```txt
Allocation Limit:   200MB
Allocation/Round:   10MB
```

and produced the following results

```txt
Max Heap Size:      13MB    # 25 MB     failed
Max Heap Size:      48MB    # 100 MB    failed
Max Heap Size:      121MB   # 250 MB    failed
Max Heap Size:      121MB   # 500 MB    failed
Max Heap Size:      241MB   # 1000 MB   failed
Max Heap Size:      500MB   # 2000 MB   succeeded
```

It was immediately visible that the output of the max heap size ([Runtime.maxMemory](https://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html#maxMemory--)) seems to be a fraction of what the container's limit is.
Therefore, the question whether the JVM is aware of the container's memory limit can be answered with yes.

To get an understanding where these values come from, I ran the following commands

```bash
docker container run -m100m --memory-swap 100m --rm amazoncorretto:17 java -XX:+PrintFlagsFinal -version | grep HeapSize > 100m
docker container run -m1000m --memory-swap 1000m --rm amazoncorretto:17 java -XX:+PrintFlagsFinal -version | grep HeapSize > 1000m
diff 100m 1000m
```

| Parameter       | 100m               | 1000m                |
|-----------------|--------------------|----------------------|
| InitialHeapSize | 8388608 (8.3 MB)   | 16777216 (16.7 MB)   |
| MaxHeapSize     | 52428800 (52.4 MB) | 262144000 (262.1 MB) |
| SoftMaxHeapSize | 52428800 (52.4 MB) | 262144000 (261.1 MB) |

To verify that it is not just Amazon corretto, I ran the `java/jvm_heap_diff.sh` script with `ibmjava` and `eclipse-temurin` and could confirm that they also specify the `MaxHeapSize` relative to the container's max memory.

#### Running OOM

The less interesting part for the question, but very important to mention is, that the default script allocating up to 200MB only succeeded with 2000MB input memory specified.
That is due to the JVM presumably taking up the remaining memory on its own. 

```bash
$ docker container run -it \
    -m 1000m  --memory-swap 1000m \
    -v $(pwd)/build/FillMemory.jar:/tmp/fill-memory/run.jar \
    --rm amazoncorretto:17 \
    java -verbose:gc -jar /tmp/fill-memory/run.jar
Allocation Limit:   200MB
Allocation/Round:   10MB
Max Heap Size:      241MB
...
[0.333s][info][gc] GC(28) Pause Young (Allocation Failure) 150M->150M(165M) 0.156ms
[0.339s][info][gc] GC(29) Pause Full (Allocation Failure) 150M->0M(15M) 5.930ms
----- Report -----
Total Heap Size:    171MB
Allocated:          160MB
Presumably free:    81MB
Definitely free:    10MB
------------------
Allocating Memory:  170MB
[0.373s][info][gc] GC(30) Pause Young (Allocation Failure) 160M->160M(171M) 0.148ms
[0.379s][info][gc] GC(31) Pause Full (Allocation Failure) 160M->0M(15M) 5.899ms
[0.381s][info][gc] GC(32) Pause Full (Allocation Failure) 0M->0M(15M) 1.291ms
Exception in thread "main" java.lang.OutOfMemoryError: Java heap space
        at com.misterderpie.FillMemory.main(FillMemory.java:20)
```

A thorough explanation of the difference between "definitely" and "presumably" free memory can be found in this [StackOverflow Thread](https://stackoverflow.com/questions/12807797/java-get-available-memory).

### Go

