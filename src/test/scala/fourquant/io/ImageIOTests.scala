package fourquant.io

import java.io.{File, FileInputStream}

import org.apache.spark.SparkContext
import org.scalatest.{FunSuite, Matchers}

class ImageIOTests extends FunSuite with Matchers {
  lazy val sc = new SparkContext("local[4]","Test")
  val testDataDir = "/Users/mader/Dropbox/Informatics/spark-imageio/test-data/"
  test("Load tile from big image multiple times") {
    val is = ImageIOOps.createStream(
      new FileInputStream(new File(testDataDir+"Hansen_GFC2014_lossyear_00N_000E.tif"))
    )

    ImageIOOps.readTile(is,0,0,100,100) match {
      case Some(cTile) =>
        cTile.getWidth shouldBe 100
        cTile.getHeight shouldBe 100
        println("Loaded tile is :"+cTile)
      case None =>
        throw new IllegalArgumentException("Cannot be empty")
    }

    ImageIOOps.readTile(is,0,0,100,50) match {
      case Some(cTile) =>
        cTile.getHeight shouldBe 50
        cTile.getWidth shouldBe 100
      case None =>
        throw new IllegalArgumentException("Cannot be empty")
    }

  }

  test("Load array data from tile") {
    val is = ImageIOOps.createStream(
      new FileInputStream(new File(testDataDir+"Hansen_GFC2014_lossyear_00N_000E.tif"))
    )

    ImageIOOps.readTileDouble(is,1000,4000,1000,500) match {
      case Some(cTile) =>
        cTile.length shouldBe 500
        cTile(0).length shouldBe 1000
        cTile(10)(10) shouldBe 0.0
        val allPix = cTile.flatten
        allPix.sum shouldBe 100.0
        allPix.max shouldBe 20.0
        allPix.min shouldBe 0.0

        println("Loaded tile is :"+cTile)
      case None =>
        throw new IllegalArgumentException("Cannot be empty")
    }

    ImageIOOps.readTileDouble(is,10,5,25,30) match {
      case Some(cTile) =>
        cTile.length shouldBe 30
        cTile(0).length shouldBe 25
        cTile(0)(5) shouldBe 0.0
        println("Loaded tile is :"+cTile)

      case None =>
        throw new IllegalArgumentException("Cannot be empty")
    }



  }


  test("Read size of big image") {
    val is = ImageIOOps.createStream(
      new FileInputStream(new File(testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif"))
    )

    val iif = ImageIOOps.getImageInfo(is)
    println(iif)
    iif.height shouldBe 40000
    iif.width shouldBe 40000
    iif.count shouldBe 1
  }

  test("Tile ROI Generation") {
    val simpletest = ImageIOOps.makeTileROIS(10,10,10,10)
    simpletest.length shouldBe 1

    val ttest = ImageIOOps.makeTileROIS(35,35,10,10)
    println(ttest.mkString("\n"))
    ttest.length shouldBe 16

    val astest = ImageIOOps.makeTileROIS(35,35,10,5)
    astest.length shouldBe 28
    println(astest.mkString("\n"))
  }

  test("Load several tiles from a big image") {
    val is = ImageIOOps.createStream(
      new FileInputStream(new File(testDataDir+"Hansen_GFC2014_lossyear_00N_000E.tif"))
    )

    val ttest = ImageIOOps.makeTileROIS(35,35,10,10)
    val tiles = ttest.flatMap(inPos => ImageIOOps.readTile(is,inPos._1,inPos._2,inPos._3,inPos._4))
    tiles.length shouldBe 16
    all ( tiles.map(_.getWidth()) ) shouldBe 10
    all ( tiles.map(_.getHeight()) ) shouldBe 10


    val atest = ImageIOOps.makeTileROIS(10,10,4,3)
    val stiles = atest.flatMap(inPos => ImageIOOps.readTile(is,inPos._1,inPos._2,inPos._3,inPos._4))
    stiles.length shouldBe 12
    all ( stiles.map(_.getWidth()) ) shouldBe 4
    all ( stiles.map(_.getHeight()) ) shouldBe 3
  }

  test("Load all tiles from a big image") {
    val is = ImageIOOps.createStream(
      new FileInputStream(new File(testDataDir+"Hansen_GFC2014_lossyear_00N_000E.tif"))
    )
    val info = ImageIOOps.getImageInfo(is)
    val alltilepositions = ImageIOOps.makeTileROIS(info.width,info.height,1000,1000)
    println(alltilepositions.mkString("\n"))
    alltilepositions.length shouldBe 1600
    val subpos = alltilepositions.take(10)

    val tiles = subpos.flatMap(inPos =>
      ImageIOOps.readTile(is,inPos._1,inPos._2,inPos._3,inPos._4))
    tiles.length shouldBe 10
    all ( tiles.map(_.getWidth()) ) shouldBe 1000
    all ( tiles.map(_.getHeight()) ) shouldBe 1000

  }

  test("Test the boundary images") {
    val is = ImageIOOps.createStream(
      new FileInputStream(new File(testDataDir+"Hansen_GFC2014_lossyear_00N_000E.tif"))
    )
    val info = ImageIOOps.getImageInfo(is)
    val alltilepositions = ImageIOOps.makeTileROIS(info.width,info.height,333,333)
    alltilepositions.length shouldBe 14641
    val subpos = alltilepositions.takeRight(3)

    val tiles = subpos.flatMap(inPos =>
      ImageIOOps.readTile(is,inPos._1,inPos._2,inPos._3,inPos._4))

    println(tiles.mkString("\n"))
    tiles.length shouldBe 3

    all ( tiles.map(_.getWidth()) ) shouldBe 40
    all ( tiles.map(_.getHeight()) ) shouldBe 40

  }

}
