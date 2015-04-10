package fourquant.io

import fourquant.io.ScifioOps._
import io.scif.img.{ImgOpener, SCIFIOImgPlus}
import net.imglib2.`type`.NativeType
import net.imglib2.`type`.numeric.RealType
import net.imglib2.`type`.numeric.real.{FloatType,DoubleType}
import net.imglib2.`type`.numeric.integer.{ByteType, LongType, IntType}
import net.imglib2.img.ImgFactory
import net.imglib2.img.array.{ArrayImg, ArrayImgFactory}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.reflect.ClassTag

//import net.imglib2.`type`.numeric.real.FloatType
//import net.imglib2.`type`.numeric.integer.IntType

import scala.collection.JavaConversions._

/**
 * A general set of opertions for importing images
 * Created by mader on 2/27/15.
 */
object IOOps {

  /**
   * So they needn't be manually created, they can just be implicitly thrown in
   */
  implicit val floatMaker = () => new FloatType()
  implicit val doubleMaker = () => new DoubleType()
  implicit val intMaker = () => new IntType()
  implicit val longMaker = () => new LongType()
  implicit val byteMaker = () => new ByteType()

  implicit class fqContext(sc: SparkContext) extends Serializable {

    private def staticTypeReadImages[T<: RealType[T]](file: String,iFactory: ImgFactory[T],
                                                      iType: T):
    RDD[(String,SCIFIOImgPlus[T])] = {
      sc.binaryFiles(file).mapPartitions{
        curPart =>
          val io = new ImgOpener()
          curPart.flatMap{
            case (filename,pds) =>
              for (img<-io.openPDS[T](filename,pds,iFactory,iType))
              yield (filename,img)
          }
      }
    }

    /**
     * A generic tool for opening images as Arrays
     * @param filepath the path to the files that need to be loaded
     * @param bType a function which creates new FloatType objects and can be serialized
     * @tparam T the primitive type for the array representation of the image
     * @tparam U the imglib2 type for the ArrayImg representation
     * @return a list of pathnames (string) and image objects (SparkImage)
     */
    def genericArrayImages[T,U <: NativeType[U] with RealType[U]](filepath: String)(implicit
                                    tm: ClassTag[T], bType: () => U):
    RDD[(String, ArraySparkImg[T,U])] = {
      sc.binaryFiles(filepath).mapPartitions{
        curPart =>
          val io = new ImgOpener()
          curPart.flatMap{
            case (filename,pds) =>
              for (img<-io.openPDS[U](filename,pds,new ArrayImgFactory[U], bType() ))
              yield (filename,
                new ArraySparkImg[T,U](Right(img.getImg.asInstanceOf[ArrayImg[U,_]]))
                )
          }
      }
    }

    /**
     * A version of generic array Images for float-type images
     * @return float-formatted images
     */
    def floatImages(filepath: String) =
      sc.genericArrayImages[Float,FloatType](filepath)

    /**
     * A version of generic array Images for double-type images
     * @return float-formatted images
     */
    def doubleImages(filepath: String) =
      sc.genericArrayImages[Double,DoubleType](filepath)

    /**
     * A version of generic array Images for float-type images
     * @return float-formatted images
     */
    def intImages(filepath: String) =
      sc.genericArrayImages[Int,IntType](filepath)
      


  }

}
