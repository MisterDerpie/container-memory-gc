#!/bin/sh
javac MonitorMemory.java -d build/
cd build/
jar -cfe MonitorMemory.jar com.misterderpie.MonitorMemory .