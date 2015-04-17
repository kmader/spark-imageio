package fourquant.tiles

import java.awt.{RenderingHints, Graphics2D}
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.imageio.ImageIO

import fourquant.arrays.ArrayPosition
import fourquant.io.BufferedImageOps
import fourquant.io.BufferedImageOps.ArrayImageMapping
import org.apache.spark.rdd.RDD

import scala.reflect.ClassTag

/**
 * Created by mader on 4/16/15.
 */
object Previews extends Serializable {
    object implicits extends Serializable {
      implicit class previewTiles[A: ArrayPosition,
      @specialized(Double, Char, Boolean) B : ArrayImageMapping](
                                                                  rdd: RDD[(A,Array[Array[B]])])(
                                                                  implicit ct: ClassTag[A],
                                                                  btt: ClassTag[Array[Array[B]]]
                                                                  ) extends Serializable {
        def tilePreview(scale: Double) = {
          val sp = rdd.simplePreview(scale)

        }
      }
      implicit class previewImage[A,
      @specialized(Double, Char, Boolean) B : ArrayImageMapping](
                                                       rdd: RDD[(A,Array[Array[B]])])(
                                                                implicit ct: ClassTag[A],
      btt: ClassTag[Array[Array[B]]]
        ) extends Serializable {
        def simplePreview(scale: Double) = {
          rdd.mapValues{
            cArr =>
              val bImg = BufferedImageOps.fromArrayToImage(cArr)
              val sWid = (bImg.getWidth()*scale).toInt
              val sHgt = (bImg.getHeight()*scale).toInt
              val outImg: BufferedImage = new BufferedImage(sWid, sHgt, bImg.getType)
              val g: Graphics2D = outImg.createGraphics
              g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC)
              g.drawImage(bImg, 0, 0, sWid, sHgt, 0, 0, bImg.getWidth, bImg.getHeight, null)
              val os = new ByteArrayOutputStream()
              //val b64 = new Base64.OutputStream(os);
              ImageIO.write(outImg, "png", Base64.getEncoder().wrap(os))
              os.toString(StandardCharsets.ISO_8859_1.name())
          }
        }
      }
    }
}
