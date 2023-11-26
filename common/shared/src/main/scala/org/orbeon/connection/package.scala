package org.orbeon

import cats.effect.IO

import java.io.InputStream


package object connection {

  type Content               = ContentT[InputStream]
  type AsyncContent          = ContentT[fs2.Stream[IO, Byte]]

  type StreamedContent       = StreamedContentT[InputStream]
  type AsyncStreamedContent  = StreamedContentT[fs2.Stream[IO, Byte]]

  type ConnectionResult      = ConnectionResultT[InputStream]
  type AsyncConnectionResult = ConnectionResultT[fs2.Stream[IO, Byte]]
}
