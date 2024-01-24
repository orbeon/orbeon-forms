package org.orbeon.oxf.xforms.processor

import org.orbeon.datatypes.BasicLocationData
import org.orbeon.dom.Document
import org.orbeon.oxf.common.{OXFException, OrbeonLocationException}
import org.orbeon.oxf.http.BasicCredentials
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.transformer.TransformerURIResolver
import org.orbeon.oxf.processor.{ProcessorImpl, URIProcessorOutputImpl}
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.xforms.Loggers
import org.orbeon.oxf.xml.{ParserConfiguration, TransformerUtils, XMLReaderToReceiver}
import org.orbeon.saxon.Configuration
import org.orbeon.saxon.om.DocumentInfo
import org.xml.sax.InputSource

import javax.xml.transform.sax.SAXSource
import scala.util.control.NonFatal


/**
 * URI resolver used during XForms initialization.
 *
 * This URI resolver is able to use a username and password for HTTP and HTTPS, and works in conjunction with
 * URIProcessorOutputImpl.
 */
class XFormsURIResolver(
  processor          : ProcessorImpl, private var
  processorOutput    : URIProcessorOutputImpl,
  pipelineContext    : PipelineContext,
  prohibitedInput    : String,
  parserConfiguration: ParserConfiguration
) extends TransformerURIResolver(processor, pipelineContext, prohibitedInput, parserConfiguration) {

  // Use global definition for headers to forward
  override def resolve(href: String, base: String): SAXSource =
    resolve(href, base, null)

  def resolve(href: String, base: String, credentials: BasicCredentials): SAXSource =
    if (ProcessorImpl.getProcessorInputSchemeInputName(href) != null) {
      // Use parent resolver if accessing a processor input
      super.resolve(href, base)
    } else {
      // This is a regular URL
      val url = URLFactory.createURL(base, href)
      val protocol = url.getProtocol
      val isHttpProtocol = protocol == "http" || protocol == "https"
      if (isHttpProtocol) {
        // Override the behavior to read into the state
        val urlString = url.toExternalForm
        val state = getProcessor.getState(getPipelineContext).asInstanceOf[URIProcessorOutputImpl.URIReferencesState]
        // First, put in state if necessary
        processorOutput.readURLToStateIfNeeded(getPipelineContext, url, state, credentials)
        // Then try to read from state
        if (state.isDocumentSet(urlString, credentials)) {
          // not sure why this would not be the case
          // This means the document requested is already available. We use the cached document.
          val xmlReader = new XMLReaderToReceiver {
            def parse(systemId: String): Unit =
              state.getDocument(urlString, credentials).replay(createXMLReceiver)
          }
          if (Loggers.isDebugEnabled("resolver"))
            Loggers.logger.logger.debug("resolving resource through initialization resolver: `{}` ", urlString)
          new SAXSource(xmlReader, new InputSource(urlString))
        } else
          throw new OXFException(s"Cannot find document in state for URI: `$urlString`")
      }
      else {
        // Use parent resolver for other protocols
        super.resolve(href, base)
      }
    }

  def readAsOrbeonDom(urlString: String, credentials: BasicCredentials): Document =
    try {
      // XInclude handled by source if needed
      TransformerUtils.readOrbeonDom(resolve(urlString, null, credentials), false)
    } catch {
      case NonFatal(t) =>
        throw OrbeonLocationException.wrapException(t, BasicLocationData(urlString, -1, -1))
    }

  def readAsTinyTree(configuration: Configuration, urlString: String, credentials: BasicCredentials): DocumentInfo =
    try {
      // XInclude handled by source if needed
      TransformerUtils.readTinyTree(configuration, resolve(urlString, null, credentials), false)
    } catch {
      case NonFatal(t) =>
        throw OrbeonLocationException.wrapException(t, BasicLocationData(urlString, -1, -1))
    }
}