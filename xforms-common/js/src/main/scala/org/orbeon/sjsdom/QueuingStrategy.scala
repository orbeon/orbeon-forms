package org.orbeon.sjsdom

import scala.scalajs.js

/** See [[https://streams.spec.whatwg.org/#qs-api ¶7.1. The queuing strategy API]]
  *
  * @tparam T
  *   Type of the Chunks returned by the Stream
  */
trait QueuingStrategy[T] extends js.Object {

  /** A non-negative number indicating the high water mark of the stream using this queuing strategy. */
  var highWaterMark: Int

  /** (non-byte streams only)
    *
    * The result is used to determine backpressure, manifesting via the appropriate desiredSize property. For readable
    * streams, it also governs when the underlying source's [[ReadableStreamUnderlyingSource.pull]] method is called.
    *
    * A function that computes and returns the finite non-negative size of the given chunk value.
    */
  var size: js.Function1[T, Int]
}