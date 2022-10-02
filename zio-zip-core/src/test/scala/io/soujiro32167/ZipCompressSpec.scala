package io.soujiro32167

import io.soujiro32167.models.ZipEntry
import zio.*
import zio.stream.{ZPipeline, ZSink, ZStream}
import zio.test.*
import zio.test.Assertion.*

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*

object ZipCompressSpec extends ZIOSpecDefault {
  implicit class Silly[E, A](val self: ZIO[Nothing, E, A]) extends AnyVal {
    def widen: ZIO[Any, E, A] = self.asInstanceOf[ZIO[Any, E, A]]
  }
  def spec = suite("zip compression")(
    test("single entry") {
      val file =
        Files.createTempFile("derp", ".zip")

      val s = ZStream("myfile" -> ZStream.fromIterable("I am a file".getBytes))

      val a = for {
        _ <- s
          .viaFunction(ZipCompress.zip())
          .run(ZSink.fromOutputStream(Files.newOutputStream(file)))
          .widen
      } yield {
        val zipFile = new ZipFile(file.toFile)
        val actual = zipFile
          .entries()
          .asScala
          .map(entry => entry.getName -> new String(zipFile.getInputStream(entry).readAllBytes()))
          .toList
        assertTrue(actual == List("myfile" -> "I am a file"))
      }
      a
    },
    test("multi entry") {
      val zipFile = Files.createTempFile("derp", ".zip")
      ZStream("myfile1" -> ZStream.fromIterable("f1".getBytes), "myfile2" -> ZStream.fromIterable("f2".getBytes))
        .viaFunction(ZipCompress.zip())
        .run(ZSink.fromOutputStream(Files.newOutputStream(zipFile)))
        .widen *>
        ZIO
          .attempt(new ZipFile(zipFile.toFile))
          .map(zip => zip -> zip.entries().asScala.toList)
          .map { case (zip, entries) =>
            entries.map(entry => entry.getName -> new String(zip.getInputStream(entry).readAllBytes()))
          }
          .map(res => assertTrue(res == List("myfile1" -> "f1", "myfile2" -> "f2")))
    },
    test("unzip from premade file") {
      val fruits = getClass.getResource("/fruits.zip")
      for {
        expected <- ZIO
          .attempt(new ZipFile(new File(fruits.toURI)))
          .map(zip => zip -> zip.entries().asScala.toList)
          .map { case (zip, entries) =>
            entries.map(entry => entry.getName -> new String(zip.getInputStream(entry).readAllBytes()))
          }
          .widen
        s = ZStream
          .fromInputStream(fruits.openStream())
          .viaFunction(ZipCompress.unzip[Any]())
        actual <- s.mapZIO { case (name, content) =>
          content
            .via(ZPipeline.utf8Decode)
            .run(ZSink.foldLeft("")(_ + _))
            .map(name -> _)
        }.runCollect
      } yield assert(actual.toList)(equalTo(expected))
    },
    test("Supports sub streams with varying envs and errors") {
      trait R1
      trait R2
      trait E1
      trait E2 extends E1
      def toCompress: ZStream[R1, E1, ZipEntry[R2, E2]] = ???
      lazy val f: ZStream[R1 & R2, E1, Byte] =
        toCompress.viaFunction(ZipCompress.zip())

      implicitly[f.type <:< ZStream[R1 & R2, E1, Byte]]

      assertCompletes
    }
  )
}
