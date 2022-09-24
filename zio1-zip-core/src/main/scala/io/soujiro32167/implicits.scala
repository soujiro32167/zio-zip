package io.soujiro32167

import io.soujiro32167.models.ZipEntry
import zio.blocking.Blocking
import zio.stream.ZStream

object implicits {
  implicit class ZStreamOps[-R, +E, +I](val self: ZStream[R, E, I]) extends AnyVal {
    def pipe[R1 <: R, E1 >: E, O](f: ZStream[R, E, I] => ZStream[R1, E1, O]): ZStream[R1, E1, O] =
      f(self)
  }

  implicit class ZStreamCompressOps[-R0, -R, E0, E >: E0](val self: ZStream[R, E, ZipEntry[R0, E0]]) extends AnyVal {
    def compress(errorMapper: Throwable => E): ZStream[R & R0 & Blocking, E, Byte] =
      self.pipe(ZipCompress.zip(errorMapper = errorMapper))
  }

  implicit class ZStreamCompressThrowableOps[-R0, -R](val self: ZStream[R, Throwable, ZipEntry[R0, Throwable]])
      extends AnyVal {
    def compress: ZStream[R & R0 & Blocking, Throwable, Byte] =
      self.pipe(ZipCompress.zip0)
  }
}
