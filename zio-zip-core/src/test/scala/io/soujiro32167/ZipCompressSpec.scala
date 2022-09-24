package io.soujiro32167

import io.soujiro32167.models.ZipEntry

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import zio.*
import zio.stream.{Stream, ZPipeline, ZSink, ZStream}
import zio.test.Assertion.*
import zio.test.*

import scala.jdk.CollectionConverters.*

object ZipCompressSpec extends ZIOSpecDefault {
  def spec = suite("zip compression")(
    test("single entry") {
      val zipFile =
        Files.createTempFile("derp", ".zip")

      val s = ZStream("myfile" -> ZStream.fromIterable("I am a file".getBytes))
      val compressed = s.viaFunction(ZipCompress.zip())
        .run(ZSink.fromOutputStream(Files.newOutputStream(zipFile)))
      compressed *>
        ZIO.attempt(new ZipFile(zipFile.toFile))
          .map(zip => zip -> zip.entries().asScala.toList)
          .map { case (zip, entries) =>
            entries.map(entry => entry.getName -> new String(zip.getInputStream(entry).readAllBytes()))
          }
          .map(res => assertTrue(res == List("myfile" -> "I am a file")))
    },
    test("multi entry") {
      val zipFile = Files.createTempFile("derp", ".zip")
      ZStream("myfile1" -> ZStream.fromIterable("f1".getBytes), "myfile2" -> ZStream.fromIterable("f2".getBytes))
        .viaFunction(ZipCompress.zip())
        .run(ZSink.fromOutputStream(Files.newOutputStream(zipFile))) *>
        ZIO.attempt(new ZipFile(zipFile.toFile))
          .map(zip => zip -> zip.entries().asScala.toList)
          .map { case (zip, entries) =>
            entries.map(entry => entry.getName -> new String(zip.getInputStream(entry).readAllBytes()))
          }
          .map(res => assertTrue(res == List("myfile1" -> "f1", "myfile2" -> "f2")))
    },
    test("unzip from premade file") {
      val fruits = getClass.getResource("/fruits.zip")
      for {
        expected <- ZIO.attempt(new ZipFile(new File(fruits.toURI)))
          .map(zip => zip -> zip.entries().asScala.toList)
          .map { case (zip, entries) =>
            entries.map(entry => entry.getName -> new String(zip.getInputStream(entry).readAllBytes()))
          }
        s = ZStream
          .fromInputStream(fruits.openStream())
          .viaFunction(ZipCompress.unzip())
        actual <- s
          .mapZIO { case (name, content) =>
            content
              .via(ZPipeline.utf8Decode)
              .run(ZSink.foldLeft("")(_ + _))
              .map(name -> _)
          }
          .runCollect
      } yield assert(actual.toList)(equalTo(expected))
    },
    test("Supports sub streams with varying envs and errors"){
      trait R1
      trait R2
      trait E1
      trait E2
      def toCompress: ZStream[R1, E1, ZipEntry[R2, E2]] = ???
      lazy val s: ZStream[R1 & R2, E1 | E2 | Throwable, Byte] =
        toCompress.viaFunction(ZipCompress.zip())

      extension[R, E, A] (self: ZStream[R, E | Throwable, A])
        inline def orDiePartial(implicit trace: Trace): ZStream[R, E, A] =
          self.catchAll {
            case e: Throwable => ZStream.die(e)
            case e: E => ZStream.fail(e)
          }

      lazy val s2: ZStream[R1 & R2, E1 | E2, Byte] = s.orDiePartial

      assertCompletes
    }
  )
}
