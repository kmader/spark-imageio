package fourquant.labeling

import fourquant.arrays.ArrayPosition
import org.apache.spark.rdd.RDD

import scala.reflect.ClassTag

/**
 * Created by mader on 4/13/15.
 */
object ConnectedComponents extends Serializable {
  trait LabelCriteria[T] extends Serializable {
    def matches(a: T, b: T): Boolean
  }
  object implicits extends Serializable {
    implicit val IntLabelCriteria = new LabelCriteria[Int] {
      override def matches(a: Int, b: Int): Boolean = (a==b)
    }
    implicit val BoolLabelCriteria = new LabelCriteria[Boolean] {
      override def matches(a: Boolean, b: Boolean): Boolean = (a==b)
    }
  }
  def Labeling2D[A: ArrayPosition, B: LabelCriteria](rdd: RDD[(A,B)],window: (Int, Int) = (1,1))(
      implicit act: ClassTag[A], bct: ClassTag[B]) = {
    var curRdd = rdd.zipWithUniqueId().map(kv => (kv._1._1,(kv._2,kv._1._2)))

    var swapCounts = 1
    var iter = 0

    while (swapCounts>0 & iter<20) {
      val swaps = rdd.sparkContext.accumulator(0L)
      val newRdd = curRdd.flatMap{
        case (pos,value) => spreadPoints(pos,value,window)
      }.groupBy {
        kvpos =>
          val cPos = implicitly[ArrayPosition[A]].getPos(kvpos._1)
          (cPos(0),cPos(1))
      }.mapPartitions {
        cPart =>

          for(
            (pos,values) <- cPart;
            (colKV,cswaps) <- collapsePoint(values.toSeq);
            junk = (swaps.add(cswaps))
          )
            yield colKV
      }
      curRdd = newRdd
      iter+=1
      swapCounts = swaps.value.toInt+1
      println("Current Iteration:"+iter+", swaps:"+swapCounts)
    }
    curRdd
  }

  private [labeling] def spreadPoints[A: ArrayPosition, B](pos: A, pValue: B, window: (Int, Int)) = {
    for(x<- -window._1 to window._1; y<- -window._2 to window._2; samePoint = (x==0) & (y==0))
      yield (implicitly[ArrayPosition[A]].add(pos,Array(x,y)),pValue,samePoint)
  }

  private[labeling] def collapsePoint[A, B: LabelCriteria](values: Seq[(A,(Long,B),Boolean)]) = {
    val (origPt,otherPts) = values.partition(_._3)

      origPt.headOption match {
      case Some(hPoint) =>
        val oldLabel = hPoint._2._1
        val newLabel = (otherPts.
          filter(i => implicitly[LabelCriteria[B]].matches(hPoint._2._2,i._2._2)). // if they match
          map(_._2._1) ++ Seq(oldLabel)).min
        Some(((hPoint._1,(newLabel,hPoint._2._2))),if(oldLabel==newLabel) 0 else 1)
      case None => None
    }
  }
}
