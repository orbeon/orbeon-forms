package org.orbeon.oxf.xforms.processor

import org.orbeon.dom
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{BinaryTextSupport, ProcessorImpl, ProcessorOutput}
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiver}
import org.xml.sax.helpers.AttributesImpl


class XFormsCompiler extends ProcessorImpl {

  override def createOutput(outputName: String): ProcessorOutput =
    addOutput(
      outputName,
      new ProcessorOutputImpl(XFormsCompiler.this, outputName) {
        def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

          implicit val rcv: XMLReceiver = xmlReceiver
          implicit val indentedLogger: IndentedLogger = Loggers.newIndentedLogger("compiler")

          val formDocument = readCacheInputAsOrbeonDom(pipelineContext, "data")
          val (jsonString, _) = XFormsCompiler.compile(formDocument)
          XFormsCompiler.outputJson(jsonString)
        }
      }
    )
}

object XFormsCompiler {

  def compile(
    formDocument  : dom.Document
  )(implicit
    xmlReceiver   : XMLReceiver,
    indentedLogger: IndentedLogger
  ): (String, XFormsStaticState) = {

    val (template, staticState) = PartAnalysisBuilder.createFromDocument(formDocument)
    val jsonString = XFormsStaticStateSerializer.serialize(template, staticState)

    (jsonString, staticState)
  }

  def outputJson(jsonString: String)(implicit xmlReceiver: XMLReceiver): Unit =
    withDocument {

      xmlReceiver.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI)
      xmlReceiver.startPrefixMapping(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI)

      val attributes = new AttributesImpl
      attributes.addAttribute(XMLConstants.XSI_URI, "type", "xsi:type", "CDATA", XMLConstants.XS_STRING_QNAME.qualifiedName)
      attributes.addAttribute("", Headers.ContentTypeLower, Headers.ContentTypeLower, "CDATA", ContentTypes.JsonContentType)

      withElement(
        BinaryTextSupport.TextDocumentElementName,
        atts = attributes
      ) {
        val chw = new ContentHandlerWriter(xmlReceiver, false)
        chw.write(jsonString)
      }
    }
}