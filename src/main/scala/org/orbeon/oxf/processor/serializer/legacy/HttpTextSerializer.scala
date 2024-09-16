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

import org.orbeon.dom.QName
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.*
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl
import org.orbeon.oxf.processor.serializer.{CachedSerializer, HttpSerializerBase}
import org.orbeon.oxf.util.ContentHandlerWriter
import org.orbeon.oxf.xml.XMLConstants.XSI_TYPE_QNAME
import org.orbeon.oxf.xml.XMLReceiverSupport.*
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiver}

import java.io.*


/**
 * Legacy HTTP text serializer. This is deprecated by HttpSerializer.
 */
object HttpTextSerializer {
  private val TextDocumentElementName = "document"
  private val ContentTypeLowerQName   = QName(Headers.ContentTypeLower)
}

abstract class HttpTextSerializer extends HttpSerializerBase {

  self =>

  import HttpTextSerializer._

  override final protected def readInput(
    pipelineContext: PipelineContext,
    response       : ExternalContext.Response,
    input          : ProcessorInput,
    config         : HttpSerializerBase.Config
  ): Unit = {
    response.setContentType(config.encodingWithCharset(getDefaultContentType, CachedSerializer.DefaultEncoding))
    readInput(
      pipelineContext,
      input,
      config,
      new OutputStreamWriter(response.getOutputStream, config.encodingOrDefault(CachedSerializer.DefaultEncoding))
    )
  }

  protected def isSerializeXML11: Boolean = getPropertySet.getBoolean("serialize-xml-11", default = false)

  /**
   * This must be overridden by subclasses.
   */
  protected def readInput(
    context: PipelineContext,
    input  : ProcessorInput,
    config : HttpSerializerBase.Config,
    writer : Writer
  ): Unit

  /**
   * This method is used when the legacy serializer is used in the new converter mode. In this
   * case, the converter exposes a "data" output, and the processor's start() method is not
   * called.
   */
  override def createOutput(name: String): ProcessorOutput = {
    require(name == ProcessorImpl.OUTPUT_DATA, s"Invalid output name: `$name`")
    addOutput(
      name,
      new CacheableTransformerOutputImpl(self, name) {
        override def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

          implicit val receiver: XMLReceiver = xmlReceiver

          // Create OutputStream that converts to Base64
          val writer = new ContentHandlerWriter(xmlReceiver, false)

          // Read configuration input
          val config = readConfig(pipelineContext)

          withDocument {
            withElement(
              TextDocumentElementName,
              atts    =
                List(
                  XSI_TYPE_QNAME        -> XMLConstants.XS_STRING_QNAME.qualifiedName,
                  ContentTypeLowerQName -> config.encodingWithCharset(getDefaultContentType, CachedSerializer.DefaultEncoding)
                ),
              extraNs =
                List(
                  XMLConstants.XSI_PREFIX -> XMLConstants.XSI_URI, // probably not needed
                  XMLConstants.XSD_PREFIX -> XMLConstants.XS_STRING_QNAME.namespace.uri
                )
            ) {
              // Write content
              readInput(pipelineContext, getInputByName(ProcessorImpl.INPUT_DATA), config, writer)
            }
          }
        }
      }
    )
  }
}