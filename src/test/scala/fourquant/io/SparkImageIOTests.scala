package fourquant.io

import org.apache.spark.SparkContext
import org.scalatest.{FunSuite, Matchers}
import fourquant.io.ImageIOOps._

class SparkImageIOTests extends FunSuite with Matchers {
  //lazy val sc = new SparkContext("local[4]","Test")

  lazy val sc = new SparkContext("spark://MacBook-Air.local:7077","Test")
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
    }//.histogram(100)

  }

  test("Cloud Test") {
    sc.hadoopConfiguration.set("fs.s3n.awsAccessKeyId","AKIAJM4PPKISBYXFZGKA")
    sc.hadoopConfiguration.set("fs.s3n.awsSecretAccessKey","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")
    val tiledImage = sc.readTiledDoubleImage("s3n://geo-images/*.tif",1000,10000,40)

    tiledImage.first._2.length shouldBe 10000
    tiledImage.first._2(0).length shouldBe 1000

    val lengthImage = tiledImage.mapValues(_.length)
    tiledImage.count shouldBe 160
  }



}
