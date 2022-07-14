package io.soujiro32167

import zio.ZManaged
import zio.blocking.Blocking
import zio.stream.ZStream

object models {
  type ZPipe[R0, R <: R0, E0, E >: E0, A0, A] = ZStream[R0, E0, A0] => ZStream[R, E, A]
  type Pipe[A, B] = ZPipe[Any, Any, Throwable, Throwable, A, B]
  type ZipEntry = (String, ZStream[Blocking, Throwable, Byte])
  type ->[A, B] = (A, B)

  implicit class ZManagedOps[-R, +E, +A](val zmanaged: ZManaged[R, E, A]) extends AnyVal {
    def toStream: ZStream[R, E, A] = ZStream.managed(zmanaged)
  }
}
