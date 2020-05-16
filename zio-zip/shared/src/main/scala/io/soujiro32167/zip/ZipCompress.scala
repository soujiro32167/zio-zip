package io.soujiro32167.zip

import java.io.ByteArrayOutputStream
import java.util.zip.{ZipInputStream, ZipOutputStream, ZipEntry => JZipEntry}

import zio.stream.{Stream, ZStreamChunk}
import zio.{Chunk, Managed, Task}

object ZipCompress {

  sealed private[this] trait Command

  private[this] case class StartEntry(name: String, zip: ZipOutputStream) extends Command

  private[this] case class AddDataToZipEntry(data: Chunk[Byte], zip: ZipOutputStream) extends Command

  private[this] case class EndEntry(name: String, zip: ZipOutputStream) extends Command

  private[this] case class EndFile(zip: ZipOutputStream) extends Command


  def zip(chunkSize: Int = ZStreamChunk.DefaultChunkSize): Pipe[ZipEntry, Chunk[Byte]] = { sources =>
    (for {
      baos <- Managed.fromAutoCloseable(Task(new ByteArrayOutputStream(chunkSize)))
      zis <- Managed.fromAutoCloseable(Task(new ZipOutputStream(baos)))
    } yield baos -> zis).toStream.flatMap{ case (baos, zipStream) =>

      def nextZipChunk: Task[Chunk[Byte]] = Task {
        val zippedData = baos.toByteArray
        baos.reset()
        Chunk.fromArray(zippedData)
      }

      (sources.flatMap { case (fileName, content) =>
          Stream(StartEntry(fileName, zipStream)) ++
            content.map(AddDataToZipEntry(_, zipStream)) ++
            Stream(EndEntry(fileName, zipStream))
        } ++ Stream(EndFile(zipStream))).tap(c => Task(println(c)))
          .mapM {
        case StartEntry(filePath, zip) =>
          Task(zip.putNextEntry(new JZipEntry(filePath))) *> nextZipChunk
        case AddDataToZipEntry(data, zip) =>
          Task(zip.write(data.toArray)) *> nextZipChunk
        case EndEntry(_, zip) =>
          Task(zip.closeEntry()) *> nextZipChunk
        case EndFile(zip) => Task(zip.finish()) *> nextZipChunk
      }
    }
  }

  def unzip(chunkSize: Int = ZStreamChunk.DefaultChunkSize): Pipe[Byte, ZipEntry] = { stream =>

    def entry(zis: ZipInputStream): Task[Option[(String, Stream[Throwable, Chunk[Byte]])]] =
      Task(Option(zis.getNextEntry)).map(_.map { ze =>
        ze.getName -> Stream.fromInputStream(zis, chunkSize).chunks
      })

    def unzipEntries(zis: ZipInputStream): Stream[Throwable, String -> Stream[Throwable, Chunk[Byte]]] =
      Stream.unfoldM(zis) { zis0 =>
        entry(zis0).map(_.map(_ -> zis0))
      }

    stream.toInputStream.mapM(is => Task(new ZipInputStream(is))).toStream.flatMap(unzipEntries)
  }
}