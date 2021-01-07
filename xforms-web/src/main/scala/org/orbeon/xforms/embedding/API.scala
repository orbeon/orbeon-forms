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
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.|


// Client-side embedding API for the full XForms processor
object API {

  def configure(
    submissionProvider : SubmissionProvider
  ): FormProcessor = ???
}

trait FormProcessor {

  def renderForm(
    container    : html.Element,
    compiledForm : CompiledForm,
    location     : js.UndefOr[String | URL] = js.undefined, // or query string?
    headers      : js.UndefOr[Headers]      = js.undefined,
  ): RuntimeForm
}

// Instances of `CompiledForm` cannot be created on the client. They are compiled on
// the server and then made available to the client.
trait CompiledForm

trait RuntimeForm {
  def uuid: String
  def close(): Unit
}
