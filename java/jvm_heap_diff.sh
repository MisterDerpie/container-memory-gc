#!/bin/sh
# Based on https://stackoverflow.com/questions/4667483/how-is-the-default-max-java-heap-size-determined
echo "First JVM Memory:     $1"
echo "Second JVM Memory:    $2"
echo "JVM:                  $3"

FILE_ONE="/tmp/java_experiment_$1"
FILE_TWO="/tmp/java_experiment_$2"

docker container run \
    "-m$1" --memory-swap "$1" \
    --rm $3 \
    java -XX:+PrintFlagsFinal -version | grep HeapSize > $FILE_ONE


docker container run \
    "-m$2" --memory-swap "$2" \
    --rm $3 \
    java -XX:+PrintFlagsFinal -version | grep HeapSize > $FILE_TWO

echo $FILE_ONE
diff $FILE_ONE $FILE_TWO