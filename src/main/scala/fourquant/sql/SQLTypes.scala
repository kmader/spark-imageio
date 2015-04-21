package fourquant.sql

import fourquant.sql.SQLTypes.{ArrayTile, ByteArrayTileUDT, DoubleArrayTileUDT}
import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.expressions.GenericMutableRow
import org.apache.spark.sql.types._

import scala.reflect.ClassTag

/**
 * Created by mader on 4/21/15.
 */
object SQLTypes {
  abstract class ArrayTileUDT[@specialized(Byte, Char, Short, Int, Long, Float, Double)T]
    extends UserDefinedType[ArrayTile[T]] {
    def getElementType: DataType
    val ct : ClassTag[T]
    override def sqlType: StructType = {
      StructType(
        Seq(
          StructField("rows",IntegerType, nullable=false),
          StructField("cols",IntegerType, nullable=false),
          StructField("array",ArrayType(getElementType,containsNull=false),nullable=false)
        )
      )
    }

    override def serialize(obj: Any): Row = {
      val row = new GenericMutableRow(3)
      obj match {
        case pData: ArrayTile[T] =>
          row.setInt(0,pData.getRows)
          row.setInt(1,pData.getCols)
          row.update(2,pData.flatten.toSeq)
        case _ =>
          throw new RuntimeException("The given object:"+obj+" cannot be serialized by "+this)
      }
      row
    }

    override def deserialize(datum: Any): ArrayTile[T] = {
      datum match {
        case v: ArrayTile[T] =>
          System.err.println("Something strange happened, or was never serialized")
          v
        case r: Row =>
          require(r.length==3,"Wrong row-length given "+r.length+" instead of 3")
          val rows = r.getInt(0)
          val cols = r.getInt(1)
          val inArr = r.getAs[Iterable[T]](2).toArray(ct)
          ArrayTile[T](rows,cols,inArr)(ct)
      }
    }

    override def userClass: Class[ArrayTile[T]] = classOf[ArrayTile[T]]

    override def equals(o: Any) = o match {
      case v: ArrayTile[T] => true
      case _ => false
    }

    override def hashCode = 5577269
    override def typeName = "ArrayTile["+getElementType+"]"
    override def asNullable = this
  }

  trait ArrayTile[@specialized(Byte, Char, Short, Int, Long, Float, Double) T] extends
  Serializable {
    def getArray(): Array[Array[T]]
    def flatten(): Array[T]
    def getRows = getArray.length
    def getCols = getArray()(0).length
    def getRow(i: Int) = getArray()(i)
    def getCol(j: Int) = getArray().map(_(j))
  }

  object ArrayTile extends Serializable {
    def apply[T : ClassTag](rows: Int, cols: Int, inArr: Array[T]) = {
      val outArray = new Array[Array[T]](rows)
      var i = 0
      var ind = 0
      while(i<rows) {
        outArray(i) = new Array[T](cols)
        var j=0
        while(j<cols) {
          outArray(i)(j)=inArr(ind)
          ind+=1
        }
        i+=1
      }
      new ArrayTile[T]{
        override def getArray(): Array[Array[T]] = outArray

        override def flatten(): Array[T] = getArray().flatten
      }
    }
  }

  class DoubleArrayTileUDT extends ArrayTileUDT[Double] {
    override def getElementType: DataType = DoubleType

    override val ct: ClassTag[Double] = implicitly[ClassTag[Double]]
  }
  class ByteArrayTileUDT extends ArrayTileUDT[Byte] {
    override def getElementType: DataType = ByteType

    override val ct: ClassTag[Byte] = implicitly[ClassTag[Byte]]
  }

}


@SQLUserDefinedType(udt = classOf[DoubleArrayTileUDT])
trait DoubleArrayTile extends ArrayTile[Double]

@SQLUserDefinedType(udt = classOf[ByteArrayTileUDT])
trait ByteArrayTile extends ArrayTile[Byte]