package io.soujiro32167

import io.soujiro32167.models.*
import zio.stream.{ZStream, *}
import zio.*

import java.io.{ByteArrayOutputStream, IOException}
import java.util.zip.{ZipInputStream, ZipOutputStream, ZipEntry as JZipEntry}

object ZipCompress {

  sealed private[this] trait Command

  private[this] case class StartEntry(name: String, zip: ZipOutputStream) extends Command

  private[this] case class AddDataToZipEntry(data: Chunk[Byte], zip: ZipOutputStream) extends Command

  private[this] case class EndEntry(name: String, zip: ZipOutputStream) extends Command

  private[this] case class EndFile(zip: ZipOutputStream) extends Command

  extension [R, E, A] (self: ZStream[R, E, A])
    def |++|[R1 <: R, E1, A1 >: A](that: => ZStream[R1, E1, A1])(implicit trace: Trace): ZStream[R1, E | E1, A1] =
      self concat that

    def flatMapE[R1 <: R, E1, B](f: A => ZStream[R1, E1, B])(implicit trace: Trace): ZStream[R1, E | E1, B] =
      self.flatMap(f)

    def widen[E1]: ZStream[R, E | E1, A] = self.asInstanceOf[ZStream[R, E | E1, A]]



  def zip[R0, R, E, E0](chunkSize: Int = ZStream.DefaultChunkSize): ZStream[R, E, ZipEntry[R0, E0]] => ZStream[R0 & R, E | E0 | Throwable, Byte] = { sources =>
    val jpipeline = for {
      baos <- ZIO.fromAutoCloseable(ZIO.succeed(new ByteArrayOutputStream(chunkSize)))
      zis <- ZIO.fromAutoCloseable(ZIO.attempt(new ZipOutputStream(baos)))
    } yield (baos, zis)


    ZStream.scoped(jpipeline).flatMap { case (baos, zipStream) =>

      def nextZipChunk: Task[Chunk[Byte]] = ZIO.attemptBlocking {
        val zippedData = baos.toByteArray
        baos.reset()
        Chunk.fromArray(zippedData)
      }

      def commandToBytes: Command => Task[Chunk[Byte]] = {
        case StartEntry(filePath, zip) =>
          ZIO.attemptBlocking(zip.putNextEntry(new JZipEntry(filePath))) *> nextZipChunk
        case AddDataToZipEntry(data, zip) =>
          ZIO.attemptBlocking(zip.write(data.toArray)) *> nextZipChunk
        case EndEntry(_, zip) => ZIO.attemptBlocking(zip.closeEntry()) *> nextZipChunk
        case EndFile(zip) => ZIO.attemptBlocking(zip.finish()) *> nextZipChunk
      }

      val s = sources.flatMapE { case (fileName, content) =>
        ZStream.succeed(StartEntry(fileName, zipStream)) ++
          content.mapChunks(chunk => Chunk(AddDataToZipEntry(chunk, zipStream))) ++
          ZStream.succeed(EndEntry(fileName, zipStream))
      } ++ ZStream.succeed(EndFile(zipStream))

      s.mapConcatChunkZIO(commandToBytes)
    }
  }

  def unzip[R](chunkSize: Int = ZStream.DefaultChunkSize):
      ZStream[R, Throwable, Byte] => ZStream[R, Throwable, ZipEntry[Any, IOException]] = { (stream: ZStream[R, Throwable, Byte]) =>

      def entry(zis: ZipInputStream) =
        ZIO.attemptBlocking(Option(zis.getNextEntry)).map(_.map { ze =>
          ze.getName -> ZStream.fromInputStream(zis, chunkSize)
        })

      def unzipEntries(zis: ZipInputStream) =
        ZStream.unfoldZIO(zis) { zis0 =>
          entry(zis0).map(_.map(_ -> zis0))
        }

      ZStream.scoped(
        stream
        .toInputStream
        .flatMap(is => ZIO.fromAutoCloseable(ZIO.succeed(new ZipInputStream(is))))
      )
        .flatMap(unzipEntries)
  }
}
