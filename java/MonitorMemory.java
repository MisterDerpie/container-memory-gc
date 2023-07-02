package com.misterderpie;

public class MonitorMemory {
    
    private static Runtime runtime = Runtime.getRuntime();

    public static void main(String[] args) throws Exception {
        System.out.println("Max Heap Size:      " + memoryInMiB(runtime.maxMemory()));
    
        for (int i = 0; i < 100; i++) {
            printMemoryReport();
            int[] spaceConsoomer = new int[i*1024*1024]; // 10 MB
            printMemoryReport();
            Thread.sleep(1000);
        }
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
    }

    private static String memoryInMiB(long memoryInBytes) {
        return (memoryInBytes / 1024 / 1024) + "MB";
    }
}
