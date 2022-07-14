package io.soujiro32167

import java.io.ByteArrayOutputStream
import java.util.zip.{ZipInputStream, ZipOutputStream, ZipEntry => JZipEntry}

import zio.blocking.Blocking
import zio.stream.{Stream, ZStream}
import zio.{Chunk, Managed, Task}
import models._

object ZipCompress {

  sealed private[this] trait Command

  private[this] case class StartEntry(name: String, zip: ZipOutputStream) extends Command

  private[this] case class AddDataToZipEntry(data: Chunk[Byte], zip: ZipOutputStream) extends Command

  private[this] case class EndEntry(name: String, zip: ZipOutputStream) extends Command

  private[this] case class EndFile(zip: ZipOutputStream) extends Command

  def zip(chunkSize: Int = ZStream.DefaultChunkSize): ZPipe[Any, Blocking, Throwable, Throwable, ZipEntry, Byte] = {
    sources =>
      (for {
        baos <- Managed.fromAutoCloseable(Task(new ByteArrayOutputStream(chunkSize)))
        zis  <- Managed.fromAutoCloseable(Task(new ZipOutputStream(baos)))

      } yield (baos, zis)).toStream.flatMap { case (baos, zipStream) =>
        def nextZipChunk: Task[Chunk[Byte]] = Task {
          val zippedData = baos.toByteArray
          baos.reset()
          Chunk.fromArray(zippedData)
        }

        (sources.flatMap { case (fileName, content) =>
          Stream(StartEntry(fileName, zipStream)) ++
            content.mapChunks(chunk => Chunk(AddDataToZipEntry(chunk, zipStream))) ++
            Stream(EndEntry(fileName, zipStream))
        } ++ Stream(EndFile(zipStream)))
          .mapConcatChunkM {
            case StartEntry(filePath, zip) =>
              Task(zip.putNextEntry(new JZipEntry(filePath))) *> nextZipChunk
            case AddDataToZipEntry(data, zip) =>
              Task(zip.write(data.toArray)) *> nextZipChunk
            case EndEntry(_, zip) => Task(zip.closeEntry()) *> nextZipChunk
            case EndFile(zip)     => Task(zip.finish()) *> nextZipChunk
          }
      }
  }

  def unzip[R](chunkSize: Int = ZStream.DefaultChunkSize): ZPipe[R, R, Throwable, Throwable, Byte, ZipEntry] = {
    stream =>

      def entry(zis: ZipInputStream): Task[Option[ZipEntry]] =
        Task(Option(zis.getNextEntry)).map(_.map { ze =>
          ze.getName -> Stream.fromInputStream(zis, chunkSize)
        })

      def unzipEntries(zis: ZipInputStream): Stream[Throwable, ZipEntry] =
        Stream.unfoldM(zis) { zis0 =>
          entry(zis0).map(_.map(_ -> zis0))
        }

      stream.toInputStream
        .flatMap(is => Managed.fromAutoCloseable(Task(new ZipInputStream(is))))
        .toStream
        .flatMap(unzipEntries)
  }
}
