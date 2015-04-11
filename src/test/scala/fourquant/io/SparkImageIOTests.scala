package fourquant.io

import fourquant.tiles.TilingStrategies
import org.apache.spark.SparkContext
import org.scalatest.{FunSuite, Matchers}
import fourquant.io.ImageIOOps._

class SparkImageIOTests extends FunSuite with Matchers {
  val useCloud = false
  val useLocal = true
  lazy val sc = if (useLocal) new SparkContext("local[4]", "Test")
  else
    new SparkContext("spark://MacBook-Air.local:7077", "Test")

  val testDataDir = "/Users/mader/Dropbox/Informatics/spark-imageio/test-data/"

  test("Load image in big tiles") {
    import TilingStrategies.Grid._
    val tImg = sc.readTiledDoubleImage(testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",
      1000, 1000, 100)
    //tImg.count shouldBe 1600
    tImg.first._2.length shouldBe 1000
    tImg.first._2(0).length shouldBe 1000
  }
  test("load image as double") {
    import TilingStrategies.Grid._
    val tImg = sc.readTiledImage[Double](testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",
      2000,2000,100)
    //tImg.count shouldBe 64
    val results = tImg.mapValues(_.flatten.sum).cache()
    results.filter(_._2>0).foreach { cTile => println(cTile._1+" => "+cTile._2)}
    println("Final Results: "+results.collect().mkString(", "))
  }
  test("load image as char") {
    import TilingStrategies.Grid._
    val tImg = sc.readTiledImage[Char](testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",
      2000,2000,100)
    //tImg.count shouldBe 64
    val results = tImg.mapValues(_.flatten.map(_.toDouble).sum).cache()
    results.filter(_._2>0).foreach { cTile => println(cTile._1+" => "+cTile._2)}
    println("Final Results: "+results.collect().mkString(", "))
  }
  test("Load image in big flat") {
    import TilingStrategies.Grid._
    val tImg = sc.readTiledDoubleImage(testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",
      1000, 1000, 100)
    val imgHist = tImg.flatMap {
      case (blockpos, blockdata) =>
        for (cval <- blockdata.flatten) yield cval
    } //.histogram(100)

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
