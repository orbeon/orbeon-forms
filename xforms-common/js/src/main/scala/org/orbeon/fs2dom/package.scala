/*
 * Copyright 2022 Arman Bilge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.orbeon

import cats.effect.kernel.{Async, Resource}
import fs2.Stream
import org.orbeon.sjsdom.ReadableStream

import scala.scalajs.js.typedarray.Uint8Array


package object fs2dom {

//  def readBlob[F[_]](blob: F[Blob])(implicit F: Async[F]): Stream[F, Byte] =
//    readReadableStream(blob.flatMap(b => F.delay(b.stream())))

  def readReadableStream[F[_]: Async](
      readableStream: F[ReadableStream[Uint8Array]],
      cancelAfterUse: Boolean = true
  ): Stream[F, Byte] = StreamConverters.readReadableStream(readableStream, cancelAfterUse)

  def toReadableStream[F[_]: Async]: fs2.Pipe[F, Byte, ReadableStream[Uint8Array]] =
    StreamConverters.toReadableStream

  def toReadableStreamResource[F[_]: Async](
      stream: Stream[F, Byte]
  ): Resource[F, ReadableStream[Uint8Array]] =
    stream.through(toReadableStream).compile.resource.lastOrError

//  def events[F[_]: Async, E <: DomEvent](target: EventTarget, `type`: String): Stream[F, E] =
//    EventTargetHelpers.listen(target, `type`)

}