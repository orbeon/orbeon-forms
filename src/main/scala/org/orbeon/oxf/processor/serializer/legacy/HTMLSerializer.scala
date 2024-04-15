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

import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.serializer.{CachedSerializer, HttpSerializerBase}
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInput}
import org.orbeon.oxf.xml.{TransformerUtils, XMLReceiver}

import java.io.Writer
import javax.xml.transform.stream.StreamResult


private object HTMLSerializer {

  val DefaultContentType = "text/html"
  val DefaultMethod      = "html"

  private class StripNamespaceXMLReceiver(xmlReceiver: XMLReceiver, writer: Writer, serializeXML11: Boolean)
    extends SerializerXMLReceiver(xmlReceiver, writer, serializeXML11) {
    override def startPrefixMapping(s: String, s1: String): Unit = ()
    override def endPrefixMapping(s: String): Unit = ()
  }
}

class HTMLSerializer extends HttpTextSerializer {

  import HTMLSerializer._

  //    protected
  override def getDefaultContentType: String = DefaultContentType

  override protected def readInput(
    context: PipelineContext,
    input  : ProcessorInput,
    config : HttpSerializerBase.Config,
    writer : Writer
  ): Unit = {
    val identity = TransformerUtils.getIdentityTransformerHandler
    TransformerUtils.applyOutputProperties(
      identity.getTransformer,
      config.methodOr(DefaultMethod),
      config.versionOr(null),
      config.publicDoctypeOrNull,
      config.systemDoctypeOrNull,
      config.encodingOrDefault(CachedSerializer.DefaultEncoding),
      config.omitXMLDeclaration,
      config.standaloneOrNull,
      config.indent,
      config.indentAmount
    )
    identity.setResult(new StreamResult(writer))
    ProcessorImpl.readInputAsSAX(
      context,
      input,
      new StripNamespaceXMLReceiver(identity, writer, isSerializeXML11)
    )
  }
}