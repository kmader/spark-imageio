package fourquant.io

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.{File, FileInputStream, InputStream}
import javax.imageio.ImageIO
import javax.imageio.spi.IIORegistry
import javax.imageio.stream.ImageInputStream

import fourquant.io.BufferedImageOps._
import org.apache.spark.SparkContext
import org.apache.spark.input.PortableDataStream

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.mutable

/**
 * A general set of opertions for importing images
 * Created by mader on 2/27/15.
 */
object ImageIOOps extends Serializable {

  import org.geotoolkit.image.io.plugin.RawTiffImageReader
  { // append the tiff file reading
    val registry = IIORegistry.getDefaultInstance()
    registry.registerServiceProvider(new RawTiffImageReader.Spi())
  }
  case class ImageInfo(count: Int, height: Int, width: Int, info: String)

  private [io] def createStream(input: InputStream) = ImageIO.createImageInputStream(input)
  private [io] def getReader(stream: ImageInputStream) = {
    stream.seek(0)
    ImageIO.getImageReaders(stream).toList.headOption.map(reader => {
      reader.setInput(stream)
      reader
    })
  }

  private [io] def getImageInfo(stream: ImageInputStream) = {
    getReader(stream) match {
      case Some(reader) =>
        ImageInfo(reader.getNumImages(true),reader.getHeight(0),reader.getWidth(0),
          reader.getFormatName)
      case None =>
        ImageInfo(0,-1,-1,"")
    }
  }

  def readTile(infile: File, x: Int, y: Int, w: Int, h: Int): Option[BufferedImage] =
    readTile(createStream(new FileInputStream(infile)),x,y,w,h)

  private[io] def readTile(stream: ImageInputStream, x: Int, y: Int, w: Int, h: Int):
   Option[BufferedImage]= {
    val sourceRegion = new Rectangle(x, y, w, h) // The region you want to extract
    getReader(stream) match {
      case Some(reader) =>
        val param = reader.getDefaultReadParam()
        param.setSourceRegion(sourceRegion); // Set region
        val oBM = reader.read(0, param)
        Some(oBM) // Will read only the region specified
      case None => None
    }
  }

  private[io] def readTileArray[T: ArrayImageMapping](stream: ImageInputStream,
    x: Int, y: Int, w: Int, h: Int): Option[Array[Array[T]]] = {
    readTile(stream,x,y,w,h).map(_.as2DArray[T])
  }

  private[io] def readTileDouble(stream: ImageInputStream,
                                                      x: Int, y: Int, w: Int, h: Int):
  Option[Array[Array[Double]]] = {
    readTile(stream,x,y,w,h).map(_.as2DArray[Double])
  }

  private def roundDown(a: Int, b: Int): Int = {
    a % b match {
      case 0 => Math.floor(a/b).toInt-1
      case _ => Math.floor(a*1.0/b).toInt
    }
  }

  private[io] def makeTileROIS(fullWidth: Int, fullHeight: Int, tileWidth: Int, tileHeight: Int) = {
    // Float.MinPositiveValue means it rounds down at 1.00
    val endXtile =roundDown(fullWidth, tileWidth)
    val endYtile = roundDown(fullHeight,tileHeight)
    for(stx <- 0 to endXtile; sty<- 0 to endYtile)
      yield (stx*tileWidth,sty*tileHeight,tileWidth,tileHeight)
  }



  implicit class iioSC(sc: SparkContext) extends Serializable {
    def readTiledImage[T : ArrayImageMapping](path: String, tileWidth: Int, tileHeight: Int,
                                             partitionCount: Int
                                               ) = {
      sc.binaryFiles(path).mapValues{
        case pds: PortableDataStream =>
          val imInfo = getImageInfo(createStream(pds.open()))
          (pds,imInfo)
      }.flatMapValues{
        case (pds: PortableDataStream, info: ImageInfo) =>
          for(cTile <- makeTileROIS(info.width,info.height,tileWidth,tileHeight))
            yield (pds,cTile)
      }.repartition(partitionCount).mapPartitions{
        inPart =>
          // reuise the open portabledatastreams to avoid reopening and copying the file
          var streamLog = new mutable.HashMap[String,ImageInputStream]()
          for (cTileChunk <- inPart;
               curPath = cTileChunk._1;
               curStream = streamLog.getOrElseUpdate(curPath,createStream(cTileChunk._2._1.open));
                sx = cTileChunk._2._2._1;
                sy = cTileChunk._2._2._2;
               curTile <- readTileArray[T](curStream,sx,sy, tileWidth,tileHeight)
          )
            yield ((curPath,sx,sy),curTile)
      }
    }
    def readTiledDoubleImage(path: String, tileWidth: Int, tileHeight: Int,
                              partitionCount: Int) =
      readTiledImage[Double](path,tileWidth,tileHeight,partitionCount)
  }



}
