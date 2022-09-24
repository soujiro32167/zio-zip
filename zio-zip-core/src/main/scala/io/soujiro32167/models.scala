package io.soujiro32167

import zio.stream.ZStream

object models {
  type ZipEntry0        = ZipEntry[Any, Throwable]
  type ZipEntry[-R, +E] = (String, ZStream[R, E, Byte])
}
