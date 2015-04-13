package fourquant.io

import fourquant.arrays.{Positions, BreezeOps}
import fourquant.io.IOOps._
import fourquant.tiles.TilingStrategies
import net.imglib2.`type`.numeric.real.FloatType
import org.apache.spark.SparkContext
import org.apache.spark.mllib.linalg.Matrices
import org.scalatest.{FunSuite, Matchers}

class SparkScifioTests extends FunSuite with Matchers {
  val useCloud = false
  val useLocal = true
  val useBigImages = false

  lazy val sc = if (useLocal) new SparkContext("local[4]", "Test")
  else
    new SparkContext("spark://MacBook-Air.local:7077", "Test")

  val testDataDir = "/Users/mader/Dropbox/Informatics/spark-imageio/test-data/"

  test("Load a small image in spark") {
    import TilingStrategies.Grid.GridTiling2D
    import BreezeOps._
    import Positions._
    val regions = GridTiling2D.createTiles2D(100, 100, 20, 20)
    val imgStr = ImageTestFunctions.makeImage(100,100,"tif")

    val imgTiles = sc.genericArrayImagesRegion2D[Float,FloatType](imgStr,5,regions)
    imgTiles.count shouldBe 25
    val sparseMat = imgTiles.mapValues {
      case spImg =>
        val outArr = spImg.getArray
        Matrices.dense(outArr.dim(0).toInt,outArr.dim(1).toInt,outArr.rawArray.map(_.toDouble))
    }.sparseThresh(_>5)

    val strResult = sparseMat.take(5).mkString("\n")
    sparseMat.count shouldBe 298
    sparseMat.distinct.count shouldBe 298
    println(strResult)
  }

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
  if (useBigImages) {

    test("Load image in big tiles") {
      import TilingStrategies.Grid.GridTiling2D
      val regions = GridTiling2D.createTiles2D(40000, 40000, 2000, 2000)
      val roiImages =
        sc.genericArrayImagesRegion2D[Float, FloatType](
          testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif", 100, regions)
      // roiImages.count shouldBe 400
      val results = roiImages.mapValues(_.getArray.rawArray).mapValues(fArray => (fArray.min,
        fArray.max, fArray.sum, fArray.length)).cache()

      results.filter(_._2._3 > 0).foreach(println(_))
      println(results.collect().mkString("\n"))
    }
  }




}
