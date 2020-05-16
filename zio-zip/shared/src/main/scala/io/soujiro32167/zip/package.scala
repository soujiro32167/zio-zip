package io.soujiro32167

import zio.{Chunk, ZManaged}
import zio.stream.{Stream, ZStream}

package object zip {
  type ZPipe[R0, R <: R0, E0, E >: E0, A0, A] = ZStream[R0, E0, A0] => ZStream[R, E, A]
  type Pipe[A, B] = ZPipe[Any, Any, Throwable, Throwable, A, B]
  type ZipEntry = (String, Stream[Throwable, Chunk[Byte]])
  type ->[A, B] = (A, B)

  implicit class StreamOps[-R, +E, +A](val stream: ZStream[R, E, A]) extends AnyVal {
    def intersperse[A2 >: A](sep: A2): ZStream[R, E, A2] = ???
  }

  implicit class ZManagedOps[-R, +E, +A](val zmanaged: ZManaged[R, E, A]) extends AnyVal {
    def toStream: ZStream[R, E, A] = ZStream.managed(zmanaged)
  }
}
