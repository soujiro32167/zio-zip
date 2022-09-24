package io.soujiro32167

import io.soujiro32167.models.*
import zio.blocking.{Blocking, effectBlocking}
import zio.stream.{Stream, ZStream}
import zio.{Chunk, Managed, RIO, Task, ZIO}

import java.io.{ByteArrayOutputStream, IOException}
import java.util.zip.{ZipInputStream, ZipOutputStream, ZipEntry as JZipEntry}

object ZipCompress {

  sealed private[this] trait Command

  private[this] case class StartEntry(name: String, zip: ZipOutputStream) extends Command

  private[this] case class AddDataToZipEntry(data: Chunk[Byte], zip: ZipOutputStream) extends Command

  private[this] case class EndEntry(name: String, zip: ZipOutputStream) extends Command

  private[this] case class EndFile(zip: ZipOutputStream) extends Command

  def zip0[R0, R]: ZPipe[R0, R & R0 & Blocking, Throwable, Throwable, ZipEntry[R0, Throwable], Byte] =
    zip(errorMapper = identity)

  def zip[R0, R, E0, E >: E0](chunkSize: Int = ZStream.DefaultChunkSize, errorMapper: Throwable => E): ZPipe[R0, R & R0 & Blocking, E0, E, ZipEntry[R0, E0], Byte] = {
    sources =>
      (for {
        baos <- Managed.fromAutoCloseable(Task(new ByteArrayOutputStream(chunkSize)))
        zis  <- Managed.fromAutoCloseable(Task(new ZipOutputStream(baos)))
      } yield (baos, zis)).toStream.mapError(errorMapper).flatMap { case (baos, zipStream) =>

        def nextZipChunk: RIO[Blocking, Chunk[Byte]] = effectBlocking {
          val zippedData = baos.toByteArray
          baos.reset()
          Chunk.fromArray(zippedData)
        }

        def commandToBytes: Command => RIO[Blocking, Chunk[Byte]] = {
          case StartEntry(filePath, zip) =>
            effectBlocking(zip.putNextEntry(new JZipEntry(filePath))) *> nextZipChunk
          case AddDataToZipEntry(data, zip) =>
            effectBlocking(zip.write(data.toArray)) *> nextZipChunk
          case EndEntry(_, zip) => effectBlocking(zip.closeEntry()) *> nextZipChunk
          case EndFile(zip) => effectBlocking(zip.finish()) *> nextZipChunk
        }

        (
          sources.flatMap { case (fileName, content) =>
              ZStream(StartEntry(fileName, zipStream)) ++
              content.mapChunks(chunk => Chunk(AddDataToZipEntry(chunk, zipStream))) ++
              ZStream(EndEntry(fileName, zipStream))
          } ++ ZStream(EndFile(zipStream))
        ).mapConcatChunkM { c =>
            commandToBytes(c).mapError(errorMapper)
          }
      }
  }

  def unzip[R](chunkSize: Int = ZStream.DefaultChunkSize): ZPipe[R, R & Blocking, Throwable, Throwable, Byte, ZipEntry0] = {
    stream =>

      def entry(zis: ZipInputStream): ZIO[Blocking, Throwable, Option[(String, ZStream[Blocking, IOException, Byte])]] =
        effectBlocking(Option(zis.getNextEntry)).map(_.map { ze =>
          ze.getName -> Stream.fromInputStream(zis, chunkSize)
        })

      def unzipEntries(zis: ZipInputStream): ZStream[Blocking, Throwable, ZipEntry[Blocking, Throwable]] =
        Stream.unfoldM(zis) { zis0 =>
          entry(zis0).map(_.map(_ -> zis0))
        }

      stream.toInputStream
        .flatMap(is => Managed.fromAutoCloseable(Task(new ZipInputStream(is))))
        .toStream
        .flatMap(unzipEntries)
  }
}
