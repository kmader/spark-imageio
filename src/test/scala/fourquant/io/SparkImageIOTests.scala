package fourquant.io

import org.apache.spark.SparkContext
import org.scalatest.{FunSuite, Matchers}
import fourquant.io.ImageIOOps._

class SparkImageIOTests extends FunSuite with Matchers {
  lazy val sc = new SparkContext("local[4]","Test")
  val testDataDir = "/Users/mader/Dropbox/Informatics/spark-imageio/test-data/"
  test("Load image in big tiles") {
    val tImg = sc.readTiledDoubleImage(testDataDir+"Hansen_GFC2014_lossyear_00N_000E.tif",
      1000,1000,100)
    //tImg.count shouldBe 1600
    tImg.first._2.length shouldBe 1000
    tImg.first._2(0).length shouldBe 1000
  }

  test("Load image in big flat") {
    val tImg = sc.readTiledDoubleImage(testDataDir+"Hansen_GFC2014_lossyear_00N_000E.tif",
      1000,1000,100)
    val imgHist = tImg.flatMap{
      case(blockpos,blockdata) =>
        for(cval <- blockdata.flatten) yield cval
    }.histogram(100)

  }



}
