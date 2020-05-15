package io.soujiro32167

import zio.Chunk
import zio.stream.{Stream, ZStream}

package object zip {
  type Pipe[A, B] = Stream[Throwable, A] => Stream[Throwable, B]
  type ZipEntry = (String, Stream[Throwable, Chunk[Byte]])
  type ->[A, B] = (A, B)

  implicit class StreamOps[-R, +E, +A](stream: ZStream[R, E, A]) {
    def intersperse[A2 >: A](sep: A2): ZStream[R, E, A2] = ???
  }
}
