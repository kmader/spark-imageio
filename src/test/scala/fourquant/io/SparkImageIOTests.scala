package fourquant.io

import fourquant.io.ImageIOOps._
import fourquant.tiles.{TilingStrategy2D, TilingStrategies}
import org.apache.spark.SparkContext
import org.scalatest.{FunSuite, Matchers}

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

  test("Spot Check  real Data from the big image as double") {
    // just one tile
    implicit val ts = new TilingStrategy2D() {
      override def createTiles2D(fullWidth: Int, fullHeight: Int, tileWidth: Int, tileHeight:
      Int): Array[(Int, Int, Int, Int)] = Array((36000,8000,2000,2000))
    }
    val tImg = sc.readTiledImage[Double](testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",
      2000,2000,100)

    tImg.count shouldBe 1
    tImg.collect.headOption match {
      case Some((cKey,cTile)) =>
        cTile.flatten.min shouldBe 0
        cTile.flatten.max shouldBe 13
        cTile.flatten.sum shouldBe 1785.0 +- 0.5
        println("Non Zero Elements:"+cTile.flatten.filter(_>0).length)
      case None =>
        false shouldBe true
    }
  }
  test("Spot Check  real Data from the big image as char") {
    // just one tile
    implicit val ts = new TilingStrategy2D() {
      override def createTiles2D(fullWidth: Int, fullHeight: Int, tileWidth: Int, tileHeight:
      Int): Array[(Int, Int, Int, Int)] = Array((36000,8000,2000,2000))
    }
    val tImg = sc.readTiledImage[Char](testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",
      2000,2000,100)

    tImg.count shouldBe 1
    tImg.collect.headOption match {
      case Some((cKey,cTile)) =>
        cTile.flatten.min shouldBe 0
        cTile.flatten.max shouldBe 13
        cTile.flatten.map(_.toDouble).sum shouldBe 1785.0 +- 0.5
        println("Non Zero Elements:"+cTile.flatten.filter(_>0).length)
      case None =>
        false shouldBe true
    }
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
