/**
 * Copyright (C) 2020 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.xforms.embedding

import org.scalajs.dom.experimental.{Headers, URL}

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array
import scala.scalajs.js.|


// One question here was whether we can use the standard `Request` and `Response` interfaces of the
// `Fetch` API. But these are not very easy to work with, and not all methods are supported by all
// browsers. So for now we use our own objects.
trait SubmissionRequest extends js.Object {
  val method  : String
  val url     : URL                    // makes it easier to parse the components
  val headers : Headers                // standard `Headers` object not supported in Safari for iOS for Fetch
  val body    : js.UndefOr[Uint8Array] // or `ReadableStream` from Fetch, but that's still experimental; or `Iterable`; or plain `Array`
}

trait SubmissionResponse extends js.Object {
  val statusCode : Int
  val headers    : Headers
  val body       : js.UndefOr[Uint8Array | js.Array[Byte]] // we can extend supported formats later
}

// Trait to be implemented by the embedder to support offline submissions
trait SubmissionProvider extends js.Object {
  def submit     (req: SubmissionRequest): SubmissionResponse
  def submitAsync(req: SubmissionRequest): js.Promise[SubmissionResponse]
}