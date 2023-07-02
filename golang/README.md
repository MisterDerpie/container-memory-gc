# Fill Memory

This directory contains a simple Go script `fill-memory.go` that allocates a random amount of memory within a predefined range.

Adjust the following values below to change how many rounds to run, how much memory to allocate,
and how long the pause after a forced GC invocation should take.

```go
const maxRounds int = 100
const maxMbToAllocate int = 100
const pauseAfterGc = 50 * time.Millisecond
```

Flags:

- `f` - Force GC before each allocaction
- `d` - Disable automatic GC

## Build

Go must be installed on the machine.
Installation instructions can be found at [go.dev/doc/install](https://go.dev/doc/install).

```bash
go build .
```

produces a binary `container-memory-gc` in this folder.

## Run the Memory Tool

You can either run it via Go or build and then execute the binary.

```bash
./container-memory-gc
go run .
```

To get output of the Garbage Collector, run it with `GODEBUG=gctrace=1` set.
For applying the GC soft limit, set `GOMEMLIMIT={size}MiB`.
There is a great blogpost by Etienne Dilocker about the limit variable:
[GOMEMLIMIT is a game changer for high-memory applications](https://weaviate.io/blog/gomemlimit-a-game-changer-for-high-memory-applications)

```bash
GODEBUG=gctrace=1 go run .
```

## Run the Memory Tool in Docker

The Go binary must be built.
With below snippet, you can start a Docker container running alpine, executing the binary.
Use `-m {limit}m --memory-swap {limit}m` to specify the memory limit for the container.
As per [Docker/Memory-Swap-Details](https://docs.docker.com/config/containers/resource_constraints/#--memory-swap-details), both need to be
set to the same value to disable swapping.

```bash
docker container run -it \
    -e GODEBUG=gctrace=1 \
    -m {limit}m --memory-swap {limit}m \
    -v $(pwd)/container-memory-gc:/tmp/fill-memory/container-memory-gc \
    --rm alpine \
    /tmp/fill-memory/container-memory-gc
```

As stated at the top, the flags `-d` and `-f` are available.
If the experiment succeeds, after 100 rounds the output `Successfully finished.` occurs.

### Example

- `f` enabled - GC will be invoked each time before allocating new memory
- `d` enabled - Automatic GC is disabled
- `GODEBUG=gtrace=1` - Enable printing of GC trace

```bash
docker container run -it \
    -e GODEBUG=gctrace=1 \
    -m 125m --memory-swap 125m \
    -v $(pwd)/container-memory-gc:/tmp/fill-memory/container-memory-gc \
    --rm alpine \
    /tmp/fill-memory/container-memory-gc -f -d
```