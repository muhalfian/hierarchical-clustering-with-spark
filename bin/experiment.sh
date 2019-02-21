#!/bin/bash

## ENV
source ~/spark/conf/spark-env.sh
SPARK_SUBMIT=/usr/local/spark/bin/spark-submit

## paramters
__MAX_CPU_CORES_LIST="1 2 4 8 16 32 64"
__DATA_SIZE_LIST="10000 100000 1000000 10000000 100000000"
__DIMENSION_LIST="2 5 10 20 50 100 200 1000 10000"
__NUM_CLUSTERS_LIST="5 10 20 50 100"

__SPARK_MASTER="spark://10.252.37.109:7077"
__JAR="hdfs://blade1-node:9000/online_media/jars/prayuga-streaming_2.11-0.1.jar"
__ADDITIONAL_JAR="hdfs://blade1-node:9000/online_media/jars/spark-core_2.11-1.5.2.logging.jar"

for __DATA_SIZE in $__DATA_SIZE_LIST
do
  for __DIMENSION in $__DIMENSION_LIST
  do
    for __NUM_CLUSTERS in $__NUM_CLUSTERS_LIST
    do
      for __MAX_CPU_CORES in $__MAX_CPU_CORES_LIST
      do
        __NUM_PARTITIONS=$(($__MAX_CPU_CORES * 2))
        $SPARK_SUBMIT  \
          --master "$__SPARK_MASTER" \
          --class HierarchicalClusteringApp \
          --deploy-mode cluster \
          --supervise  \
          --total-executor-cores $__MAX_CPU_CORES \
          --jars $__ADDITIONAL_JAR \
          $__JAR "$__SPARK_MASTER" $__MAX_CPU_CORES $__DATA_SIZE $__DIMENSION $__NUM_CLUSTERS $__NUM_PARTITIONS
      done
    done
  done
done
