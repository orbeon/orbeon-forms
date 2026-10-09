package org.orbeon.oxf.xforms.processor

import org.orbeon.dom
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{BinaryTextSupport, ProcessorImpl, ProcessorOutput}
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.*
import org.orbeon.oxf.xforms.*
import org.orbeon.oxf.xforms.analysis.PartAnalysisBuilder
import org.orbeon.oxf.xml.XMLReceiverSupport.*
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
          val (jsonString, _) = XFormsCompiler.compile(formDocument, XFormsCompiler.isClientPropertyName(_))
          XFormsCompiler.outputJson(jsonString)
        }
      }
    )
}

object XFormsCompiler {

  private val ServerOnlyXFormsPrefixes = Set(
    "assets",
    "resources",
    "whitespace",
    "cache",
    "store"
  )

  private val ServerOnlyXFormsProperties = Set(
    "oxf.xforms.xbl.library",
    "oxf.xforms.minimal-resources",
    "oxf.xforms.combine-resources",
    "oxf.xforms.replication",
    "oxf.xforms.gzip-state",
    "oxf.xforms.local-submission-forward",
    "oxf.xforms.local-submission-include",
    "oxf.xforms.local-instance-include",
    "oxf.xforms.optimize-get-all",
    "oxf.xforms.forward-submission-headers",
    "oxf.xforms.sanitize",
  )

  def isClientPropertyName(
    propertyName  : String,
    matchesAppForm: (String, String) => Boolean = (_, _) => true
  ): Boolean =
    ! PropertySet.isSensitivePropertyName(propertyName) && {
      propertyName.splitTo[List](".") match {
        case "oxf" :: "xforms" :: rest =>
          ! ServerOnlyXFormsProperties.contains(propertyName) &&
            (rest match {
              case head :: _ if ServerOnlyXFormsPrefixes.contains(head) => false
              case "xbl" :: "mapping" :: _                              => false
              case "xbl" :: _ if rest.length >= 6                       => matchesAppForm(rest(rest.length - 2), rest.last)
              case _                                                    => true
            })
        case _ =>
          false
      }
    }

  def compile(
    formDocument    : dom.Document,
    isClientProperty: String => Boolean
  )(implicit
    xmlReceiver     : XMLReceiver,
    indentedLogger  : IndentedLogger
  ): (String, XFormsStaticState) = {

    val (template, staticState) = PartAnalysisBuilder.createFromDocument(formDocument)
    val jsonString = XFormsStaticStateSerializer.serialize(template, staticState, isClientProperty)

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