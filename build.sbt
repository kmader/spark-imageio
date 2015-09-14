name := "spark-imageio"

version := "0.1-SNAPSHOT"

organization := "fourquant"

scalaVersion := "2.10.4"

spName := "4quant/spark-imageio"

sparkVersion := "1.4.1"

sparkComponents += "core"

publishMavenStyle := true

licenses += "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")

pomExtra := (
  <url>https://github.com/4quant/spark-imageio</url>
  <scm>
    <url>git@github.com:4quant/spark-imageio.git</url>
    <connection>scm:git:git@github.com:4quant/spark-imageio.git</connection>
  </scm>
  <developers>
    <developer>
      <id>kmader</id>
      <name>Kevin Mader</name>
      <url>https://github.com/kmader</url>
    </developer>
  </developers>)

libraryDependencies += "org.geotoolkit" %% "geotk-coverageio" % "3.21" 
libraryDependencies += "org.nd4j" %% "nd4j-jblas" % "0.0.3.5.5.2" 
libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4" % "test"

