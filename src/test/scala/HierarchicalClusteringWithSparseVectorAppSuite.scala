import org.scalatest.FunSuite

class HierarchicalClusteringWithSparseVectorAppSuite extends FunSuite {

  test("main") {
    val master = "local"
    val maxCores = 1
    val rows = 10000
    val dimension = 100
    val numClusters = 10
    val numPartitions = 4
    val args = Array(master, maxCores, rows, dimension, numClusters, numPartitions).map(_.toString)
    HierarchicalClusteringWithSparseVectorApp.main(args)
  }
}
