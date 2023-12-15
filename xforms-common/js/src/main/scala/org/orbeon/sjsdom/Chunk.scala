package org.orbeon.sjsdom

import scala.scalajs.js

/** See [[https://streams.spec.whatwg.org/#chunk ¶2 Model]] but mostly the examples in the whatwg streams spec */
@js.native
trait Chunk[+T] extends js.Object {

  /** The value of the chunk. */
  def value: T = js.native

  def done: Boolean = js.native
}