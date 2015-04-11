package fourquant.io

import fourquant.io.IOOps._
import fourquant.tiles.TilingStrategies
import net.imglib2.`type`.numeric.real.FloatType
import org.apache.spark.SparkContext
import org.scalatest.{FunSuite, Matchers}

class SparkScifioTests extends FunSuite with Matchers {
  val useCloud = true
  val useLocal = true
  lazy val sc = if (useLocal) new SparkContext("local[4]", "Test")
  else
    new SparkContext("spark://MacBook-Air.local:7077", "Test")

  val testDataDir = "/Users/mader/Dropbox/Informatics/spark-imageio/test-data/"

  if (useCloud) {
    test("Cloud Test") {

      sc.hadoopConfiguration.set("fs.s3n.awsAccessKeyId", "AKIAJM4PPKISBYXFZGKA")
      sc.hadoopConfiguration.set("fs.s3n.awsSecretAccessKey",
        "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")

      val roiImages =
        sc.genericArrayImagesRegion2D[Float,FloatType](
          "s3n://geo-images/*.tif",100,Array((38000,6000,2000,2000)))
      roiImages.count shouldBe 1
      println(roiImages.mapValues(_.getArray.rawArray).mapValues(fa => (fa.min,fa.max,fa.sum))
        .collect().mkString(", "))
    }
  }

  test("Load image in big tiles") {
    import TilingStrategies.Grid.GridTiling2D
    val regions = GridTiling2D.createTiles2D(40000,40000,2000,2000)
    val roiImages =
      sc.genericArrayImagesRegion2D[Float,FloatType](
        testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",100,regions)
     // roiImages.count shouldBe 400
    val results = roiImages.mapValues(_.getArray.rawArray).mapValues(fArray => (fArray.min,
      fArray.max, fArray.sum,fArray.length)).cache()

    results.filter(_._2._3>0).foreach(println(_))
    println(results.collect().mkString("\n"))
  }




}
