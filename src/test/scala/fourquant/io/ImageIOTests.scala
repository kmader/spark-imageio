package fourquant.io

import java.io.{File, FileInputStream}

import org.apache.spark.SparkContext
import org.scalatest.{FunSuite, Matchers}

class ImageIOTests extends FunSuite with Matchers {
  lazy val sc = new SparkContext("local[4]","Test")
  val testDataDir = "/Users/mader/Dropbox/Informatics/spark-imageio/test-data/"
  test("Load tile from big image") {
    val is = new FileInputStream(new File(testDataDir+"Hansen_GFC2014_lossyear_00N_000E.tif"))

    ImageIOOps.readTile(is,0,0,100,100) match {
      case Some(cTile) =>
        cTile.getHeight shouldBe 100
        cTile.getWidth shouldBe 100
      case None =>
        throw new IllegalArgumentException("Cannot be empty")
    }

  }

  test("Read size of big image") {
    val is = new FileInputStream(new File(testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif"))

    val iif = ImageIOOps.getImageInfo(is)
    println(iif)
    iif.height shouldBe 40000
    iif.width shouldBe 40000
    iif.count shouldBe 1
  }

  test("Tile ROI Generation") {
    val ttest = ImageIOOps.makeTileROIS(35,35,10,10)
    ttest.length shouldBe 16

    val astest = ImageIOOps.makeTileROIS(35,35,10,5)
    astest.length shouldBe 32
    println(astest.mkString("\n"))
  }

  test("Load several tiles from a big image") {
    val is = new FileInputStream(new File(testDataDir+"Hansen_GFC2014_lossyear_00N_000E.tif"))
    val ttest = ImageIOOps.makeTileROIS(35,35,10,10)
    val tiles = ttest.flatMap(inPos => ImageIOOps.readTile(is,inPos._1,inPos._2,inPos._3,inPos._4))
    tiles.length shouldBe 16
    all ( tiles.map(_.getWidth()) ) shouldBe 10
    all ( tiles.map(_.getHeight()) ) shouldBe 10

    val atest = ImageIOOps.makeTileROIS(35,35,10,5)
    val stiles = atest.flatMap(inPos => ImageIOOps.readTile(is,inPos._1,inPos._2,inPos._3,inPos._4))
    stiles.length shouldBe 16
    all ( stiles.map(_.getWidth()) ) shouldBe 10
    all ( stiles.map(_.getHeight()) ) shouldBe 5
  }




}
