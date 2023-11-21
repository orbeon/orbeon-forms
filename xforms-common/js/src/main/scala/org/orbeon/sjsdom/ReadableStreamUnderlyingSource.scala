package org.orbeon.sjsdom


import scala.scalajs.js

/** See [[https://streams.spec.whatwg.org/#underlying-source-api ¶4.2.3. The underlying source API]] of whatwg streams
  * spec.
  *
  * @tparam T
  *   Type of the Chunks returned by the Stream
  */
trait ReadableStreamUnderlyingSource[T] extends js.Object {

  /** A function that is called immediately during creation of the ReadableStream.
    *
    * If this setup process is asynchronous, it can return a promise to signal success or failure; a rejected promise
    * will error the stream. Any thrown exceptions will be re-thrown by the [[ReadableStream]] constructor.
    */
  var start: js.UndefOr[js.Function1[ReadableStreamController[T], js.UndefOr[js.Promise[Unit]]]] = js.undefined

  /** A function that is called whenever the stream’s internal queue of chunks becomes not full, i.e. whenever the
    * queue’s desired size becomes positive. Generally, it will be called repeatedly until the queue reaches its high
    * water mark (i.e. until the desired size becomes non-positive).
    *
    * This function will not be called until [[start]] successfully completes. Additionally, it will only be called
    * repeatedly if it enqueues at least one chunk or fulfills a BYOB request; a no-op [[pull]] implementation will not
    * be continually called.
    *
    * If the function returns a promise, then it will not be called again until that promise fulfills. (If the promise
    * rejects, the stream will become errored.) This is mainly used in the case of pull sources, where the promise
    * returned represents the process of acquiring a new chunk. Throwing an exception is treated the same as returning a
    * rejected promise.
    */
  var pull: js.UndefOr[js.Function1[ReadableStreamController[T], js.UndefOr[js.Promise[Unit]]]] = js.undefined

  /** A function that is called whenever the consumer cancels the stream, via [[ReadableStream.cancel]] or
    * [[ReadableStreamReader.cancel():scala\.scalajs\.js\.Promise[Unit]*]]. It takes as its argument the same value as
    * was passed to those methods by the consumer. If the shutdown process is asynchronous, it can return a promise to
    * signal success or failure; the result will be communicated via the return value of the [[cancel]] method that was
    * called. Additionally, a rejected promise will error the stream, instead of letting it close. Throwing an exception
    * is treated the same as returning a rejected promise.
    */
  var cancel: js.UndefOr[js.Function1[js.Any, js.UndefOr[js.Promise[Unit]]]] = js.undefined

  /** Can be set to "bytes" to signal that the constructed [[ReadableStream]] is a readable byte stream.
    *
    * Setting any value other than "bytes" or undefined will cause the ReadableStream() constructor to throw an
    * exception.
    */
  var `type`: js.UndefOr[ReadableStreamType] = js.undefined

  /** (byte streams only)
    *
    * Can be set to a positive integer to cause the implementation to automatically allocate buffers for the underlying
    * source code to write into.
    */
  var autoAllocateChunkSize: js.UndefOr[Int] = js.undefined
}