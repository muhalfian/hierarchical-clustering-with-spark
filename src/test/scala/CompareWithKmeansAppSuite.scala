import org.scalatest.FunSuite

class CompareWithKmeansAppSuite extends FunSuite {

  test("main") {
    val master = "local"
    val maxCores = 1
    val rows = 10000
    val numClusters = 5
    val dimension = 2
    val numPartitions = 4

    val args = Array(master, maxCores, rows, numClusters, dimension, numPartitions).map(_.toString)
    CompareWithKMeansApp.main(args)
  }
}
