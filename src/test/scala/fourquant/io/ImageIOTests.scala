package fourquant.io

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

import _root_.io.scif.img.ImgOpener
import fourquant.io.IOOps._
import fourquant.io.ScifioOps.ArraySparkImg
import net.imglib2.`type`.NativeType
import net.imglib2.`type`.numeric.RealType
import net.imglib2.`type`.numeric.real.{DoubleType, FloatType}
import net.imglib2.img.array.{ArrayImg, ArrayImgFactory}
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.scalatest.{FunSuite, Matchers}

import scala.collection.JavaConversions._

class ImageIOTests extends FunSuite with Matchers {
  lazy val sc = new SparkContext("local[4]","Test")

  val io = new ImgOpener()

  val xdim = 100
  val ydim = 120

  lazy val testImgPath = fourquant.io.ImageIOTests.makeImage(xdim,ydim)

  def checkTestImage[U <: NativeType[U] with RealType[U] ](firstImage: ArrayImg[U,_]): Unit = {
    assert(firstImage.dimension(0)==xdim,"has the right width")
    assert(firstImage.dimension(1)==ydim,"has the right height")
    firstImage.numDimensions() match {
      case 2 => "Alright"
      case 3 => assert(firstImage.dimension(2)==1,"has only one slice")
      case 4 =>
        assert(firstImage.dimension(2)==1,"has only one slice")
        assert(firstImage.dimension(3)==1,"has only one slice")
      case _ =>
        assert(firstImage.numDimensions()>3,"Number of dimensions is too high")
    }

      val imgIt =  firstImage.iterator()
   assert(imgIt.next().getRealDouble == 65535.0, "The first value")
    imgIt.next()
    assert(imgIt.next().getRealDouble == 0.0, "The third value")
  }

  test("Create a fake image") {
    val a = new File(testImgPath)
    assert(a.exists,"Does the file exist after creating it")
    val iimg = ImageIO.read(a)
    assert(iimg.getWidth==xdim,"Correct width")
    assert(iimg.getHeight==ydim,"Correct height")
  }

  test("Read a fake image in ImgLib2") {
    val inImage = io.openImgs[FloatType](testImgPath,new ArrayImgFactory[FloatType],new FloatType)
    assert(inImage.size()==1,"There is only one image in the file")
    val firstImage = inImage.head
    checkTestImage(firstImage.getImg().asInstanceOf[ArrayImg[FloatType,_]])
  }


  test("Read a fake image generically spark") {
    val dtg = () => new DoubleType
    val pImgData = sc.genericArrayImages[Double,DoubleType](testImgPath).cache

    assert(pImgData.count==1,"only one image")

    val firstImage = pImgData.first._2.getImg

    checkTestImage(firstImage.asInstanceOf[ArrayImg[DoubleType,_]])

  }

  test("Read and play with a generic image") {
    val dtg = () => new DoubleType
    val pImgData = sc.genericArrayImages[Double,DoubleType](testImgPath).cache
    val indexData = pImgData.map(_._2).flatMap {
      inKV => for(i <- 0 to 5) yield (i,inKV)
    }

    val mangledData = indexData.cartesian(indexData).filter(a => a._1._1==(a._2._1+1))

    assert(mangledData.count==5,"5 images can be mapped from n->n+1")

    val firstImage = mangledData.first._1._2.getImg

    checkTestImage(firstImage.asInstanceOf[ArrayImg[DoubleType,_]])
  }

  test("Read a float image in spark") {
    val pImgData = sc.floatImages(testImgPath).cache

    assert(pImgData.count==1,"only one image")

    val firstImage = pImgData.first._2.getImg
    checkTestImage(firstImage.asInstanceOf[ArrayImg[FloatType,_]])

  }

  test("Read a int image in spark") {
    val pImgData = sc.intImages(testImgPath).cache

    assert(pImgData.count==1,"only one image")

    val firstImage = pImgData.first._2.getImg
    checkTestImage(firstImage.asInstanceOf[ArrayImg[FloatType,_]])

  }

  test("Read a double image in spark") {
    val pImgData = sc.doubleImages(testImgPath).cache

    assert(pImgData.count==1,"only one image")

    val firstImage = pImgData.first._2.getImg
    checkTestImage(firstImage.asInstanceOf[ArrayImg[FloatType,_]])

  }

  test("Gaussian filter a float image") {
    val pImgData = sc.floatImages(testImgPath).
      mapValues{iImg =>
      new ArraySparkImg(
        Right(
        net.imglib2.algorithm.gauss.Gauss.toFloat(Array(3.0,3.0),iImg.getImg).
        asInstanceOf[ArrayImg[FloatType,_]]
        )
      )
    }

    pImgData.count should equal (1)

    val firstImage = pImgData.first._2.getImg

    firstImage.firstElement().getRealDouble should equal (9720.0+-100)



  }

  test("Gaussian apply op") {
    val gaussianOp =
      (x: ArrayImg[FloatType,_]) => net.imglib2.algorithm.gauss.Gauss.toFloat(Array(3.0,3.0),x)
    val pImgData = sc.floatImages(testImgPath).
      mapValues(iImg => iImg.applyOp(gaussianOp))

    assert(pImgData.count==1,"only one image")

    val firstImage = pImgData.first._2.getImg

    firstImage.firstElement().getRealDouble should equal (9720.0+-100)

  }

  test("Read a big image in spark") {

    val pImgData = sc.doubleImages("/Users/mader/Dropbox/4Quant/Volume_Viewer_2.tif").cache

    pImgData.count should equal (1)

    val firstImage = pImgData.first._2.getImg.asInstanceOf[ArrayImg[FloatType,_]]
    firstImage.dimension(0) should equal (684)

    firstImage.dimension(1) should equal (800)

  }

}


object ImageIOTests extends Serializable {
  def makeImage(xdim: Int, ydim: Int): String = {
    val tempFile = File.createTempFile("junk",".png")
    val emptyImage = new BufferedImage(xdim,ydim,BufferedImage.TYPE_USHORT_GRAY)
    val g = emptyImage.getGraphics()
    //g.drawString("Hey!",50,50)
    g.drawRect(0,0,1,1)
    ImageIO.write(emptyImage,"png",tempFile)
    println("PNG file written:"+tempFile.getAbsolutePath)
    tempFile.getAbsolutePath

  }
}