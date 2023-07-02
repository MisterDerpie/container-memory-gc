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

- Java: Yes.
- Go: No, but it also does not respect the host's memory.

> Will the GC be force-invoked when reaching the container limit, to prevent Out-Of-Memory (OOM)?

- Java: Yes, that follows as the JVM is aware of the container's memory limit.
As pointed out below, the default limit of the JVM heap is less than the memory allocated to the container.
- Go: No.

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

As stated at the start of the section, Go is at least not indicating to be aware of the memory restrictions in its Garbage Collector.
The setup used will always have located memory of less or approximately equal to 100MB.
In the next iteration of the script, the previously allocated memory becomes orphaned.
Thus, it could be picked up by the Garbage Collector and be freed, before the next chunk of less or approximately equal to 100MB is requested.

All experiments were conducted with a memory limit of 150MB.

### Relying on Go's default GC

```bash
$ docker container run -it \
    -e GODEBUG=gctrace=1 \
    -m 150m --memory-swap 150m \
    -v $(pwd)/container-memory-gc:/tmp/fill-memory/container-memory-gc \
    --rm alpine \
    /tmp/fill-memory/container-memory-gc
```

Depending on how much luck you have with the RNG, the run takes longer or shorter.
In all runs I had, I eventually received similar to the following result.
Note that the lines prefixed with `gc` are from Go's garbage collector, which is enabled by setting the environment variable `GODEBUG=gctrace=1`.

```txt
gc 2 @0.085s 0%: 0.013+0.14+0.014 ms clock, 0.22+0/0.17/0+0.22 ms cpu, 138->138->82 MB, 138 MB goal, 0 MB stacks, 0 MB globals, 16 P
----- Report -----
Allocated []int32:  21495808
Possible Heap Size: 154MB
Allocated:          82MB
HeapAlloc:          82MB
HeapInUse:          82MB
HeapIdle:           61MB
------------------
Round:              4/100
Allocating:         79MB
gc 3 @0.209s 0%: 0.013+0.36+0.012 ms clock, 0.21+0/0.20/0+0.19 ms cpu, 161->161->79 MB, 164 MB goal, 0 MB stacks, 0 MB globals, 16 P
```

In some of the cases, there was no GC log line printed, and it just died out of memory.

```
Allocating:         14MB
----- Report -----
Allocated []int32:  3670016
Possible Heap Size: 207MB
Allocated:          105MB
HeapAlloc:          105MB
HeapInUse:          105MB
HeapIdle:           90MB
------------------
Round:              9/100
Allocating:         53MB
```

Every run failed eventually as soon as `HeapAlloc + Allocating > {Max Container Memory}` occurred.
Let's have a deeper look into what the [gctrace](https://pkg.go.dev/runtime) tells us:

```
gctrace: setting gctrace=1 causes the garbage collector to emit a single line to standard
error at each collection, summarizing the amount of memory collected and the
length of the pause. The format of this line is subject to change.
Currently, it is:
	gc # @#s #%: #+#+# ms clock, #+#/#/#+# ms cpu, #->#-># MB, # MB goal, # MB stacks, #MB globals, # P
where the fields are as follows:
...
	#->#-># MB   heap size at GC start, at GC end, and live heap
    # MB goal    goal heap size
```

It is visible that the heap size at GC start surpasses the maximum memory the container is allowed to consume.
What is not entirely clear to me is _when_ the GC got invoked.
Citing from the documentation [A Guide to the Go Garbage Collector](https://tip.golang.org/doc/gc-guide#Latency)

> The Go GC, however, is not fully stop-the-world and does most of its work concurrently with the application.

it could be that the concurrent execution leads to the intermediate heap size being larger than the memory limit.
The next section shows the concurrent behavior, as the log of the GC interferes with the print from the program itself.
Therefore, I assume that the GC at the time of invocation is "late to the party" and cannot rescue the application from its death.

### Forcing GC before allocating new memory

Life looks better when we force the GC to clean up every time before we allocate memory.

```bash
$ docker container run -it \
    -e GODEBUG=gctrace=1 \
    -m 150m --memory-swap 150m \
    -v $(pwd)/container-memory-gc:/tmp/fill-memory/container-memory-gc \
    --rm alpine \
    /tmp/fill-memory/container-memory-gc -f
...
Forcing garbage collection.
gc 145 @5.879s 0%: 0.012+0.12+0.011 ms clock, 0.19+0/0.16/0+0.18 ms cpu, 3->3->0 MB, 4Round:              75/100
 MB goal, Allocating:       71MB
0 MB stacks, 0 MB globals, 16 P (forced)
gc 146 @5.885s 0%: 0.018+0.27+0.011 ms clock, 0.29+0/0.18/0+0.17 ms cpu, 71->71->71 MB, 71 MB goal, 0 MB stacks, 0 MB globals, 16 P
----- Report -----
Allocated []int32:  18612224
Possible Heap Size: 141MB
Allocated:          71MB
HeapAlloc:          71MB
HeapInUse:          71MB
HeapIdle:           60MB
------------------
Forcing garbage collection.
gc 147 @5.993s 0%: 0.014+0.097+0.007 ms clock, 0.23+0/0.20/0+0.11 ms cpu, 71->71->0 MB, 142 MB goal, 0 MB stacks, 0 MB globals, 16 P (forced)
Round:              76/100
Allocating:         100MB
gc 148 @6.005s 0%: 0.017+0.45+0.009 ms clock, 0.27+0/0.16/0+0.15 ms cpu, 100->100->100 MB, 100 MB goal, 0 MB stacks, 0 MB globals, 16 P
----- Report -----
Allocated []int32:  26214400
Possible Heap Size: 141MB
Allocated:          100MB
HeapAlloc:          100MB
HeapInUse:          100MB
HeapIdle:           31MB
...
------------------
Successfully finished.
```

Provided an excerpt of the very place where we would have exceeded the limit with the previous experiment.
In the 75th iteration, we allocated 71MB.
Then, in the 76th iteration, we allocate 100MB.
If the heap would not have been cleaned, that would result in 171MB of assigned memory.
Comparing this to the preceding example, where the garbage collection was not enforced, this would have led to an OOM.
None of the runs contains lines of the form `{start}->{end}->{live}` where start/end exceed the assigned memory.

### Comparison - Automatic GC vs Forced GC

Comparing the results of both, I come to the conclusion that the Go GC is not aware of the container's memory limit, or at least not acting based on it.
Note that I conclude the same for the host memory, read further below.
This conclusion is based on the output we saw from the first experiment.

```txt
161->161->79 MB, 164 MB goal
#->#-># MB   heap size at GC start, at GC end, and live heap
# MB goal    goal heap size
```

If Go's garbage collector would be aware that the container has a memory limit of 150MB, or acted based on that, it would have rather freed up all orphened variables than increasing the Heap Size.

There is one important thing to note though: Go's Garbage Collector is not just ignoring the container limit, it does not consider the host's limit either.
To underline this, I invoked the Go binary directly on the host machine (setting the allocate per round to 25GB).

```bash
$ GODEBUG=gctrace=1 go run .
----- Report -----
Allocated []int32:  1
Possible Heap Size: 11MB
Allocated:          0MB
HeapAlloc:          0MB
HeapInUse:          0MB
HeapIdle:           3MB
------------------
Round:              1/100
Allocating:         25000MB
gc 1 @0.169s 0%: 0.011+0.33+0.010 ms clock, 0.18+0.24/0.84/0.12+0.16 ms cpu, 25000->25000->25000 MB, 25000 MB goal, 0 MB stacks, 0 MB globals, 16 P
----- Report -----
Allocated []int32:  6553600000
Possible Heap Size: 25434MB
Allocated:          25000MB
HeapAlloc:          25000MB
HeapInUse:          25000MB
HeapIdle:           2MB
------------------
Round:              2/100
Allocating:         25000MB
gc 2 @38.487s 0%: 0.018+0.67+0.014 ms clock, 0.28+0/1.1/0+0.22 ms cpu, 50000->50000->25000 MB, 50000 MB goal, 0 MB stacks, 0 MB globals, 16 P
signal: killed
```

When running it with `-f` or `-f -d` (`-d` disables the automatic GC), each garbage collection will entirely free the orphaned variables.

```txt
gc 2 @38.292s 0%: 0.011+0.41+0.009 ms clock, 0.18+0/0.41/0.001+0.14 ms cpu, 25000->25000->0 MB, 8796093021775 MB goal, 0 MB stacks, 0 MB globals, 16 P (forced)
```

In most real world applications, one single operation is unlikely to increase the memory in such drastic amounts.
Utilizing 

There are also more parameters to tweak around the Go Garbage Collector:

- [GOGC](https://tip.golang.org/doc/gc-guide#GOGC) - Aggressiveness of the Go GC
- [GOMEMLIMIT](https://weaviate.io/blog/gomemlimit-a-game-changer-for-high-memory-applications) - Soft Memory Limit