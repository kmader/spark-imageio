package fourquant.io

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io._
import javax.imageio.ImageIO
import javax.imageio.spi.IIORegistry
import javax.imageio.stream.ImageInputStream

import fourquant.io.BufferedImageOps._
import fourquant.tiles.TilingStrategy2D
import org.apache.spark.SparkContext
import org.apache.spark.input.PortableDataStream

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.mutable

/**
 * A general set of opertions for importing images
 * Created by mader on 2/27/15.
 */
object ImageIOOps extends Serializable {
  val verbose = false
  import org.geotoolkit.image.io.plugin.RawTiffImageReader
  { // append the tiff file reading
    val registry = IIORegistry.getDefaultInstance()
    registry.registerServiceProvider(new RawTiffImageReader.Spi())
  }
  case class ImageInfo(count: Int, height: Int, width: Int, info: String)

  def createStream(input: InputStream) = ImageIO.createImageInputStream(input)

  def getReader(stream: ImageInputStream,suffix: Option[String] = None) = {
    // reset the stream to beginning (if possible)
    stream.seek(0)
    val sufReader = (for(sf<-suffix.toList;
                        foundReader <- ImageIO.getImageReadersBySuffix(sf).toList)
                              yield foundReader)
    val streamReader = ImageIO.getImageReaders(stream).toList
    if (verbose) println("\tTotal Readers found:"+sufReader.length+", "+streamReader.length)
    val bestReader = (sufReader.headOption,streamReader.headOption) match {
      case (Some(reader),_) => Some(reader) // prefer the suffix based reader
      case (None,Some(reader)) => Some(reader)
      case (None,None) => None
    }
    bestReader.map(reader => {
      reader.setInput(stream)
      reader
    })
  }


  def getImageInfo(stream: ImageInputStream) = {
    getReader(stream) match {
      case Some(reader) =>
        ImageInfo(reader.getNumImages(true),reader.getHeight(0),reader.getWidth(0),
          reader.getFormatName)
      case None =>
        ImageInfo(0,-1,-1,"")
    }
  }

  def readTile(infile: File, x: Int, y: Int, w: Int, h: Int): Option[BufferedImage] =
    readTile(createStream(new FileInputStream(infile)),
      infile.getName().split("[.]").reverse.headOption,
      x,y,w,h)

  private[io] def readTile(stream: ImageInputStream, suffix: Option[String],
                           x: Int, y: Int, w: Int, h: Int):
   Option[BufferedImage] = {
    val sourceRegion = new Rectangle(x, y, w, h) // The region you want to extract

    getReader(stream,suffix) match {
      case Some(reader) =>
        val param = reader.getDefaultReadParam()
        param.setSourceRegion(sourceRegion); // Set region
        try {
          Some(reader.read(0, param))
        } catch {
          case  _ : Throwable =>
            System.err.println("Image cannot be loaded, or likely is out of bounds, returning none")
            None
        }
      case None => None
    }
  }

  def readTileArray[T: ArrayImageMapping](stream: ImageInputStream,suffix: Option[String],
    x: Int, y: Int, w: Int, h: Int): Option[Array[Array[T]]] = {
    readTile(stream,suffix,x,y,w,h).map(_.as2DArray[T])
  }

  private[io] def readTileDouble(stream: ImageInputStream,suffix: Option[String],
                                                      x: Int, y: Int, w: Int, h: Int):
  Option[Array[Array[Double]]] = {
    readTile(stream,suffix,x,y,w,h).map(_.as2DArray[Double])
  }



  def readImageAsTiles[T: ArrayImageMapping](stream: ImageInputStream,suffix: Option[String],
                                              tileWidth: Int, tileHeight: Int)(
    implicit ts: TilingStrategy2D) = {
    val info = getImageInfo(stream)
    ts.createTiles2D(info.width,info.height,tileWidth,tileHeight).flatMap {
      case (x, y, width, height) =>
        for(cTile<-readTileArray[T](stream,suffix,x,y,width,height))
          yield ((x,y),cTile)
    }
  }


  /**
   * Keep all the scifio related tools together
   */
  object Scifio extends Serializable{
    def readImageAsTiles(path: String, tileWidth: Int, tileHeight: Int)(
      implicit ts: TilingStrategy2D) = {
      val (creader,meta) = ScifioOps.readPath(path)
      val imgMeta = meta.get(0)
      val axLen = imgMeta.getAxesLengths()
      val width = axLen(0).toInt
      val height = axLen(1).toInt
      ts.createTiles2D(width,height,tileWidth,tileHeight).map {
        case (x, y, width, height) =>
           ((x,y),creader.openPlane(0,0,Array[Long](x,y),Array[Long](width,height)))
      }
    }
  }



  def scifioReadTile() = {

  }


  private[io] object Utils {
    def cachePDS(pds: PortableDataStream): InputStream =
      new ByteArrayInputStream(pds.toArray())

    /**
     *
     * @param filepath the given file path
     * @return if it is a local file
     */
    private def isPathLocal(filepath: String): Boolean = {
      try {
        new File(filepath).exists()
      } catch {
        case _: Throwable => false
      }
    }
    /**
     * Provides a local path for opening a PortableDataStream
     * @param pds
     * @param suffix
     * @return
     */
    private def flattenPDS(pds: PortableDataStream, suffix: String): String = {
      if (isPathLocal(pds.getPath)) {
        pds.getPath
      } else {
        println("Copying PDS Resource....")
        val bais = new ByteArrayInputStream(pds.toArray())
        val tempFile = File.createTempFile("spio","."+suffix)
        org.apache.commons.io.IOUtils.copy(bais,new FileOutputStream(tempFile))
        tempFile.getAbsolutePath
      }
    }
  }

  implicit class scifioSC(sc: SparkContext) extends Serializable {

  }

  implicit class iioSC(sc: SparkContext) extends Serializable {
    /**
     * Load the image(s) as a series of 2D tiles
     * @param path hadoop-style path to the image files (can contain wildcards)
     * @param tileWidth
     * @param tileHeight
     * @param partitionCount number of partitions (cores * 2-4)
     * @tparam T (the type of the output image)
     * @return an RDD with a key of the image names, and tile coordinates, and a value of the data
     *         as a 2D array typed T
     */
    def readTiledImage[T : ArrayImageMapping](path: String, tileWidth: Int, tileHeight: Int,
                                             partitionCount: Int
                                               )(implicit ts: TilingStrategy2D) = {
      sc.binaryFiles(path).mapValues{
        case pds: PortableDataStream =>
          val imInfo = getImageInfo(createStream(pds.open()))
          (pds,imInfo)
      }.flatMapValues{
        case (pds: PortableDataStream, info: ImageInfo) =>
          for(cTile <- ts.createTiles2D(info.width,info.height,tileWidth,tileHeight))
            yield (pds,cTile)
      }.repartition(partitionCount).mapPartitions{
        inPart =>
          // reuise the open portabledatastreams to avoid reopening and copying the file
          var streamLog = new mutable.HashMap[String,ImageInputStream]()
          for (cTileChunk <- inPart;
               curPath = cTileChunk._1;
               suffix =  curPath.split("[.]").reverse.headOption;
               /*curStream = streamLog.getOrElseUpdate(curPath,
                 createStream(cTileChunk._2._1.open())); **/
                // for now read the tile everytime
                curStream = createStream(cTileChunk._2._1.open());
                sx = cTileChunk._2._2._1;
                sy = cTileChunk._2._2._2;
               curTile <- readTileArray[T](curStream,suffix,sx,sy, tileWidth,tileHeight)
          )
            yield ((curPath,sx,sy),curTile)
      }
    }

    def readTiledDoubleImage(path: String, tileWidth: Int, tileHeight: Int,
                              partitionCount: Int)(implicit ts: TilingStrategy2D) =
      readTiledImage[Double](path,tileWidth,tileHeight,partitionCount)
  }

}
