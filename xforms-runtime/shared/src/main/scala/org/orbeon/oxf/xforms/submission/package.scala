package org.orbeon.oxf.xforms

import cats.effect.IO

import java.io.InputStream

package object submission {
  type ConnectResult      = ConnectResultT[InputStream]
  type AsyncConnectResult = ConnectResultT[fs2.Stream[IO, Byte]]
}
