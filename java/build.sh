#!/bin/sh
javac FillMemory.java -d build/
cd build/
jar -cfe FillMemory.jar com.misterderpie.FillMemory .