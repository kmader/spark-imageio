package fourquant.io

import fourquant.arrays.{BreezeOps, Positions}
import fourquant.io.ImageIOOps._
import fourquant.io.SparkImageIOTests.GeoPoint
import fourquant.labeling.ConnectedComponents
import fourquant.labeling.ConnectedComponents.LabelCriteria
import fourquant.tiles.{TilingStrategies, TilingStrategy2D}
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.{FunSuite, Matchers}

class SparkImageIOTests extends FunSuite with Matchers {
  val useCloud = false
  val useLocal = true
  val bigTests = false
  lazy val sconf = {
    //System.setProperty("spark.executor.memory", "20g")

    val nconf = if(useLocal) {
      new SparkConf().setMaster("local[4]")
    } else {
      new SparkConf().setMaster("spark://merlinc60:7077"). //"spark://MacBook-Air.local:7077"
        set("spark.local.dir","/scratch/")
    }
    nconf.
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



  test("Quick Component Lablineg") {
    import TilingStrategies.Grid._

    val imgPath = ImageTestFunctions.makeImagePath(50,50,"tif",
      "/Users/mader/Dropbox/Informatics/spark-imageio/test-data/")

    val tiledImage = sc.readTiledDoubleImage(imgPath, 10, 25, 80).cache

    val fTile = tiledImage.first

    fTile._2(0).length shouldBe 10
    fTile._2.length shouldBe 25

    val lengthImage = tiledImage.mapValues(_.length)

    import BreezeOps._
    import Positions._

    val tileCount = tiledImage.count()

    val bm = tiledImage.toMatrixRDD()


    val nonZeroEntries = tiledImage.sparseThresh(_>0).cache

    implicit val doubleLabelCrit = new LabelCriteria[Double] {
      override def matches(a: Double, b: Double): Boolean = true//Math.abs(a-b)<0.75
    }
    val compLabel = ConnectedComponents.Labeling2D(nonZeroEntries,(3,3))

    val components = compLabel.map(_._2._1).countByValue()

    val nzCount = nonZeroEntries.count()

    val histogram = nonZeroEntries.map(_._2).histogram(20)


    println(("Tile Count",tileCount,"Non-Zero Count",nzCount,"Components",components.size))

    println("Histogram"+" "+histogram._1.zip(histogram._2).mkString(" "))
    print("Components:"+components.mkString(", "))

    tileCount shouldBe 300
    nzCount shouldBe 2088
  }

  test("Tiny Image Tiling and Thresholding Test") {
    import TilingStrategies.Grid._

    val imgPath = ImageTestFunctions.makeImage(100,100,"tif")

    sc.addFile(imgPath)

    val tiledImage = sc.readTiledDoubleImage(imgPath.split("/").reverse.head,
        10, 20, 80).cache

    val fTile = tiledImage.first

    fTile._2(0).length shouldBe 10
    fTile._2.length shouldBe 20

    val lengthImage = tiledImage.mapValues(_.length)

    import BreezeOps._
    import Positions._

    val tileCount = tiledImage.count()

    val bm = tiledImage.toBlockMatrix()


    val nonZeroEntries = tiledImage.sparseThresh(_>0)


    val nzCount = nonZeroEntries.count()

    val entries = bm.toCoordinateMatrix().entries.filter(_.value>0)

    val entryCount = entries.count

    val histogram = nonZeroEntries.map(_._2).histogram(20)


    println(("Tile Count",tileCount,"Non-Zero Count",nzCount, "Entries Count",entryCount))

    println("Histogram"+" "+histogram._1.zip(histogram._2).mkString(" "))
    println("Sampling:"+entries.takeSample(false,20).mkString("\n"))

    tileCount shouldBe 500
    nzCount shouldBe 298
    nzCount shouldBe entryCount
  }


  if (bigTests) {
    test("load image as double") {
      import TilingStrategies.Grid._
      val tImg = sc.readTiledImage[Double](testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",
        2000,2000,100)
      //tImg.count shouldBe 64
      import BreezeOps._
      val results = tImg.getTileStats.cache()
      results.filter(_._2.nzcount>0).foreach { cTile => println(cTile._1+" => "+cTile._2)}
      val resTable = results.collect()
      val nzCount  = resTable.filter(_._2.nzcount>0).length
      nzCount shouldBe 13
      println("Final Results (nzTiles:"+nzCount+"): "+resTable.mkString(", "))
    }

    test("load image as char") {
      import TilingStrategies.Grid._
      val tImg = sc.readTiledImage[Char](testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",
        2000,2000,100)
      //tImg.count shouldBe 64
      import BreezeOps._
      val results = tImg.getTileStats.cache()
      results.filter(_._2.nzcount>0).foreach { cTile => println(cTile._1+" => "+cTile._2)}
      val resTable = results.collect()
      val nzCount  = resTable.filter(_._2.nzcount>0).length
      nzCount shouldBe 13
      println("Final Results (nzTiles:"+nzCount+"): "+resTable.mkString(", "))
    }

    test("Full Image Tiling and Thresholding Test") {
      import TilingStrategies.Grid._

      val tiledImage = {
        var tempImg = sc.readTiledDoubleImage(testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif",
          1500, 1500, 80)
        if (useLocal) {
          tempImg
        } else {
          tempImg.cache
        }
      }
      val fTile = tiledImage.first
      fTile._2.length shouldBe 1500
      fTile._2(0).length shouldBe 1500

      val lengthImage = tiledImage.mapValues(_.length)

      import BreezeOps._
      import Positions._

      val tileCount = tiledImage.count()
      val nonZeroEntries = if (useLocal) tiledImage.sparseThresh(_>0) else
        tiledImage.sparseThresh(_>0).cache

      val nzCount = nonZeroEntries.count()

      val histogram = nonZeroEntries.map(_._2).histogram(20)
      val sampleData = nonZeroEntries.map(kv => ((kv._1.getX,kv._1.getY),kv._2)).
        takeSample(false,20)
        .mkString("\n")

      println("Histogram"+" "+histogram._1.zip(histogram._2).mkString(" "))
      println("Sampling:"+sampleData)

      println(("Tile Count",tileCount,"Non-Zero Count",nzCount))



      // spark sql
      val sqlContext = new org.apache.spark.sql.SQLContext(sc)
      import sqlContext.implicits._



      val nzDataFrame = nonZeroEntries.map{
        case (pkey,pvalue) => GeoPoint(pkey._1,pkey._2,pkey._3,(pvalue+2000).toInt)
      }.toDF()

      nzDataFrame.registerTempTable("geopoints")
      nzDataFrame.saveAsParquetFile("map_points")

      tileCount shouldBe 200
      nzCount shouldBe 235439
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

      import BreezeOps._
      import Positions._

      val nonZeroEntries = tiledImage.sparseThresh(_>0)

      val nzCount = nonZeroEntries.count()
      val tileCount = tiledImage.count()
      println(("Tile Count",tileCount,"Non-Zero Count",nzCount))

      tileCount shouldBe 800
      nzCount shouldBe 235439
    }
  }

}
object SparkImageIOTests extends Serializable {
  case class GeoPoint(name: String, i: Int, j: Int, year: Int)
}
