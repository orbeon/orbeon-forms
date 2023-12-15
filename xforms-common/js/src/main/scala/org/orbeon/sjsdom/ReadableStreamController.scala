package org.orbeon.sjsdom


import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

/** [[https://streams.spec.whatwg.org/#rs-controller-class ¶3.3 Class ReadableStreamController]] of whatwg spec
  *
  * The ReadableStreamController constructor cannot be used directly; it only works on a ReadableStream that is in the
  * middle of being constructed.
  *
  * @param stream
  *   can be null
  * @tparam T
  *   Type of the Chunks to be enqueued to the Stream
  */
@js.native
@JSGlobal
class ReadableStreamController[-T] private[this] () extends js.Object {

  /** The desiredSize getter returns the desired size to fill the controlled stream’s internal queue. It can be
    * negative, if the queue is over-full. An underlying source should use this information to determine when and how to
    * apply backpressure.
    *
    * @return
    *   the size of the strem - no idea if this actually is an int
    */
  def desiredSize: Int = js.native

  /** The close method will close the controlled readable stream. Consumers will still be able to read any
    * previously-enqueued chunks from the stream, but once those are read, the stream will become closed throws
    * scala.scalajs.js.TypeError if this is not a readable controller
    */
  def close(): Unit = js.native

  /** The enqueue method will enqueue a given chunk in the controlled readable stream.
    *
    * @param chunk
    *   throws scala.scalajs.js.RangeError if size is too big
    * @return
    *   seems like its an undefOr[Int] of the size
    */
  def enqueue(chunk: T): Unit = js.native
  def enqueue(): Unit = js.native

  /** The error method will error the readable stream, making all future interactions with it fail with the given error
    * e.
    *
    * @param e
    *   : an error - can this be any type? throws scala.scalajs.js.TypeError
    */
  def error(e: Any): Unit = js.native
}