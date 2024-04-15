/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.processor.serializer.legacy

import org.orbeon.oxf.processor.serializer.BinaryTextXMLReceiver
import org.orbeon.oxf.xml.{NamespaceCleanupXMLReceiver, XMLReceiver}

import java.io.Writer


/**
 * A forwarding content handler that flushes the output when receiving a given processing instruction.
 *
 * Also clean invalid XML 1.0 namespace declarations if needed.
 */
class SerializerXMLReceiver(xmlReceiver: XMLReceiver, serializeXML11: Boolean)
  extends NamespaceCleanupXMLReceiver(xmlReceiver, serializeXML11) {

  private var writer: Option[Writer] = None

  def this(xmlReceiver: XMLReceiver, writer: Writer, serializeXML11: Boolean) {
    this(xmlReceiver, serializeXML11)
    this.writer = Option(writer)
  }

  override def processingInstruction(target: String, data: String): Unit =
    if (BinaryTextXMLReceiver.PITargets(target) && data == "flush")
      writer.foreach(_.flush())
    else
      super.processingInstruction(target, data)
}