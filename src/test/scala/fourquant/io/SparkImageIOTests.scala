package fourquant.io

import fourquant.arrays.{BreezeOps, Positions}
import fourquant.io.ImageIOOps._
import fourquant.tiles.{TilingStrategies, TilingStrategy2D}
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.{FunSuite, Matchers}

class SparkImageIOTests extends FunSuite with Matchers {
  val useCloud = false
  val useLocal = true
  val bigTests = true
  lazy val sconf = {
    //System.setProperty("spark.executor.memory", "20g")

    val nconf = if(useLocal) {
      new SparkConf().setMaster("local[4]")
    } else {
      new SparkConf().setMaster("spark://merlinc60:7077") //"spark://MacBook-Air.local:7077"
    }
    nconf.//setExecutorEnv("spark.executor.memory", "20g").
      set("spark.executor.memory", "20g").
      setAppName(classOf[SparkImageIOTests].getCanonicalName)
  }
  lazy val sc = {
    println(sconf.toDebugString)
    var tsc = new SparkContext(sconf)
    if (!useLocal) {
      SparkContext.jarOfClass(classOf[SparkImageIOTests]) match {
        case Some(jarFile) =>
          println("Adding "+jarFile)
          tsc.addJar(jarFile)
        case None =>
          println("Jar File missing")
      }
      tsc.addJar("/Users/mader/Dropbox/Informatics/spark-imageio/assembly/target/spio-assembly-0.1-SNAPSHOT.jar")
      tsc.addJar("/Users/mader/Dropbox/Informatics/spark-imageio/target/spark-imageio-1.0-SNAPSHOT-tests.jar")
    }
    tsc
  }

  val testDataDir = if(useLocal) {
    "file:/Users/mader/Dropbox/Informatics/spark-imageio/test-data/"
  } else {
    "/scratch/"
  }


  test("Load image in big tiles") {
    import TilingStrategies.Grid._
    val tImg = sc.readTiledDoubleImage(testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",
      1000, 1000, 100)
    //tImg.count shouldBe 1600
    tImg.first._2.length shouldBe 1000
    tImg.first._2(0).length shouldBe 1000
  }

  test("Spot Check real Data from the big image as double") {
    // just one tile
    implicit val ts = new TilingStrategy2D() {
      override def createTiles2D(fullWidth: Int, fullHeight: Int, tileWidth: Int, tileHeight:
      Int): Array[(Int, Int, Int, Int)] = Array((36000,8000,2000,2000))
    }
    val tImg = sc.readTiledImage[Double](testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",
      2000,2000,100)

    import BreezeOps._
    tImg.count shouldBe 1
    tImg.getTileStats.collect.headOption match {
      case Some((cKey,cTile)) =>
        cTile.min shouldBe 0
        cTile.max shouldBe 13
        (cTile.mean*cTile.count) shouldBe 1785.0 +- 0.5
        println("Non Zero Elements:"+cTile.nzcount)
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
    import BreezeOps._
    tImg.count shouldBe 1
    tImg.getTileStats.collect.headOption match {
      case Some((cKey,cTile)) =>
        cTile.min shouldBe 0
        cTile.max shouldBe 13
        (cTile.mean*cTile.count) shouldBe 1785.0 +- 0.5
        println("Non Zero Elements:"+cTile.nzcount)
      case None =>
        false shouldBe true
    }
  }

  test("load image as double") {
    import TilingStrategies.Grid._
    val tImg = sc.readTiledImage[Double](testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",
      2000,2000,100)
    //tImg.count shouldBe 64
    import BreezeOps._
    val results = tImg.getTileStats.cache()
    results.filter(_._2.nzcount>0).foreach { cTile => println(cTile._1+" => "+cTile._2)}
    println("Final Results: "+results.collect().mkString(", "))
  }

  test("load image as char") {
    import TilingStrategies.Grid._
    val tImg = sc.readTiledImage[Char](testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",
      2000,2000,100)
    //tImg.count shouldBe 64
    import BreezeOps._
    val results = tImg.getTileStats.cache()
    results.filter(_._2.nzcount>0).foreach { cTile => println(cTile._1+" => "+cTile._2)}
    println("Final Results: "+results.collect().mkString(", "))
  }

  if (bigTests) {
    test("Full Image Tiling and Thresholding Test") {
      import TilingStrategies.Grid._

      val tiledImage = sc.readTiledDoubleImage(testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",
        1000, 2000, 80)

      val fTile = tiledImage.first
      fTile._2.length shouldBe 2000
      fTile._2(0).length shouldBe 1000

      val lengthImage = tiledImage.mapValues(_.length)

      import Positions._
      import BreezeOps._

      val nonZeroEntries = tiledImage.sparseThresh(_>0)

      val nzCount = nonZeroEntries.count()
      val tileCount = tiledImage.count()
      println(("Tile Count",tileCount,"Non-Zero Count",nzCount))

      tileCount shouldBe 800
      nzCount shouldBe 1000
    }
  }

  if (useCloud) {
    test("Cloud Test") {
      import TilingStrategies.Grid._
      sc.hadoopConfiguration.set("fs.s3n.awsAccessKeyId", "AKIAJM4PPKISBYXFZGKA")
      sc.hadoopConfiguration.set("fs.s3n.awsSecretAccessKey",
        "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")

      val tiledImage = sc.readTiledDoubleImage("s3n://geo-images/*.tif", 1000, 2000, 80)
      val fTile = tiledImage.first
      fTile._2.length shouldBe 2000
      fTile._2(0).length shouldBe 1000

      val lengthImage = tiledImage.mapValues(_.length)

      import Positions._
      import BreezeOps._

      val nonZeroEntries = tiledImage.sparseThresh(_>0)

      val nzCount = nonZeroEntries.count()
      val tileCount = tiledImage.count()
      println(("Tile Count",tileCount,"Non-Zero Count",nzCount))

      tileCount shouldBe 800
      nzCount shouldBe 1000
    }
  }

}
