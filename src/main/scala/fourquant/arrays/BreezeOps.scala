package fourquant.arrays

import breeze.linalg.{Matrix => _, _}
import org.apache.spark.mllib.linalg.{Matrices, Matrix}
import org.apache.spark.rdd.RDD

import scala.language.implicitConversions

import scala.reflect.ClassTag

/**
 * A collection of tools to integrate more easily with Breeze and MLLib
 * Created by mader on 4/13/15.
 */
object BreezeOps extends Serializable { // have the proper conversions for positions automatically
  implicit class denseVectorRDD[A : ClassTag, B: Numeric](rdd: RDD[(A,DenseVector[B])]) {

  }

  implicit class denseMatrixRDD[A : ClassTag, B: Numeric](rdd: RDD[(A,DenseMatrix[B])])
    extends Serializable {
    lazy val doubleRdd = rdd.mapValues(im => im.mapValues(v => implicitly[Numeric[B]].toDouble
      (v)))

    def asDouble = doubleRdd

    def +[C: Numeric](rddB: RDD[(A,DenseMatrix[C])]) = doubleRdd.join(rddB.asDouble).mapValues{
      case (aMat,bMat) =>
        aMat+bMat
    }
  }

  implicit def arrayRDD2DtoMatrixRDD[A: ClassTag, B: Numeric](rdd: RDD[(A,Array[Array[B]])])(
            implicit bt: ClassTag[Array[Array[B]]], bbt: ClassTag[Array[B]], bbbt: ClassTag[B])
     = rdd.mapValues {
    arr =>
      Matrices.dense(arr.length, arr(0).length,
        arr.flatten[B].map(v => implicitly[Numeric[B]].toDouble(v)))
  }

  implicit class matrixThreshold(im: Matrix) extends Serializable {

    def sparseThreshold(f: (Double) => Boolean) = {
      im.toArray.zipWithIndex.filter{
        case (dblVal,idx) => f(dblVal)
      }.map{
        case (dblVal,idx) =>
          ((Math.round(idx/im.numRows).toInt,idx % im.numRows),dblVal)
      }
    }
  }
  implicit class matrixRDD[A: ClassTag](mrdd: RDD[(A, Matrix)]) extends Serializable {
    /**
     * Apply a threshold to the data
     * @param f the threshold function
     * @return
     */
    def threshold(f: (Double) => Boolean) = {
        mrdd.mapValues(im => new DenseMatrix(im.numRows,im.numCols,im.toArray.map(f)))
    }
    def localSparseThresh(f: (Double) => Boolean) =
      mrdd.mapValues(_.sparseThreshold(f))

    def apply(f: (Double) => Double) =
      mrdd.mapValues(im => Matrices.dense(im.numRows,im.numCols,im.toArray.map(f)))

  }

  implicit class locMatrixRDD[A: ArrayPosition](mrdd: RDD[(A, Matrix)])(
                                               implicit at: ClassTag[A]
    ) extends Serializable {
    def sparseThresh(f: (Double) => Boolean) = {
      mrdd.localSparseThresh(f).flatMap{
        case (cPos,spArray) =>
          for(cPt <- spArray) yield(
            implicitly[ArrayPosition[A]].add(cPos, Array(cPt._1._1,cPt._1._2)),
            cPt._2)
      }
    }
    
  }

}
