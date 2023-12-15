package org.orbeon.sjsdom

import scala.scalajs.js

/** [[https://streams.spec.whatwg.org/#enumdef-readablestreamtype ReadableStreamType enum]] */
@js.native
sealed trait ReadableStreamType extends js.Any

object ReadableStreamType {
  val bytes: ReadableStreamType = "bytes".asInstanceOf[ReadableStreamType]
}