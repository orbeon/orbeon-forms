package org.orbeon.sjsdom

import scala.scalajs.js
import scala.scalajs.js.annotation._

/** See [[https://streams.spec.whatwg.org/#reader-class ¶3.4. Class ReadableStreamReader]] of whatwg streams spec.
  *
  * The ReadableStreamReader class represents a readable stream reader designed to be vended [sic] by a ReadableStream
  * instance.
  *
  * The ReadableStreamReader constructor is generally not meant to be used directly; instead, a stream’s getReader()
  * method should be used. This allows different classes of readable streams to vend different classes of readers
  * without the consumer needing to know which goes with which.
  *
  * @tparam T
  *   Type of the Chunks returned by the Stream
  */
@js.native
@JSGlobal
class ReadableStreamReader[+T](stream: ReadableStream[T]) extends js.Object {

  /** See [[https://streams.spec.whatwg.org/#reader-closed §3.4.4.1. get closed]] of whatwg Streams spec.
    *
    * The closed getter returns a promise that will be fulfilled when the stream becomes closed or the reader’s lock is
    * released, or rejected if the stream ever errors.
    */
  def closed: js.Promise[ReadableStreamReader[T]] = js.native

  /** See [[https://streams.spec.whatwg.org/#reader-cancel §3.4.4.2. cancel(reason)]] of whatwg Streams spec.
    *
    * If the reader is active, the cancel method behaves the same as that for the associated stream. When done, it
    * automatically releases the lock.
    */
  def cancel(reason: Any): js.Promise[Unit] = js.native
  def cancel(): js.Promise[Unit] = js.native

  /** See [[https://streams.spec.whatwg.org/#reader-read 3.4.4.3. read()]] of whatwg Stream spec.
    *
    * The read method will return a promise that allows access to the next chunk from the stream’s internal queue, if
    * available. If the chunk does become available, the promise will be fulfilled with an object of the form { value:
    * theChunk, done: false }. If the stream becomes closed, the promise will be fulfilled with an object of the form {
    * value: undefined, done: true }. If the stream becomes errored, the promise will be rejected with the relevant
    * error. If reading a chunk causes the queue to become empty, more data will be pulled from the underlying source.
    */
  def read(): js.Promise[Chunk[T]] = js.native

  /** The releaseLock method releases the reader’s lock on the corresponding stream. After the lock is released, the
    * reader is no longer active. If the associated stream is errored when the lock is released, the reader will appear
    * errored in the same way from now on; otherwise, the reader will appear closed. A reader’s lock cannot be released
    * while it still has a pending read request, i.e., if a promise returned by the reader’s read() method has not yet
    * been settled. Attempting to do so will throw a TypeError and leave the reader locked to the stream.
    *
    * throws scala.scalajs.js.TypeError
    */
  def releaseLock(): Unit = js.native
}