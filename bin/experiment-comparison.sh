#!/bin/bash

## ENV
source ~/spark/conf/spark-env.sh
SPARK_SUBMIT=${HOME}/spark/bin/spark-submit

## paramters
__MAX_CPU_CORES_LIST="160"
__DATA_SIZE_LIST="500000 1000000 5000000"
__DIMENSION_LIST="100 500 1000"
__NUM_CLUSTERS_LIST="20 50 "

__SPARK_MASTER="spark://${SPARK_MASTER_IP}:7077"
__JAR="${HOME}/hierarchical-clustering-with-spark/target/scala-2.10/hierarchical-clustering_2.10-0.0.1.jar"

for __DATA_SIZE in $__DATA_SIZE_LIST
do
  for __DIMENSION in $__DIMENSION_LIST
  do
    for __NUM_CLUSTERS in $__NUM_CLUSTERS_LIST
    do
      for __MAX_CPU_CORES in $__MAX_CPU_CORES_LIST
      do
        __NUM_PARTITIONS=$(($__MAX_CPU_CORES))
        $SPARK_SUBMIT  \
          --master "$__SPARK_MASTER" \
          --class CompareWithKMeansApp \
          --total-executor-cores $__MAX_CPU_CORES \
          $__JAR "$__SPARK_MASTER" $__MAX_CPU_CORES $__DATA_SIZE $__DIMENSION $__NUM_CLUSTERS $__NUM_PARTITIONS
      done
    done
  done
done
