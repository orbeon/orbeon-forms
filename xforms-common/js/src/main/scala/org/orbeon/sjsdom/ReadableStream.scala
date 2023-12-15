package org.orbeon.sjsdom

import scala.scalajs.js

/** defined at [[https://streams.spec.whatwg.org/#readable-stream ¶2.1. Readable Streams]] of whatwg Streams spec.
  *
  * @tparam T
  *   Type of the Chunks returned by the Stream. Can't make it coveriant, due to T
  */
@js.native
trait ReadableStream[+T] extends js.Object {

  /** The locked getter returns whether or not the readable stream is locked to a reader.
    *
    * throws scala.scalajs.js.TypeError if the stream is not readable
    */
  def locked: Boolean = js.native

  /** The cancel method cancels the stream, signaling a loss of interest in the stream by a consumer. The supplied
    * reason argument will be given to the underlying source, which may or may not use it.
    *
    * @param reason
    *   the reason
    * @return
    *   a Promise
    */
  def cancel(reason: js.UndefOr[Any] = js.native): js.Promise[Unit] = js.native

  /** See [[https://streams.spec.whatwg.org/#rs-get-reader ¶3.2.4.3. getReader()]] of whatwg streams spec. Also see the
    * example usage there.
    *
    * The getReader method creates a readable stream reader and locks the stream to the new reader. While the stream is
    * locked, no other reader can be acquired until this one is released. The returned reader provides the ability to
    * directly read individual chunks from the stream via the reader’s read method. This functionality is especially
    * useful for creating abstractions that desire the ability to consume a stream in its entirety. By getting a reader
    * for the stream, you can ensure nobodyA else can interleave reads with yours or cancel the stream, which would
    * interfere with your abstraction.
    *
    * Note that if a stream becomes closed or errored, any reader it is locked to is automatically released.
    *
    * throws scala.scalajs.js.TypeError if not a readable stream
    *
    * @return
    *   a new ReadableStreamReader
    */
  def getReader(): ReadableStreamReader[T] = js.native

  /** see [[https://streams.spec.whatwg.org/#rs-pipe-through §3.2.4.4. pipeThrough({ writable, readable }, options)]]
    *
    * The pipeThrough method provides a convenient, chainable way of piping this readable stream through a transform
    * stream (or any other { writable, readable } pair). It simply pipes the stream into the writable side of the
    * supplied pair, and returns the readable side for further use . Piping a stream will generally lock it for the
    * duration of the pipe, preventing any other consumer fromA acquiring a reader.
    *
    * This method is intentionally generic; it does not require that its this value be a ReadableStream object. It also
    * does not require that its writable argument be a WritableStream instance, or that its readable argument be a
    * ReadableStream instance.
    *
    * //todo: determine the type of options
    */
  def pipeThrough[U](pair: Any, // TODO js.Tuple2[WriteableStream[T], ReadableStream[U]]
      options: Any = js.native): ReadableStream[U] = js.native

  /** See
    * [[https://streams.spec.whatwg.org/#rs-pipe-to ¶3.2.4.5. pipeTo(dest, { preventClose, preventAbort, preventCancel } = {})]]
    * of whatwg Streams spec.
    *
    * The pipeTo method pipes this readable stream to a given writable stream. The way in which the piping process
    * behaves under various error conditions can be customized with a number of passed options. It returns a promise
    * that fulfills when the piping process completes successfully, or rejects if any errors were encountered.
    *
    * Piping a stream will generally lock it for the duration of the pipe, preventing any other consumer from acquiring
    * a reader. This method is intentionally generic; it does not require that its this value be a ReadableStream
    * object.
    *
    * //todo: determine the type of options
    */
//  def pipeTo(dest: WriteableStream[T], options: Any = js.native): js.Promise[Unit] = js.native

  /** See [[https://streams.spec.whatwg.org/#rs-tee ¶3.2.4.6. tee()]] of whatwg streams spec.
    *
    * The tee method tees this readable stream, returning a two-element array containing the two resulting branches as
    * new ReadableStream instances.
    *
    * Teeing a stream will lock it, preventing any other consumer from acquiring a reader. To cancel the stream, cancel
    * both of the resulting branches; a composite cancellation reason will then be propagated to the stream’s underlying
    * source.
    *
    * Note that the chunks seen in each branch will be the same object. If the chunks are not immutable, this could
    * allow interference between the two branches. (Let us know if you think we should add an option to tee that creates
    * structured clones of the chunks for each branch.)
    */
  def tee(): js.Array[_ <: ReadableStream[T]] = js.native // TODO js.Tuple2[ReadableStream[T], ReadableStream[T]]
}

object ReadableStream {

  def apply[T](
      underlyingSource: js.UndefOr[ReadableStreamUnderlyingSource[T]] = js.undefined,
      queuingStrategy: js.UndefOr[QueuingStrategy[T]] = js.undefined
  ): ReadableStream[T] = {
    js.Dynamic
      // ORBEON: Use `WebStreamsPolyfill.ReadableStream` instead of `ReadableStream` as Safari doesn't support
      // `ReadableStreamUnderlyingSource` yet. So when we need to create a `ReadableStream` from a
      // `ReadableStreamUnderlyingSource`, we instantiate it using the polyfill. Currently, our use case is to
      // create a `ReadableStream` and pass it to our `SubmissionProvider`. Similarly, if a `SubmissionProvider`
      // returns a `ReadableStream`, it will need to  instantiate it using the polyfill under Safari.
      // Safari error: `TypeError: ReadableByteStreamController` is not implemented. References:
      //
      // - https://streams.spec.whatwg.org/#rbs-controller-class
      // - https://caniuse.com/?search=ReadableByteStreamController
      // - https://github.com/MattiasBuelens/web-streams-polyfill

//      .newInstance(js.Dynamic.global.ReadableStream)(
      .newInstance(js.Dynamic.global.WebStreamsPolyfill.ReadableStream)(
          underlyingSource.asInstanceOf[js.Any],
          queuingStrategy.asInstanceOf[js.Any]
      )
      .asInstanceOf[ReadableStream[T]]
  }
}