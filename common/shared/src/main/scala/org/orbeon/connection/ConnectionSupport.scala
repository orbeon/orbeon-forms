package org.orbeon.connection

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import java.io.{ByteArrayInputStream, InputStream}
import scala.concurrent.Future


object ConnectionSupport {

  // Convert the `fs2.Stream` in the `ConnectResult` to an `InputStream`. Of course, We'd like to stream all the way
  // ideally, but this is a first step. We cannot use `fs2.io.toInputStream` because it requires running two threads,
  // which doesn't work in JavaScript. So we go through an in-memory `Array` for now. Note that sending data also
  // works with `Array`s. Also, note that we use `Future` as that's currently what's submitted to this manager.
  def fs2StreamToInputStreamInMemory(s: fs2.Stream[IO, Byte]): Future[InputStream] =
    s.compile.to(Array).map(new ByteArrayInputStream(_)).unsafeToFuture()
}
