/**
 *  Copyright (C) 2012 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.state

import org.orbeon.oxf.xml.SAXStore
import sbinary.Operations._
import XFormsOperations._
import XFormsProtocols._
import org.orbeon.oxf.util.Base64

// XML template with its serialization
case class AnnotatedTemplate(saxStore: SAXStore) {
  private lazy val asByteArray = toByteArray(saxStore)
  // Used to serialized into dynamic state
  def asByteSeq: Seq[Byte] = asByteArray.toSeq // this produces a WrappedArray and must not copy
  // Used to serialize into static state document
  def asBase64: String = Base64.encode(asByteArray, false)
}

object AnnotatedTemplate {

  // Restore based on bytes
  def apply(bytes: Seq[Byte]): AnnotatedTemplate =
    AnnotatedTemplate(fromByteSeq[SAXStore](bytes))

  // Restore based on a Base64-encoded string
  def apply(base64: String): AnnotatedTemplate =
    AnnotatedTemplate(fromByteArray[SAXStore](Base64.decode(base64)))
}