package fourquant.io

import fourquant.io.ImageIOOps._
import fourquant.io.IOOps._
import fourquant.tiles.TilingStrategies
import net.imglib2.`type`.numeric.real.FloatType
import org.apache.spark.SparkContext
import org.scalatest.{FunSuite, Matchers}

class SparkScifioTests extends FunSuite with Matchers {
  val useCloud = false
  val useLocal = true
  lazy val sc = if (useLocal) new SparkContext("local[4]", "Test")
  else
    new SparkContext("spark://MacBook-Air.local:7077", "Test")

  val testDataDir = "/Users/mader/Dropbox/Informatics/spark-imageio/test-data/"

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


  if (useCloud) {
    test("Cloud Test") {
      import TilingStrategies.Grid._
      sc.hadoopConfiguration.set("fs.s3n.awsAccessKeyId", "AKIAJM4PPKISBYXFZGKA")
      sc.hadoopConfiguration.set("fs.s3n.awsSecretAccessKey",
        "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")

      val tiledImage = sc.readTiledDoubleImage("s3n://geo-images/*.tif", 1000, 10000, 40)

      tiledImage.first._2.length shouldBe 10000
      tiledImage.first._2(0).length shouldBe 1000

      val lengthImage = tiledImage.mapValues(_.length)
      tiledImage.count shouldBe 160
    }
  }

}
