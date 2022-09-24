package io.soujiro32167

import io.soujiro32167.implicits.*

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import zio.blocking.Blocking
import zio.*
import zio.stream.{Stream, ZSink, ZStream, ZTransducer}
import zio.test.Assertion.*
import zio.test.*

import scala.jdk.CollectionConverters.*

object ZipCompressSpec extends DefaultRunnableSpec {
  def spec: Spec[Environment, TestFailure[Throwable], TestSuccess] = suite("zip compression")(
    testM("single entry") {
      val zipFile =
        Files.createTempFile("derp", ".zip")

      val s          = Stream("myfile" -> Stream.fromIterable("I am a file".getBytes))
      val compressed = s.compress.run(ZSink.fromOutputStream(Files.newOutputStream(zipFile)))
      compressed *>
        Task(new ZipFile(zipFile.toFile))
          .map(zip => zip -> zip.entries().asScala.toList)
          .map { case (zip, entries) =>
            entries.map(entry => entry.getName -> new String(zip.getInputStream(entry).readAllBytes()))
          }
          .map(assert(_)(equalTo(List("myfile" -> "I am a file"))))
    },
    testM("multi entry") {
      val zipFile = Files.createTempFile("derp", ".zip")
      Stream("myfile1" -> Stream.fromIterable("f1".getBytes), "myfile2" -> Stream.fromIterable("f2".getBytes)).compress
//        .tap(c => console.putStrLn(s"producing chunk of length ${c.length}"))
        .run(ZSink.fromOutputStream(Files.newOutputStream(zipFile))) *>
        // .map(b => console.putStrLn(s"wrote $b bytes")) *>
        Task(new ZipFile(zipFile.toFile))
          .map(zip => zip -> zip.entries().asScala.toList)
          .map { case (zip, entries) =>
            entries.map(entry => entry.getName -> new String(zip.getInputStream(entry).readAllBytes()))
          }
          .map(assert(_)(equalTo(List("myfile1" -> "f1", "myfile2" -> "f2"))))
    },
    testM("unzip from premade file") {
      val fruits = getClass.getResource("/fruits.zip")
      for {
        expected <- Task(new ZipFile(new File(fruits.toURI)))
          .map(zip => zip -> zip.entries().asScala.toList)
          .map { case (zip, entries) =>
            entries.map(entry => entry.getName -> new String(zip.getInputStream(entry).readAllBytes()))
          }
        s = ZStream
          .fromInputStream(fruits.openStream())
          .via(ZipCompress.unzip[Blocking]())
        actual <- s.mapM { case (name, content) =>
          content
            .transduce(ZTransducer.utf8Decode)
            .run(ZSink.foldLeft("")(_ + _))
            .map(name -> _)
        }.runCollect
      } yield assert(actual.toList)(equalTo(expected))
    },
    test("Supports sub streams with varying envs and errors") {
      trait A
      trait B
      trait Error
      trait ErrorSub extends Error
      def toCompress: ZStream[Has[A], Error, (String, ZStream[Has[B], ErrorSub, Byte])] = ???
      def errorMapper: Throwable => Error                                               = ???
      lazy val _: ZStream[Has[A] & Has[B] & Blocking, Error, Byte] =
        toCompress.via[Has[A] & Has[B] & Blocking, Error, Byte](ZipCompress.zip(errorMapper = errorMapper))
      assertCompletes
    }
  )
}
