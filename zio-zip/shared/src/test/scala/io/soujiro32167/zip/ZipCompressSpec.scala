package io.soujiro32167.zip

import java.nio.file.Files
import java.util.zip.ZipFile

import zio.{console, _}
import zio.stream.{Stream, ZSink}
import zio.test.Assertion._
import zio.test._

import scala.jdk.CollectionConverters._


object ZipCompressSpec extends DefaultRunnableSpec{
  def spec = suite("zip compression")(
    testM("single entry"){
      val zipFile = Files.createTempFile("derp", ".zip")
      println(zipFile.toString)
      ZipCompress.zip()(Stream(
        "myfile" -> Stream(Chunk.fromArray("I am a file".getBytes))
      ))
        .tap(c => console.putStrLn(s"producing chunk of length ${c.length}"))
        .run(ZSink.fromOutputStream(Files.newOutputStream(zipFile)))
        .map(b => console.putStrLn(s"wrote $b bytes")) *>
        Task(new ZipFile(zipFile.toFile))
          .map(zip => zip -> zip.entries().asScala.toList)
          .map{ case (zip, entries) => entries.map(entry => entry.getName -> new String(zip.getInputStream(entry).readAllBytes())) }
          .map(assert(_)(equalTo(List("myfile" -> "I am a file"))))
    },

    testM("multi entry"){
      val zipFile = Files.createTempFile("derp", ".zip")
      println(zipFile.toString)
      ZipCompress.zip()(Stream(
        "myfile1" -> Stream(Chunk.fromArray("f1".getBytes)),
        "myfile2" -> Stream(Chunk.fromArray("f2".getBytes))
      ))
        .tap(c => console.putStrLn(s"producing chunk of length ${c.length}"))
        .run(ZSink.fromOutputStream(Files.newOutputStream(zipFile)))
        .map(b => console.putStrLn(s"wrote $b bytes")) *>
        Task(new ZipFile(zipFile.toFile))
          .map(zip => zip -> zip.entries().asScala.toList)
          .map{ case (zip, entries) => entries.map(entry => entry.getName -> new String(zip.getInputStream(entry).readAllBytes())) }
          .map(assert(_)(equalTo(List(
            "myfile1" -> "f1",
            "myfile2" -> "f2"
          ))))
    }
  )
}
