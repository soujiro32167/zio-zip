package io.soujiro32167

import zio.ZManaged
import zio.blocking.Blocking
import zio.stream.ZStream

object models {
  type ZPipe[R, R1 <: R, E, E1 >: E, I, O] =
    ZStream[R, E, I] => ZStream[R1, E1, O]
  type Pipe[A, B] = ZPipe[Any, Any, Throwable, Throwable, A, B]
  type ZipEntry0 = ZipEntry[Blocking, Throwable]
  type ZipEntry[-R, +E]   = (String, ZStream[R, E, Byte])
  type ->[A, B]   = (A, B)

  implicit class ZManagedOps[-R, +E, +A](val zmanaged: ZManaged[R, E, A]) extends AnyVal {
    def toStream: ZStream[R, E, A] = ZStream.managed(zmanaged)
  }
}
