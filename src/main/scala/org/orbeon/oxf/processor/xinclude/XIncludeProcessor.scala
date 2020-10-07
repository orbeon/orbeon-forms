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
package org.orbeon.oxf.processor.xinclude

import scala.jdk.CollectionConverters._
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor._
import org.orbeon.oxf.processor.transformer.TransformerURIResolver
import org.orbeon.oxf.processor.transformer.xslt.XSLTTransformer
import org.orbeon.oxf.properties.PropertyStore
import org.orbeon.oxf.xml.XMLParsing.ParserConfiguration
import org.orbeon.oxf.xml._
import ProcessorImpl._
import URIProcessorOutputImpl.URIReferences

/**
 * XInclude processor.
 *
 * This processor reads a document on its "config" input that may contain XInclude directives. It
 * produces on its output a resulting document with the XInclude directives processed.
 *
 * TODO: Merge caching with URL generator, possibly XSLT transformer. See also XFormsToXHTML processor.
 */
class XIncludeProcessor extends ProcessorImpl {

  self =>

  val AttributesInput = "attributes"

  addInputInfo(new ProcessorInputOutputInfo(AttributesInput, XSLTTransformer.XSLT_PREFERENCES_CONFIG_NAMESPACE_URI))
  addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG))
  addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA))

  override def createOutput(name: String) =
    addOutput(name, new URIProcessorOutputImpl(self, name, INPUT_CONFIG) {
      def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {
        // Read attributes input only if connected (just in case, for backward compatibility, although it shouldn't happen)
        val configurationAttributes =
          if (getConnectedInputs.get(AttributesInput) ne null) {
            readCacheInputAsObject(pipelineContext, getInputByName(AttributesInput), new CacheableInputReader[Map[String, Boolean]] {
              def read(pipelineContext: PipelineContext, input: ProcessorInput) = {
                val preferencesDocument = readInputAsOrbeonDom(pipelineContext, input)
                val propertyStore = PropertyStore.parse(preferencesDocument)
                val propertySet = propertyStore.getGlobalPropertySet

                propertySet.getBooleanProperties.asScala map { case (k, v) => k -> v.booleanValue } toMap
              }
            })
          } else
            Map.empty[String, Boolean]

        // URL resolver is initialized with a parser configuration which can be configured to support external entities or not.
        val parserConfiguration = new ParserConfiguration(false, false, ! (configurationAttributes.get("external-entities") contains false))
        val uriResolver = new TransformerURIResolver(self, pipelineContext, INPUT_CONFIG, parserConfiguration)

        /**
         * The code below reads the input in a SAX store, before replaying the SAX store to the
         * XIncludeContentHandler.
         *
         * This may seem inefficient, but it is necessary (unfortunately) so the URI resolver which will be
         * called by the XInclude content handler has the right parents in the stack. When we do
         * readInputAsSAX() here, this might run a processor PR which might be outside of the current
         * pipeline PI that executed the XInclude processor. When PR run, PI might not be in the processor
         * stack anymore, because it is possible for PR to be outside of PI. When PR calls a SAX method of
         * the XInclude handler, there is no executeChildren() that runs, so when the XInclude handler
         * method is called the parent stack might not include PI, and so reading an input of PI will fail.
         *
         * The general rule is that when you receive SAX events, you might not be in the right context.
         * While the readInput...() method is running you can't rely on having the right context, so you
         * shouldn't call other readInput...() methods or do anything that relies on the context.
         *
         * We don't have this problem with Saxon, because Saxon will first read the input stylesheet and
         * then processes it. So when the processing happens, the readInput...() methods that reads the
         * stylesheet has returned.
         */

        // Try to cache URI references
        // NOTE: Always be careful not to cache refs to TransformerURIResolver. We seem to be fine here.
        var wasRead = false
        readCacheInputAsObject(pipelineContext, getInputByName(INPUT_CONFIG), new CacheableInputReader[URIReferences] {
          def read(context: PipelineContext, input: ProcessorInput) = {
            val uriReferences = new URIReferences
            val saxStore = new SAXStore
            readInputAsSAX(pipelineContext, INPUT_CONFIG, saxStore)
            saxStore.replay(new XIncludeReceiver(pipelineContext, xmlReceiver, uriReferences, uriResolver))
            wasRead = true
            uriReferences
          }
        })

        // Read if not already read
        if (! wasRead) {
          val saxStore = new SAXStore
          readInputAsSAX(pipelineContext, INPUT_CONFIG, saxStore)
          saxStore.replay(new XIncludeReceiver(pipelineContext, xmlReceiver, null, uriResolver))
        }
      }
    })
}