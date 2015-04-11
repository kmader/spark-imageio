package fourquant.io

import java.awt.image.{BufferedImage, DataBufferByte, Raster}

import org.nd4j.linalg.factory.Nd4j

import scala.reflect.ClassTag

/**
 * Created by mader on 4/11/15.
 */
object BufferedImageOps {
  val ALPHA_POS = 0
  val RED_POS = 1
  val GREEN_POS = 2
  val BLUE_POS = 3


  /**
   * Convert the value from a buffered image to an array of type T
   * @tparam T
   */
  trait ImageMapping[T] extends Serializable {
    def fromFloat(d: Float): T
    def fromInt(d: Int): T
    def fromARGB(d: Array[Byte]): T
  }


  /**
   * Convert an array from a buffered image to an array of type T
   * @tparam T
   */
  trait ArrayImageMapping[T] extends ImageMapping[T] {
    implicit def ct: ClassTag[T]
    def fromFloatArr(d: Array[Float]): Array[T] = d.map(fromFloat(_))
    def fromIntArr(d: Array[Int]): Array[T] = d.map(fromInt(_))
    def fromARGBArr(d: Array[Array[Byte]]): Array[T] = d.map(fromARGB(_))
  }


  class DoubleImageMapping(implicit val oct: ClassTag[Double]) extends ArrayImageMapping[Double] {
    override def fromFloat(d: Float): Double = d

    override def fromARGB(d: Array[Byte]): Double =
      (d(RED_POS).toDouble+d(BLUE_POS).toDouble+d(GREEN_POS).toDouble)/3.0

    override def fromInt(d: Int): Double = d.toDouble

    override def fromFloatArr(d: Array[Float]) = d.map(_.toDouble)
    override def fromIntArr(d: Array[Int]) = d.map(_.toDouble)

    override def ct: ClassTag[Double] = oct
  }

  implicit val im = new DoubleImageMapping


  /**
   * Implement all of the basic conversion functions on a bufferimage
   * @param bm
   */
  implicit class fqBufferImage(bm: BufferedImage) {

    def asArray[T : ArrayImageMapping]: Array[T] = {
      val activeRaster: Raster = bm.getData
      val sliceSize = activeRaster.getWidth * activeRaster.getHeight
      bm.getColorModel.getPixelSize match {
        case i if i<=16 =>
          // i (1: boolean, 8: byte, 16: short)
          val gi = activeRaster.getPixels(0, 0, activeRaster.getWidth, activeRaster.getHeight,
            new Array[Int](sliceSize))
          implicitly[ArrayImageMapping[T]].fromIntArr(gi)
        case 24 => //color images
          val ggb = convertTo2DARGB(bm)
          implicitly[ArrayImageMapping[T]].fromARGBArr(ggb)
        case 32 => //float
          val gf = activeRaster.getPixels(0, 0, activeRaster.getWidth, activeRaster.getHeight,
            new Array[Float](sliceSize))
          implicitly[ArrayImageMapping[T]].fromFloatArr(gf)
      }
    }

    def as2DArray[T : ArrayImageMapping]: Array[Array[T]] = {
      val width = bm.getWidth
      val height = bm.getHeight
      implicit val ct = implicitly[ArrayImageMapping[T]].ct
      val result = Array.ofDim[T](height,width)
      var pixel: Int = 0
      var row: Int = 0
      var col: Int = 0
      val flatArray = asArray[T]
      while (pixel < flatArray.length) {
        result(row)(col) = flatArray(pixel)
        col += 1
        pixel += 1
        if (col == width) {
          col = 0
          row += 1
        }
      }
      result
    }

    def asNDImage() = {
      val arr1 = Nd4j.create(Array[Float](1,2,3,4),Array(2,2))
    }

  }


  /**
   * @note taken from http://stackoverflow.com/questions/6524196/java-get-pixel-array-from-image
   * @param image
   * @return
   */
  private def convertTo2DARGB(image: BufferedImage): Array[Array[Byte]] = {
    val pixels: Array[Byte] = (image.getRaster.getDataBuffer.asInstanceOf[DataBufferByte]).getData
    val width = image.getWidth
    val height = image.getHeight
    val hasAlphaChannel = image.getAlphaRaster != null

    val result = Array.ofDim[Byte](height*width,4)

    if (hasAlphaChannel) {
      val pixelLength: Int = 4
        var pixel: Int = 0
        var row: Int = 0
        var col: Int = 0
        while (pixel < pixels.length) {
          {

            val alpha = (pixels(pixel).toInt & 0xff)
            val blue = (pixels(pixel+1).toInt & 0xff)
            val green = (pixels(pixel + 2).toInt & 0xff)
            val red = (pixels(pixel + 3).toInt & 0xff)

            result(col)(ALPHA_POS) = alpha.toByte
            result(col)(RED_POS) = red.toByte
            result(col)(GREEN_POS) = green.toByte
            result(col)(BLUE_POS) = blue.toByte

            col += 1
          }
          pixel += pixelLength
        }
    } else {
      val pixelLength: Int = 3
        var pixel: Int = 0
        var row: Int = 0
        var col: Int = 0
        while (pixel < pixels.length) {
          {
            var argb: Int = 0
            val blue = (pixels(pixel).toInt & 0xff)
            val green = (pixels(pixel + 1).toInt & 0xff)
            val red = (pixels(pixel + 2).toInt & 0xff)

            result(col)(ALPHA_POS) = 0
            result(col)(RED_POS) = red.toByte
            result(col)(GREEN_POS) = green.toByte
            result(col)(BLUE_POS) = blue.toByte
            col += 1
          }
          pixel += pixelLength
        }
    }
    result
  }

}
