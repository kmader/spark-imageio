package fourquant.labeling

import fourquant.arrays.{BreezeOps, Positions}
import fourquant.io.ImageIOOps._
import fourquant.io.ImageTestFunctions
import fourquant.labeling.ConnectedComponents.LabelCriteria
import fourquant.tiles.TilingStrategies
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.{FunSuite, Matchers}

/**
 * Created by mader on 4/14/15.
 */
class LabelTests extends FunSuite with Matchers {
  val useLocal = true
  val bigTests = false
  lazy val sconf = {
    //System.setProperty("spark.executor.memory", "20g")

    val nconf = if(useLocal) {
      new SparkConf().setMaster("local[4]")
    } else {
      new SparkConf().setMaster("spark://merlinc60:7077"). //"spark://MacBook-Air.local:7077"
        set("spark.local.dir","/scratch/")
    }
    nconf.
      set("spark.executor.memory", "20g").
      setAppName(classOf[LabelTests].getCanonicalName)
  }
  lazy val sc = {
    println(sconf.toDebugString)
    var tsc = new SparkContext(sconf)
    if (!useLocal) {
      SparkContext.jarOfClass(classOf[LabelTests]) match {
        case Some(jarFile) =>
          println("Adding "+jarFile)
          tsc.addJar(jarFile)
        case None =>
          println("Jar File missing")
      }
      tsc.addJar("/Users/mader/Dropbox/Informatics/spark-imageio/assembly/target/spio-assembly-0.1-SNAPSHOT.jar")
      tsc.addJar("/Users/mader/Dropbox/Informatics/spark-imageio/target/spark-imageio-1.0-SNAPSHOT-tests.jar")
    }
    tsc
  }

  val testDataDir = if(useLocal) {
    "file:/Users/mader/Dropbox/Informatics/spark-imageio/test-data/"
  } else {
    "/scratch/"
  }

  test("Spread Point Test") {
    import Positions._
    val (ptpos,ptval) = (("hello",5,10),"myname")

    val onespread = ConnectedComponents.spreadPoints(ptpos,ptval,(1,1))
    onespread.length shouldBe 9

    val (orig,other) = onespread.partition(_._3)
    orig.length shouldBe 1
    other.length shouldBe 8

    onespread.map(_._1.getX).min shouldBe 4
    onespread.map(_._1.getX).max shouldBe 6
    onespread.map(_._1.getY).min shouldBe 9
    onespread.map(_._1.getY).max shouldBe 11


    val twothreespread = ConnectedComponents.spreadPoints(ptpos,ptval,(2,3))
    twothreespread.length shouldBe (5*7)
    twothreespread.map(_._1.getX).min shouldBe 3
    twothreespread.map(_._1.getX).max shouldBe 7
    twothreespread.map(_._1.getY).min shouldBe 7
    twothreespread.map(_._1.getY).max shouldBe 13
  }

  test("Collapse Point Test") {
    val pts = Seq(
      ("0",(2L,"val0"),false),
      ("1",(3L,"val1"),false),
      ("2",(4L,"val2"),true)
    )
    implicit val strComp = new LabelCriteria[String] {
      override def matches(a: String, b: String): Boolean = true // always match
    }
    ConnectedComponents.collapsePoint[String,String](pts) match {
      case Some(((position,(label,pointValue)),swaps)) =>
        swaps shouldBe 1
        position shouldBe "2"
        label shouldBe 2L
        pointValue shouldBe "val2"
      case None => throw new RuntimeException("Failed!")
    }

    val samePts = Seq(
      ("0",(2L,"val0"),true),
      ("1",(3L,"val1"),false),
      ("2",(4L,"val2"),false)
    )

    ConnectedComponents.collapsePoint[String,String](samePts) match {
      case Some(((position,(label,pointValue)),swaps)) =>
        swaps shouldBe 0
        position shouldBe "0"
        label shouldBe 2L
        pointValue shouldBe "val0"
      case None => throw new RuntimeException("Failed!")
    }

    val differentPhases = Seq(
      ("0",(2L,"val0"),false),
      ("1",(3L,"val2"),false),
      ("2",(4L,"val2"),true)
    )
    val differentCriteria = new LabelCriteria[String] {
      override def matches(a: String, b: String): Boolean = a.contentEquals(b) // only if the
      // strings  match
    }

    ConnectedComponents.collapsePoint[String,String](differentPhases)(differentCriteria) match {
      case Some(((position,(label,pointValue)),swaps)) =>
        swaps shouldBe 1
        position shouldBe "2"
        label shouldBe 3L
        pointValue shouldBe "val2"
      case None => throw new RuntimeException("Failed!")
    }


    val emptyPts = Seq(
      ("0",(2L,"val0"),false),
      ("1",(3L,"val1"),false),
      ("2",(4L,"val2"),false)
    )
    ConnectedComponents.collapsePoint[String,String](emptyPts).isEmpty shouldBe true

  }

  test("Tiny CL Test") {
    import Positions._

    val ptList = Seq(
      (("hello",5,10),0.0),
      (("hello",5,11),1.0),
      (("hello",5,12),2.0)
    )
    val ptRdd = sc.parallelize(ptList)

    implicit val doubleLabelCrit = new LabelCriteria[Double] {
      override def matches(a: Double, b: Double): Boolean = true//Math.abs(a-b)<0.75
    }

    val compImg = ConnectedComponents.Labeling2D(ptRdd,(1,1))
    val comps = compImg.map(_._2._1).countByValue()

    println(compImg.collect.mkString("\n"))
    comps.size shouldBe 1
  }
  test("Tiny CL Test 3,3") {
    import Positions._

    val ptList = Seq(
      (("hello",5,10),0.0),
      (("hello",5,11),1.0),
      (("hello",5,12),2.0)
    )
    val ptRdd = sc.parallelize(ptList)

    implicit val newLabelCrit = new LabelCriteria[Double] {
      override def matches(a: Double, b: Double): Boolean = Math.abs(a-b)<0.75
    }

    val comps3 = ConnectedComponents.Labeling2D(ptRdd,(1,1)).map(_._2._1).countByValue()
    comps3.size shouldBe 3

  }

  test("Quick Component Labeling") {
    import TilingStrategies.Grid._

    val imgPath = ImageTestFunctions.makeImagePath(50,50,"tif",
      "/Users/mader/Dropbox/Informatics/spark-imageio/test-data/")

    val tiledImage = sc.readTiledDoubleImage(imgPath, 10, 25, 80).cache

    val fTile = tiledImage.first

    fTile._2(0).length shouldBe 10
    fTile._2.length shouldBe 25

    val lengthImage = tiledImage.mapValues(_.length)

    import BreezeOps._
    import Positions._

    val nonZeroEntries = tiledImage.sparseThresh(_>0).cache

    implicit val doubleLabelCrit = new LabelCriteria[Double] {
      override def matches(a: Double, b: Double): Boolean = true//Math.abs(a-b)<0.75
    }
    val compLabel = ConnectedComponents.Labeling2D(nonZeroEntries,(3,3))

    val components = compLabel.map(_._2._1).countByValue()

    val nzCount = nonZeroEntries.count()


    println(("Non-Zero Count",nzCount,"Components",components.size))

    print("Components:"+components.mkString(", "))

    nzCount shouldBe 2088
  }

}
