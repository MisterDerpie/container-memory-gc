package com.misterderpie;

public class FillMemory {
    
    private final static int maxLimitInMiB = 200;
    private final static int mbToAllocatePerRound = 10;

    private static Runtime runtime = Runtime.getRuntime();

    public static void main(String[] args) throws Exception {
        System.out.println("Allocation Limit:   " + maxLimitInMiB + "MB");
        System.out.println("Allocation/Round:   " + mbToAllocatePerRound + "MB");
        System.out.println("Max Heap Size:      " + memoryInMiB(runtime.maxMemory()));
        System.out.println();
    
        for (int i = 1; mbToAllocatePerRound * i <= maxLimitInMiB; i++) {
            int integersToAllocate = calculateMegabyteSize(i * mbToAllocatePerRound);
            int memoryToAllocateInByte = integersToAllocate * 4;
            System.out.println("Allocating Memory:  " + memoryInMiB(memoryToAllocateInByte));
            int[] memoryAllocation = new int[integersToAllocate];
            printMemoryReport();
        }

        System.out.println("Finished - Allocated max possible amount less than " + maxLimitInMiB + "MB.");
    }

    private static void printMemoryReport() {
        // Based on https://stackoverflow.com/questions/12807797/java-get-available-memory
        long allocatedMemory      = runtime.totalMemory() - runtime.freeMemory();
        long presumableFreeMemory = runtime.maxMemory() - allocatedMemory;
        long definitelyFreeMemory = Runtime.getRuntime().freeMemory();

        System.out.println("----- Report -----");
        System.out.println("Total Heap Size:    " + memoryInMiB(runtime.totalMemory()));
        System.out.println("Allocated:          " + memoryInMiB(allocatedMemory));
        System.out.println("Presumably free:    " + memoryInMiB(presumableFreeMemory));
        System.out.println("Definitely free:    " + memoryInMiB(definitelyFreeMemory));
        System.out.println("------------------");
    }

    private static String memoryInMiB(long memoryInBytes) {
        return (memoryInBytes / 1024 / 1024) + "MB";
    }

    private static int calculateMegabyteSize(int amountOfInts) {
        return amountOfInts * 1024 * 1024 / 4;    // 4 Byte/Int
    }
}
