import org.scalatest.FunSuite

class HierarchicalClusteringWithSparseVectorAppSuite extends FunSuite {

  test("main") {
    val master = "local"
    val maxCores = 1
    val rows = 1000
    val dimension = 1000
    val numClusters = 10
    val numPartitions = 4
    val sparsity = 0.001
    val args = Array(
      master,
      maxCores,
      rows,
      dimension,
      numClusters,
      numPartitions,
      sparsity).map(_.toString)
    HierarchicalClusteringWithSparseVectorApp.main(args)
  }
}
