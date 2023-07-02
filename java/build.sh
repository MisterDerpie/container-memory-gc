#!/bin/sh
javac MonitorMemory.java -d build/
cd build/
jar -cfe MonitorMemory.jar com.misterderpie.MonitorMemory .
# java -verbose:gc -Xms1m -Xmx50m -jar MonitorMemory.jar
java -verbose:gc -jar MonitorMemory.jar