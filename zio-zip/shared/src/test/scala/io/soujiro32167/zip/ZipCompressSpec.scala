package io.soujiro32167.zip

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

import zio.blocking.Blocking
import zio.{console, _}
import zio.stream.{Stream, ZSink, ZStream, ZTransducer}
import zio.test.Assertion._
import zio.test._

import scala.jdk.CollectionConverters._


object ZipCompressSpec extends DefaultRunnableSpec{
  def spec = suite("zip compression")(
    testM("single entry"){
      val zipFile = Files.createTempFile("derp", ".zip")
      println(zipFile.toString)
      Stream(
        "myfile" -> Stream.fromIterable("I am a file".getBytes)
      ).via(ZipCompress.zip)
//        .tap(c => console.putStrLn(s"producing chunk of length ${c.length}"))
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
      Stream(
        "myfile1" -> Stream.fromIterable("f1".getBytes),
        "myfile2" -> Stream.fromIterable("f2".getBytes)
      ).via(ZipCompress.zip)
//        .tap(c => console.putStrLn(s"producing chunk of length ${c.length}"))
        .run(ZSink.fromOutputStream(Files.newOutputStream(zipFile)))
        .map(b => console.putStrLn(s"wrote $b bytes")) *>
        Task(new ZipFile(zipFile.toFile))
          .map(zip => zip -> zip.entries().asScala.toList)
          .map{ case (zip, entries) => entries.map(entry => entry.getName -> new String(zip.getInputStream(entry).readAllBytes())) }
          .map(assert(_)(equalTo(List(
            "myfile1" -> "f1",
            "myfile2" -> "f2"
          ))))
    },

    testM("unzip from premade file"){
      val fruits = getClass.getResource("/fruits.zip")
      for {
        expected <- Task(new ZipFile(new File(fruits.toURI)))
        .map(zip => zip -> zip.entries().asScala.toList)
        .map{ case (zip, entries) => entries.map(entry => entry.getName -> new String(zip.getInputStream(entry).readAllBytes())) }
        actual <- ZStream.fromInputStream(fruits.openStream()).via(ZipCompress.unzip[Blocking])
          .mapM{ case (name, content) => content.transduce(ZTransducer.utf8Decode).run(ZSink.foldLeft("")(_ + _)).map(name -> _) }
          .runCollect
      } yield assert(actual)(equalTo(expected))
    }
  )
}
