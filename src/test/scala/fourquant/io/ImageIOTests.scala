package fourquant.io

import java.awt.image.BufferedImage
import java.io._
import javax.imageio.ImageIO
import javax.imageio.stream.ImageInputStream

import fourquant.tiles.TilingStrategies
import fourquant.tiles.TilingStrategies.Simple2DGrid
import org.apache.commons.io.output.ByteArrayOutputStream
import org.scalatest.{FunSuite, Matchers}

class ImageIOTests extends FunSuite with Matchers {

  val testDataDir = "/Users/mader/Dropbox/Informatics/spark-imageio/test-data/"
  val bigImage = testDataDir + "Hansen_GFC2014_lossyear_00N_000E.tif"
  val verbose = false
  val heavy = true

  test("Load a tile from a test image multiple times") {
    val is = ImageTestFunctions.makeVSImg(500, 500, "tif")

    ImageIOOps.readTile(is, None, 0, 0, 100, 100) match {
      case Some(cTile) =>
        cTile.getWidth shouldBe 100
        cTile.getHeight shouldBe 100
        println("Loaded tile is :" + cTile)
      case None =>
        throw new IllegalArgumentException("Cannot be empty")
    }

    ImageIOOps.readTile(is, None, 0, 0, 100, 50) match {
      case Some(cTile) =>
        cTile.getHeight shouldBe 50
        cTile.getWidth shouldBe 100
      case None =>
        throw new IllegalArgumentException("Cannot be empty")
    }

  }

  test("Load tile from big image multiple times") {
    val is = ImageIOOps.createStream(
      new FileInputStream(new File(bigImage))
    )

    ImageIOOps.readTile(is, None, 0, 0, 100, 100) match {
      case Some(cTile) =>
        cTile.getWidth shouldBe 100
        cTile.getHeight shouldBe 100
        println("Loaded tile is :" + cTile)
      case None =>
        throw new IllegalArgumentException("Cannot be empty")
    }

    ImageIOOps.readTile(is, None, 0, 0, 100, 50) match {
      case Some(cTile) =>
        cTile.getHeight shouldBe 50
        cTile.getWidth shouldBe 100
      case None =>
        throw new IllegalArgumentException("Cannot be empty")
    }

  }

  test("Over-read a tile") {
    val is = ImageTestFunctions.makeVSImg(50, 50, "tif")
    ImageIOOps.readTileDouble(is, Some("tif"), 0, 0, 100, 100) match {
      case Some(cTile) =>
        cTile.length shouldBe 50
        cTile(0).length shouldBe 50
      case None =>
        throw new IllegalArgumentException("Cannot be empty")
    }
  }

  test("Read a tile outside of image") {
    val is = ImageTestFunctions.makeVSImg(50, 50, "png")

    ImageIOOps.readTileDouble(is, Some("png"), 100, 100, 100, 100).isEmpty shouldBe true
  }


  test("Load array data from tile") {
    val is = ImageTestFunctions.makeVSImg(500, 500, "tif")

    ImageIOOps.readTileDouble(is, Some("tif"), 0, 0, 100, 200) match {
      case Some(cTile) =>
        cTile.length shouldBe 200
        cTile(0).length shouldBe 100
        cTile(10)(10) shouldBe 255.0 +- 0.1
        val allPix = cTile.flatten

        allPix.min shouldBe 0.0 +- 0.1
        allPix.max shouldBe 255.0 +- 0.1
        (allPix.sum / 255) shouldBe 299.0 +- .5

        println("Loaded tile is :" + cTile)
      case None =>
        throw new IllegalArgumentException("Cannot be empty")
    }

    ImageIOOps.readTileDouble(is, Some("tif"), 10, 5, 25, 30) match {
      case Some(cTile) =>
        cTile.length shouldBe 30
        cTile(0).length shouldBe 25
        cTile(5)(0) shouldBe 255.0 +- 0.1
        println("Loaded tile is :" + cTile)

      case None =>
        throw new IllegalArgumentException("Cannot be empty")
    }

  }


  test("Read size of big image") {
    val is = ImageIOOps.createStream(
      new FileInputStream(new File(bigImage))
    )

    val iif = ImageIOOps.getImageInfo(is)
    println(iif)
    iif.height shouldBe 40000
    iif.width shouldBe 40000
    iif.count shouldBe 1
  }


  test("Load several tiles from a big image") {
    val is = ImageIOOps.createStream(
      new FileInputStream(new File(bigImage))
    )
    val tileStrat = new Simple2DGrid()
    val ttest = tileStrat.createTiles2D(35, 35, 10, 10)
    val tiles = ttest.flatMap(inPos => ImageIOOps.readTile(is, Some("tif"),
      inPos._1, inPos._2, inPos._3, inPos._4))
    tiles.length shouldBe 16
    all(tiles.map(_.getWidth())) shouldBe 10
    all(tiles.map(_.getHeight())) shouldBe 10


    val atest = tileStrat.createTiles2D(10, 10, 4, 3)
    val stiles = atest.flatMap(inPos => ImageIOOps.readTile(is, Some("tif"),
      inPos._1, inPos._2, inPos._3, inPos._4))
    stiles.length shouldBe 12
    all(stiles.map(_.getWidth())) shouldBe 4
    all(stiles.map(_.getHeight())) shouldBe 3
  }

  test("Load all tiles from a big image") {
    val is = ImageIOOps.createStream(
      new FileInputStream(new File(bigImage))
    )
    val info = ImageIOOps.getImageInfo(is)
    val tileStrat = new Simple2DGrid()
    val alltilepositions = tileStrat.createTiles2D(info.width, info.height, 1000, 1000)
    println(alltilepositions.mkString("\n"))
    alltilepositions.length shouldBe 1600
    val subpos = alltilepositions.take(10)

    val tiles = subpos.flatMap(inPos =>
      ImageIOOps.readTile(is, Some("tif"), inPos._1, inPos._2, inPos._3, inPos._4))
    tiles.length shouldBe 10
    all(tiles.map(_.getWidth())) shouldBe 1000
    all(tiles.map(_.getHeight())) shouldBe 1000

  }

  test("Load Real Data from the big image") {
    val is = ImageIOOps.createStream(
      new FileInputStream(new File(bigImage))
    )

    ImageIOOps.readTileArray[Char](is, Some("tif"),36000,8000,2000,2000) match {
      case Some(cTile) =>
        cTile.flatten.min shouldBe 0
        cTile.flatten.max shouldBe 13
        cTile.flatten.map(_.toDouble) shouldBe 1785.0 +- 0.5
      case None =>
        false shouldBe true
    }
  }

  test("Read image as double tiles") {
    import TilingStrategies.Grid._
    val is = ImageTestFunctions.makeVSImg(250, 500, "tif")
    val uniTile = ImageIOOps.readImageAsTiles[Double](is, Some("tif"), 250, 500)
    uniTile.length shouldBe 1
    val uniSum = uniTile.map(_._2.map(i => i.sum).sum).sum

    val imTiles = ImageIOOps.readImageAsTiles[Double](is, Some("tif"), 50, 50)
    imTiles.length shouldBe 50
    val t2 = imTiles.filter(kv => kv._1 ==(50, 50))
    t2.length shouldBe 1
    t2.headOption match {
      case Some(cTile) =>
       //println(cTile._1+" =>\n"+cTile._2.map(_.mkString(",")).mkString("\n"))

        cTile._2(0)(0) shouldBe 255.0 +- 0.5
      case None =>
        false shouldBe true
    }

    val manySum = imTiles.map(_._2.map(i => i.sum).sum).sum

    uniSum shouldBe manySum +- 0.1

  }

  test("Read image as char tiles") {
    import TilingStrategies.Grid._
    val is = ImageTestFunctions.makeVSImg(30, 30, "tif")
    val imTiles = ImageIOOps.readImageAsTiles[Char](is, Some("tif"), 10, 10)
    imTiles.length shouldBe 9
    if (verbose) {
      imTiles.foreach(
        cTile => println(cTile._1 + " =>\n" + cTile._2.map(_.map(_.toInt).mkString(",")).
          mkString("\n"))
      )
    }

    val t2 = imTiles.filter(kv => kv._1 ==(20, 20))
    t2.length shouldBe 1
    t2.headOption match {
      case Some(cTile) =>

        cTile._2(0)(0).toInt shouldBe 255
        cTile._2(0)(3).toInt shouldBe 0
      case None =>
        false shouldBe true
    }

  }
if (heavy) {
  test("Read char tiles from a big image") {
    import TilingStrategies.Grid._
    val is = ImageIOOps.createStream(
      new FileInputStream(new File(bigImage))
    )

    val imTiles = ImageIOOps.readImageAsTiles[Char](is, Some("tif"), 5000, 5000)
    imTiles.length shouldBe 64
    imTiles.headOption match {
      case Some(cTile) =>
        println(cTile._1+" => "+cTile._2.flatten.map(_.toDouble).sum)
      case None =>
        false shouldBe true
    }

  }
}

  test("Test the boundary images") {
    val is = ImageIOOps.createStream(
      new FileInputStream(new File(bigImage))
    )
    val info = ImageIOOps.getImageInfo(is)
    val tileStrat = new Simple2DGrid()
    val alltilepositions = tileStrat.createTiles2D(info.width, info.height, 333, 333)
    alltilepositions.length shouldBe 14641
    val subpos = alltilepositions.takeRight(3)

    val tiles = subpos.flatMap(inPos =>
      ImageIOOps.readTile(is, Some("tif"), inPos._1, inPos._2, inPos._3, inPos._4))

    println(tiles.mkString("\n"))
    tiles.length shouldBe 3

    all(tiles.map(_.getWidth())) shouldBe 40
    all(tiles.map(_.getHeight())) shouldBe 40

  }

}


object ImageTestFunctions extends Serializable {
  def makeImageData(xdim: Int, ydim: Int, os: OutputStream, format: String): OutputStream = {
    val emptyImage = new BufferedImage(xdim, ydim, BufferedImage.TYPE_BYTE_GRAY)
    val g = emptyImage.getGraphics()
    //g.drawString("Hey!",50,50)

    for (i <- 0 to xdim) g.drawRect(i, i, 1, 1)


    ImageIO.write(emptyImage, format, os)
    os
  }

  def makeImage(xdim: Int, ydim: Int, format: String): String = {
    val tempFile = File.createTempFile("junk", "." + format)
    makeImageData(xdim, ydim, new FileOutputStream(tempFile), format)
    println("PNG file written:" + tempFile.getAbsolutePath)
    tempFile.getAbsolutePath
  }

  def makeVImg(xdim: Int, ydim: Int, format: String): InputStream = {
    val baos = new ByteArrayOutputStream()
    makeImageData(xdim, ydim, baos, format)
    new ByteArrayInputStream(baos.toByteArray)
  }

  def makeVSImg(xdim: Int, ydim: Int, format: String): ImageInputStream =
    ImageIOOps.createStream(makeVImg(xdim, ydim, format))

}