package org.orbeon.oxf.xforms.processor

import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{BinaryTextSupport, ProcessorImpl, ProcessorOutput}
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiver}
import org.xml.sax.helpers.AttributesImpl
import org.orbeon.oxf.xml.XMLReceiverSupport._


class XFormsCompiler extends ProcessorImpl {

  override def createOutput(outputName: String): ProcessorOutput =
    addOutput(
      outputName,
      new ProcessorOutputImpl(XFormsCompiler.this, outputName) {
        def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

          implicit val rcv = xmlReceiver

          val input = readCacheInputAsDOM4J(pipelineContext, "data")

          val (template, staticState) = PartAnalysisBuilder.createFromDocument(input)
          val jsonString = XFormsStaticStateSerializer.serialize(template, staticState)

          val attributes = new AttributesImpl
          attributes.addAttribute(XMLConstants.XSI_URI, "type", "xsi:type", "CDATA", XMLConstants.XS_STRING_QNAME.qualifiedName)
          attributes.addAttribute("", Headers.ContentTypeLower, Headers.ContentTypeLower, "CDATA", ContentTypes.JsonContentType)

          withDocument {

            xmlReceiver.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI)
            xmlReceiver.startPrefixMapping(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI)

            withElement(
              BinaryTextSupport.TextDocumentElementName,
              atts = attributes
            ) {
              val chw = new ContentHandlerWriter(xmlReceiver, false)
              chw.write(jsonString)
            }
          }
        }
      }
    )
}