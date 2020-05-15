package io.soujiro32167.zip

import java.io.ByteArrayOutputStream
import java.util.zip.{ZipInputStream, ZipOutputStream, ZipEntry => JZipEntry}

import zio.stream.{Stream, ZStreamChunk}
import zio.{Chunk, Managed, Task}

object ZipCompress {

  sealed private[this] trait Command

  private[this] case object OpenZipFile extends Command

  private[this] case class StartEntry(name: String, zip: ZipOutputStream) extends Command

  private[this] case object Noop extends Command

  private[this] case class AddDataToZipEntry(data: Chunk[Byte], zip: ZipOutputStream) extends Command

  private[this] case class EndEntry(name: String, zip: ZipOutputStream) extends Command

  private[this] case class CloseZipFile(zip: ZipOutputStream) extends Command

  def zip(chunkSize: Int = ZStreamChunk.DefaultChunkSize): Pipe[ZipEntry, Chunk[Byte]] = { sources =>
    val buf       = new ByteArrayOutputStream(chunkSize)
    val zipStream = new ZipOutputStream(buf)

    def nextZipChunk(): Task[Chunk[Byte]] = Task {
      val zippedData = buf.toByteArray
      buf.reset()
      Chunk.fromArray(zippedData)
    }

    (Stream(OpenZipFile) ++ sources.flatMap { zipSource =>
      Stream(StartEntry(zipSource._1, zipStream)) ++
        zipSource._2.map(AddDataToZipEntry(_, zipStream)).intersperse(Noop) ++
        Stream(EndEntry(zipSource._1, zipStream))
    } ++ Stream(CloseZipFile(zipStream))).mapM {
      case OpenZipFile | Noop =>
        Task.effectTotal(Chunk.empty)
      case StartEntry(filePath, zip) =>
        Task(zip.putNextEntry(new JZipEntry(filePath))) *> nextZipChunk()
      case AddDataToZipEntry(data, zip) =>
        Task(zip.write(data.toArray)) *> nextZipChunk()
      case EndEntry(_, zip) =>
        Task(zip.closeEntry()) *> nextZipChunk()
      case CloseZipFile(zip) =>
        Task(zip.close()) *> nextZipChunk()
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

    val managed: Managed[Throwable, ZipInputStream] =
      stream.toInputStream.flatMap(is => Managed.makeEffect(new ZipInputStream(is))(_.close))
    Stream.managed(managed).flatMap(unzipEntries)
  }
}