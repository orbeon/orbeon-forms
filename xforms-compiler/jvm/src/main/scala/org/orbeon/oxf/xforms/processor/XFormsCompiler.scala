package org.orbeon.oxf.xforms.processor

import org.orbeon.io.CharsetNames
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{BinaryTextSupport, ProcessorImpl, ProcessorOutput}
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiver}
import org.xml.sax.helpers.AttributesImpl
import org.orbeon.oxf.xml.XMLReceiverSupport._

import java.util.zip.{ZipEntry, ZipOutputStream}


class XFormsCompiler extends ProcessorImpl {

  override def createOutput(outputName: String): ProcessorOutput =
    addOutput(
      outputName,
      new ProcessorOutputImpl(XFormsCompiler.this, outputName) {
        def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

          implicit val rcv = xmlReceiver

          val params       = readCacheInputAsDOM4J(pipelineContext, "instance")
          val formDocument = readCacheInputAsDOM4J(pipelineContext, "data")

          val appName     = params.getRootElement.element("app").getText
          val formName    = params.getRootElement.element("form").getText
          val formVersion = params.getRootElement.element("form-version").getText

          val (template, staticState) = PartAnalysisBuilder.createFromDocument(formDocument)
          val jsonString = XFormsStaticStateSerializer.serialize(template, staticState)

          val useZipFormat =
            CoreCrossPlatformSupport.externalContext.getRequest.getFirstParamAsString("format").contains("zip")

          if (useZipFormat) {
            val chos = new ContentHandlerOutputStream(xmlReceiver, true)
            val zos  = new ZipOutputStream(chos)

            chos.setContentType("application/zip")

            val entry = new ZipEntry(s"/$appName/$formName/$formVersion/form/form.json")
            zos.putNextEntry(entry)
            zos.write(jsonString.getBytes(CharsetNames.Utf8))
            zos.close()
          } else {
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
        }
      }
    )
}