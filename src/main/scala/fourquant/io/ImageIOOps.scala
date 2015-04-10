package fourquant.io

import java.awt.Rectangle
import java.io.InputStream
import javax.imageio.ImageIO
import javax.imageio.spi.IIORegistry
import org.apache.spark.SparkContext

import scala.collection.JavaConversions.asScalaIterator
/**
 * A general set of opertions for importing images
 * Created by mader on 2/27/15.
 */
object ImageIOOps {

  import org.geotoolkit.image.io.plugin.RawTiffImageReader
  { // append the tiff file reading
    val registry = IIORegistry.getDefaultInstance()
    registry.registerServiceProvider(new RawTiffImageReader.Spi())
  }

  private [io] def getReader(input: InputStream) = {
    val stream = ImageIO.createImageInputStream(input)
    ImageIO.getImageReaders(stream).toList.headOption.map(reader => {
      reader.setInput(stream)
      reader
    })
  }
  case class ImageInfo(count: Int, height: Int, width: Int, info: String)
  private [io] def getImageInfo(input: InputStream) = {
    getReader(input) match {
      case Some(reader) =>
        ImageInfo(reader.getNumImages(true),reader.getHeight(0),reader.getWidth(0),
          reader.getFormatName)
      case None =>
        ImageInfo(0,-1,-1,"")
    }
  }
  private[io] def readTile(input: InputStream, x: Int, y: Int, w: Int, h: Int) = {
    val sourceRegion = new Rectangle(x, y, w, h) // The region you want to extract
    getReader(input) match {
      case Some(reader) =>
        val param = reader.getDefaultReadParam()
        param.setSourceRegion(sourceRegion); // Set region
        Some(reader.read(0, param)) // Will read only the region specified
      case None => None
    }
  }

  private[io] def makeTileROIS(fullWidth: Int, fullHeight: Int, tileWidth: Int, tileHeight: Int) = {
    val endXtile = Math.floor(fullWidth*1.0 / tileWidth).toInt
    val endYtile = Math.floor(fullHeight*1.0 / tileHeight).toInt
    for(stx <- 0 to endXtile; sty<- 0 to endYtile)
      yield (stx*tileWidth,sty*tileHeight,tileWidth,tileHeight)
  }

  implicit class iioSC(sc: SparkContext) {

  }

}
