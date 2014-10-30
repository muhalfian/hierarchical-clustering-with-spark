import org.apache.spark.mllib.clustering.{HierarchicalClustering, KMeans}
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.rdd.RDD

object CompareWithKMeansApp {

  def main(args: Array[String]) {
    val master = args(0)
    val maxCores = args(1)
    val rows = args(2).toInt
    val dimension = args(3).toInt
    val numClusters = args(4).toInt
    val numPartitions = args(5).toInt

    val appName = s"${this.getClass().getSimpleName},maxCores,${maxCores},rows:${rows}:dim:${dimension},"
    val conf = new SparkConf()
        .setAppName(appName)
        .setMaster(master)
        .set("spark.cores.max", maxCores)
    val sc = new SparkContext(conf)

    val data = generateData(sc, numPartitions, rows, dimension, numClusters)
    data.repartition(numPartitions)
    data.cache

    // KMeans
    var start = System.currentTimeMillis()
    val kmeans = KMeans.train(data, numClusters, maxIterations = 20)
    val kmeansTrainTime = System.currentTimeMillis() - start
    start = System.currentTimeMillis()
    kmeans.predict(data)
    val kmeansPredictTime = System.currentTimeMillis() - start

    // Hierarchical Clustering
    start = System.currentTimeMillis()
    val hierarchical = HierarchicalClustering.train(data, numClusters)
    val hierarchicalTrainTime = System.currentTimeMillis() - start
    start = System.currentTimeMillis()
    hierarchical.predict(data)
    val hierarchicalPredictTime = System.currentTimeMillis() - start

    // show the results
    println(s"KMeans Training Elappsed Time: ${kmeansTrainTime.toDouble / 1000} [sec]")
    println(s"KMeans Predicting Elappsed Time: ${kmeansPredictTime.toDouble / 1000} [sec]")
    println(s"KMeans Training Elappsed Time: ${hierarchicalTrainTime.toDouble / 1000} [sec]")
    println(s"KMeans Predicting Elappsed Time: ${hierarchicalPredictTime.toDouble / 1000} [sec]")
  }

  def generateData(sc: SparkContext,
    numPartitions: Int,
    rows: Int,
    dim: Int,
    numClusters: Int): RDD[Vector] = {
    sc.parallelize((1 to rows.toInt), numPartitions).map { i =>
      val idx = (i % (numClusters - 1)) + 1
      val indexes = for (j <- 0 to (Math.floor(dim / numClusters).toInt - 1)) yield j * numClusters + idx
      val values: Array[Double] = (0 to (dim - 1)).map { j =>
        val value = indexes.contains(j) match {
          case true => idx + idx * 0.01 * Math.random()
          case false => 0.0
        }
        value
      }.toArray
      Vectors.dense(values)
    }
  }
}
