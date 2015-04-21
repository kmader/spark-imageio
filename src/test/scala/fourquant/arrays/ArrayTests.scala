package fourquant.arrays

import fourquant.arrays.BreezeOps._
import org.scalatest.{FunSuite, Matchers}
/**
 * Created by mader on 4/13/15.
 */
class ArrayTests extends FunSuite with Matchers {
  val testArray = Array(Array(1.0,2.0,3.0),Array(4.0,5.0,6.0))
  import Positions._
  test("2D Array to Breeze Matrix") {

    val mat = BreezeOps.array2DtoMatrix(testArray)
    mat(0,0) shouldBe 1
    mat.numCols shouldBe 2
    mat.numRows shouldBe 3
    mat(0,1) shouldBe 4
    mat(2,0) shouldBe 3
    mat(2,1) shouldBe 6
  }

  test("Matrix Threshold") {
    val mat = BreezeOps.array2DtoMatrix(testArray)
    val thresh = mat.sparseThreshold(_==3.0)
    thresh.length shouldBe 1
    thresh.head._1.getX shouldBe 2
    thresh.head._1.getY shouldBe 0

    val thresh2 = mat.sparseThreshold(_==5.0)
    thresh2.length shouldBe 1
    thresh2.head._1.getX shouldBe 1
    thresh2.head._1.getY shouldBe 1
  }
  def getX[A : ArrayPosition](a: A) = implicitly[ArrayPosition[A]].getX(a)
  def getY[A : ArrayPosition](a: A) = implicitly[ArrayPosition[A]].getY(a)
  def getPos[A : ArrayPosition](a: A) = implicitly[ArrayPosition[A]].getPos(a)
  def getMeta[A : ArrayPosition](a: A) = implicitly[ArrayPosition[A]].getMetadata(a)
  def setMeta[A : ArrayPosition](a: A, s: String) = implicitly[ArrayPosition[A]].setMetadata(a,s)


  test("Get position information") {
    val tPos = ("junk", 100, 90)
    getX(tPos) shouldBe 100
    getY(tPos) shouldBe 90
    getPos(tPos) shouldBe Array(100L, 90L)
    getMeta(tPos) shouldBe "junk"
    setMeta(tPos, "myjunk")._1 shouldBe "myjunk"
  }

}
