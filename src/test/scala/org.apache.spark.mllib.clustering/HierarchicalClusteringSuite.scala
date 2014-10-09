/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.mllib.clustering

import breeze.linalg.{DenseVector => BDV, Vector => BV, norm => breezeNorm}
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.mllib.random.UniformGenerator
import org.apache.spark.rdd.RDD
import org.apache.spark.util.LocalSparkContext
import org.scalatest.{BeforeAndAfterEach, FunSuite}

import scala.collection.SortedMap

class HierarchicalClusteringConfSuite extends FunSuite {

  test("construct a new instance without parameters") {
    val conf = new HierarchicalClusteringConf()
    assert(conf.getNumClusters === 100)
    assert(conf.getSubIterations === 20)
    assert(conf.getEpsilon === 10E-6)
  }

  test("can replace numClusters") {
    val conf = new HierarchicalClusteringConf()
    assert(conf.getNumClusters === 100)
    conf.setNumClusters(50)
    assert(conf.getNumClusters === 50)
  }

  test("can replace subIterations") {
    val conf = new HierarchicalClusteringConf()
    assert(conf.getSubIterations === 20)
    conf.setSubIterations(50)
    assert(conf.getSubIterations === 50)
  }
}

class HierarchicalClusteringSuite extends FunSuite with BeforeAndAfterEach with LocalSparkContext {

  var vectors: Seq[Vector] = _
  var data: RDD[Vector] = _

  override def beforeEach() {
    vectors = Seq(
      Vectors.dense(0.0, 0.0, 0.0),
      Vectors.dense(1.0, 2.0, 3.0),
      Vectors.dense(4.0, 5.0, 6.0),
      Vectors.dense(7.0, 8.0, 9.0),
      Vectors.dense(10.0, 11.0, 12.0),
      Vectors.dense(13.0, 14.0, 15.0),
      Vectors.dense(16.0, 17.0, 18.0)
    )
    data = sc.parallelize(vectors, 2)
  }

  test("train method called by the companion object") {
    val numClusters = 2
    val model = HierarchicalClustering.train(data, numClusters)
    assert(model.getClass.getSimpleName === "HierarchicalClusteringModel")
  }

  test("train") {
    val conf = new HierarchicalClusteringConf().setNumClusters(3)
    val app = new HierarchicalClustering(conf)
    val model = app.train(data)
    assert(model.clusterTree.treeSize() === 3)
    model.clusterTree.toSeq().foreach { tree: ClusterTree => assert(tree.getStats() != None)}
  }

  test("train with a random data set") {
    val data = sc.parallelize((1 to 100).map(i => Vectors.dense(Math.random(), Math.random())), 2)
    val conf = new HierarchicalClusteringConf().setNumClusters(10)
    val app = new HierarchicalClustering(conf)
    val model = app.train(data)
    assert(model.clusterTree.treeSize() === 10)
    model.clusterTree.toSeq().foreach { tree: ClusterTree => assert(tree.getStats() != None)}
  }

  test("should stop if there is no splittable cluster") {
    val data = sc.parallelize((1 to 100).map(i => Vectors.dense(0.0, 0.0)), 1)
    val conf = new HierarchicalClusteringConf().setNumClusters(5)
    val app = new HierarchicalClustering(conf)
    val model = app.train(data)
    assert(model.clusterTree.treeSize() === 1)
  }

  test("parameter validation") {
    val data = sc.parallelize((1 to 100).map(i => Vectors.dense(0.0, 0.0)), 1)
    intercept[IllegalArgumentException] {
      val conf = new HierarchicalClusteringConf().setNumClusters(101)
      val app = new HierarchicalClustering(conf)
      app.train(data)
    }
  }

  test("the generated data should match its seed centers with the same number of clusters") {
    def sortedWithNorm(seq: Seq[ClusterTree]): Seq[ClusterTree] = {
      seq.sortWith { (t1, t2) =>
        breezeNorm(t1.center.toBreeze, 2.0) < breezeNorm(t2.center.toBreeze, 2.0)
      }
    }

    val seeds = SortedMap[Int, Vector](
      200 -> Vectors.dense(2.0, 3.0, 4.0), 300 -> Vectors.dense(3.0, 4.0, 5.0),
      400 -> Vectors.dense(4.0, 5.0, 6.0), 500 -> Vectors.dense(5.0, 6.0, 7.0),
      600 -> Vectors.dense(6.0, 7.0, 8.0)
    )
    val seedData = seeds.flatMap { case (count, vector) => (1 to count).map(i => vector).toIterable}
    val data = sc.parallelize(seedData.toSeq)
    val conf = new HierarchicalClusteringConf().setNumClusters(5).setRandomSeed(1)
    val app = new HierarchicalClustering(conf)
    val model = app.train(data)

    val clusters = model.getClusters()
    assert(clusters.size === 5)
    assert(clusters.count(_.depth() == 2) === 3)
    assert(clusters.count(_.depth() == 3) === 2)

    val nodes = model.clusterTree.toSeq()
    // depth: 0
    val root = nodes.filter(_.depth() == 0).apply(0)
    assert(root.getDataSize() === 2000)
    assert(root.center === Vectors.dense(4.5, 5.5, 6.5))
    // depth: 1
    var subNodes = sortedWithNorm(nodes.filter(_.depth() == 1))
    assert(subNodes.size === 2)
    assert(subNodes.apply(0).getDataSize() === 900)
    assert(subNodes.apply(1).getDataSize() === 1100)
    assert(subNodes.apply(0).center === Vectors.dense(3.2222222222222223, 4.222222222222222, 5.222222222222222))
    assert(subNodes.apply(1).center === Vectors.dense(5.545454545454546, 6.545454545454546, 7.545454545454546))
    assert(subNodes.apply(0).isLeaf() === false)
    assert(subNodes.apply(1).isLeaf() === false)
    // depth: 2
    subNodes = sortedWithNorm(nodes.filter(_.depth() == 2))
    assert(subNodes.apply(0).getDataSize() === 500)
    assert(subNodes.apply(1).getDataSize() === 400)
    assert(subNodes.apply(2).getDataSize() === 500)
    assert(subNodes.apply(3).getDataSize() === 600)
    assert(subNodes.apply(0).center === Vectors.dense(2.6, 3.6, 4.6))
    assert(subNodes.apply(1).center === Vectors.dense(4.0, 5.0, 6.0))
    assert(subNodes.apply(2).center === Vectors.dense(5.0, 6.0, 7.0))
    assert(subNodes.apply(3).center === Vectors.dense(6.0, 7.0, 8.0))
    assert(subNodes.apply(0).isLeaf() === false)
    assert(subNodes.apply(1).isLeaf() === true)
    assert(subNodes.apply(2).isLeaf() === true)
    assert(subNodes.apply(3).isLeaf() === true)
    // depth: 3
    subNodes = sortedWithNorm(nodes.filter(_.depth() == 3))
    subNodes.foreach(println)
    assert(subNodes.size === 2)
    assert(subNodes.apply(0).getDataSize() === 200)
    assert(subNodes.apply(1).getDataSize() === 300)
    assert(subNodes.apply(0).center === Vectors.dense(2.0, 3.0, 4.0))
    assert(subNodes.apply(1).center === Vectors.dense(3.0, 4.0, 5.0))
    assert(subNodes.apply(0).isLeaf() === true)
    assert(subNodes.apply(1).isLeaf() === true)
  }

  test("takeInitCenters") {
    val data = Array(
      Vectors.dense(1.0, 1.0, 1.0),
      Vectors.dense(10.0, 10.0, 10.0),
      Vectors.dense(100.0, 100.0, 100.0)
    )
    val rdd = sc.parallelize(data, 2)
    val vectors = new HierarchicalClustering().takeInitCenters(rdd)
    assert(vectors.size === 2)
    assert(vectors(0) === data(0))
    assert(vectors(1) === data(2))
  }
}


class ClusterTreeSuite extends FunSuite with LocalSparkContext with SampleData {

  override var data: RDD[Vector] = _
  override var subData1: RDD[Vector] = _
  override var subData21: RDD[Vector] = _
  override var subData22: RDD[Vector] = _
  override var subData2: RDD[Vector] = _

  override def beforeAll() {
    super.beforeAll()
    data = sc.parallelize(vectors, 3)
    subData1 = sc.parallelize(subVectors1)
    subData2 = sc.parallelize(subVectors2)
    subData21 = sc.parallelize(subVectors21)
    subData22 = sc.parallelize(subVectors22)
  }

  test("insert a new Cluster with a Cluster") {
    val root = ClusterTree.fromRDD(data)
    val child = ClusterTree.fromRDD(subData1)
    assert(root.getChildren().size === 0)
    root.insert(child)
    assert(root.getChildren().size === 1)
    assert(root.getChildren.apply(0) === child)
  }

  test("insert Cluster List") {
    val root = ClusterTree.fromRDD(data)
    val children = List(
      ClusterTree.fromRDD(subData1),
      ClusterTree.fromRDD(subData2)
    )
    assert(root.getChildren().size === 0)
    root.insert(children)
    assert(root.getChildren().size === 2)
    assert(root.getChildren() === children)
  }

  test("treeSize and depth") {
    val root = ClusterTree.fromRDD(data)
    val child1 = ClusterTree.fromRDD(subData1)
    val child2 = ClusterTree.fromRDD(subData2)
    val child21 = ClusterTree.fromRDD(subData21)
    val child22 = ClusterTree.fromRDD(subData22)

    root.insert(child1)
    root.insert(child2)
    child2.insert(child21)
    child2.insert(child22)
    assert(root.treeSize() === 3)
    assert(child1.treeSize() === 1)
    assert(child2.treeSize() === 2)
    assert(child21.treeSize() === 1)
    assert(child22.treeSize() === 1)

    assert(root.depth() === 0)
    assert(child1.depth() === 1)
    assert(child2.depth() === 1)
    assert(child21.depth() === 2)
    assert(child22.depth() === 2)
  }
}

class DataSizeStatsSuite extends FunSuite with LocalSparkContext with SampleData {

  override var data: RDD[Vector] = _
  override var subData1: RDD[Vector] = _
  override var subData21: RDD[Vector] = _
  override var subData22: RDD[Vector] = _
  override var subData2: RDD[Vector] = _

  test("select the largest cluster tree") {
    data = sc.parallelize(vectors)
    subData1 = sc.parallelize(subVectors1)
    subData2 = sc.parallelize(subVectors2)
    subData21 = sc.parallelize(subVectors21)
    subData22 = sc.parallelize(subVectors22)

    val root = ClusterTree.fromRDD(data)
    val child1 = ClusterTree.fromRDD(subData1)
    val child2 = ClusterTree.fromRDD(subData2)
    val child21 = ClusterTree.fromRDD(subData21)
    val child22 = ClusterTree.fromRDD(subData22)
    root.insert(child1)
    root.insert(child2)
    child2.insert(child21)
    child2.insert(child22)

    val stats = new DataSizeStats
    val statsMap = stats(root.toSeq())
    assert(statsMap.size == 5)
    assert(statsMap(root) === 100.0)
    assert(statsMap(child1) === 70.0)
    assert(statsMap(child2) === 30.0)
    assert(statsMap(child21) === 20.0)
    assert(statsMap(child22) === 10.0)
  }
}

class ClusterVarianceStatsSuite extends FunSuite with LocalSparkContext {

  test("the variance of a data should be greater than that of another one") {
    // the variance of subData2 is greater than that of subData1
    def rand(): Double = new UniformGenerator().nextValue()
    def rand2(): Double = 10 * new UniformGenerator().nextValue()
    val subData1 = (1 to 99).map(i => Vectors.dense(rand, rand))
    val subData2 = (1 to 99).map(i => Vectors.dense(rand2, rand2))
    val data = subData1 ++ subData2

    val root = ClusterTree.fromRDD(sc.parallelize(data, 2))
    val child1 = ClusterTree.fromRDD(sc.parallelize(subData1, 2))
    val child2 = ClusterTree.fromRDD(sc.parallelize(subData2, 2))
    root.insert(child1)
    root.insert(child2)

    val stats = new ClusterVarianceStats
    val statsMap = stats(root.toSeq())
    assert(statsMap.size === 3)
    assert(statsMap(child1) < statsMap(child2))
  }

  test("the sum of variance should be 0.0 with all same records") {
    val data = sc.parallelize((1 to 99).map(i => Vectors.dense(1.0, 2.0)), 4)
    val stats = new ClusterVarianceStats()
    val variance = stats.calculateVariance(data)
    assert(variance === 0.0)
  }

  test("the sum of variance should be 0.0 with one record") {
    val data = sc.parallelize(Seq(Vectors.dense(1.0, 2.0, 3.0)), 1)
    val stats = new ClusterVarianceStats()
    val variance = stats.calculateVariance(data)
    assert(variance === 0.0)
  }

  test("the variance should be 7.5") {
    val seedData = Seq(
      Vectors.dense(1.0), Vectors.dense(2.0), Vectors.dense(3.0),
      Vectors.dense(4.0), Vectors.dense(5.0), Vectors.dense(6.0),
      Vectors.dense(7.0), Vectors.dense(8.0), Vectors.dense(9.0)
    )
    val data = sc.parallelize(seedData, 3)
    val stats = new ClusterVarianceStats()
    val variance = stats.calculateVariance(data)
    assert(variance === 7.5)
  }

  test("the variance should be 8332500") {
    val data = sc.parallelize((1 to 9999).map(Vectors.dense(_)), 4)
    val stats = new ClusterVarianceStats()
    val variance = stats.calculateVariance(data)
    assert(variance === 8332500)
  }
}

sealed trait SampleData {
  val vectors = (0 to 99).map(i => Vectors.dense(Math.random(), Math.random(), Math.random())).toSeq
  val subVectors1 = (0 to 69).map(vectors(_))
  val subVectors2 = (70 to 99).map(vectors(_))
  val subVectors21 = (70 to 89).map(vectors(_))
  val subVectors22 = (90 to 99).map(vectors(_))

  var data: RDD[Vector]
  var subData1: RDD[Vector]
  var subData2: RDD[Vector]
  var subData21: RDD[Vector]
  var subData22: RDD[Vector]
}
