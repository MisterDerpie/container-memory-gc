package main

import (
	"flag"
	"fmt"
	"math/rand"
	"runtime"
	"runtime/debug"
)

const maxRounds int = 100
const maxMbToAllocate int = 100

func main() {
	forceGC := flag.Bool("f", false, "Force Garbage Collection")
	disableGC := flag.Bool("d", false, "Disable automatic Garbage Collection")
	flag.Parse()

	if *disableGC {
		fmt.Print("Automatic GC disabled.\n")
		debug.SetGCPercent(-1)
	}

	printMemoryReport(make([]int32, 1))
	for i := 1; i < maxRounds; i++ {
		var realAllocation = 1 + (rand.Int() % maxMbToAllocate)

		if *forceGC {
			fmt.Printf("Forcing garbage collection.\n")
			runtime.GC()
		}

		fmt.Printf("Round:              %d/%d\n", i, maxRounds)
		fmt.Printf("Allocating:	    %dMB\n", realAllocation)

		var temp []int32 = make([]int32, calculateMegabyteSize(realAllocation))
		for j := range temp {
			temp[j] = rand.Int31()
		}

		printMemoryReport(temp)
	}
	fmt.Print("Successfully finished.")
}

func printMemoryReport(arr []int32) {
	// https://golangcode.com/print-the-current-memory-usage/
	// https://pkg.go.dev/runtime#MemStats
	var m runtime.MemStats
	runtime.ReadMemStats(&m)

	fmt.Print("----- Report -----\n")
	fmt.Printf("Allocated []int32:  %d\n", len(arr))
	fmt.Printf("Possible Heap Size: %s\n", memoryInMiB(int(m.Sys)))
	fmt.Printf("Allocated:          %s\n", memoryInMiB(int(m.Alloc)))
	fmt.Printf("HeapAlloc:          %s\n", memoryInMiB(int(m.HeapAlloc)))
	fmt.Printf("HeapInUse:          %s\n", memoryInMiB(int(m.HeapInuse)))
	fmt.Printf("HeapIdle:           %s\n", memoryInMiB(int(m.HeapIdle)))
	fmt.Print("------------------\n")
}

func memoryInMiB(memoryInBytes int) string {
	return fmt.Sprintf("%dMB", memoryInBytes/1024/1024)
}

func calculateMegabyteSize(amountOfInts int) int {
	return amountOfInts * 1024 * 1024 / 4 // 4 Byte/Int
}
