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
package org.orbeon.oxf.processor.pdf

import com.lowagie.text.pdf._
import com.lowagie.text.{Image, Rectangle}
import org.log4s
import org.orbeon.datatypes.LocationData
import org.orbeon.dom.Element
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.io.CharsetNames
import org.orbeon.io.IOUtils._
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.pipeline.api.{FunctionLibrary, PipelineContext}
import org.orbeon.oxf.processor.generator.URLGeneratorBase
import org.orbeon.oxf.processor.pdf.PDFTemplateProcessor._
import org.orbeon.oxf.processor.serializer.legacy.HttpBinarySerializer
import org.orbeon.oxf.processor.serializer.{BinaryTextXMLReceiver, HttpSerializerBase}
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInput, ProcessorInputOutputInfo}
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.saxon.om.{Item, NodeInfo, ValueRepresentation}
import org.orbeon.saxon.value.{FloatValue, Int64Value}
import org.orbeon.xml.NamespaceMapping

import java.io.{ByteArrayOutputStream, OutputStream}
import java.net.URI
import java.net.URLDecoder.{decode => decodeURL}
import java.util.{List => JList}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal


/**
 * The PDF Template processor reads a PDF template and performs textual annotations on it.
 */
class PDFTemplateProcessor extends HttpBinarySerializer with Logging {// TODO: HttpBinarySerializer is deprecated

  addInputInfo(new ProcessorInputOutputInfo("model", PDFTemplateModelNamespaceURI))
  addInputInfo(new ProcessorInputOutputInfo("data"))

  protected def getDefaultContentType = ContentTypes.PdfContentType

  protected def readInput(
    pipelineContext : PipelineContext,
    input           : ProcessorInput,
    config          : HttpSerializerBase.Config,
    outputStream    : OutputStream
  ): Unit =  {

    val configDocument = readCacheInputAsOrbeonDom(pipelineContext, "model")// TODO: should we use "config"?
    val configRoot = configDocument.getRootElement
    val templateRoot = configRoot.element("template")

    val instanceDocument = readInputAsOrbeonDom(pipelineContext, input)
    val instanceDocumentInfo = new DocumentWrapper(instanceDocument, null, XPath.GlobalConfiguration)

    // Create PDF reader
    val templateReader = {
      val templateHref = templateRoot.attributeValue("href")
      Option(ProcessorImpl.getProcessorInputSchemeInputName(templateHref)) match {
        case Some(inputName) =>
          val os = new ByteArrayOutputStream
          readInputAsSAX(pipelineContext, inputName, new BinaryTextXMLReceiver(os))
          new PdfReader(os.toByteArray)
        case None =>
          new PdfReader(URLFactory.createURL(templateHref))
      }
    }

    useAndClose(new PdfStamper(templateReader, outputStream)) { stamper =>

      stamper.setFormFlattening(true)

      // Initial context
      val initialContext =
        ElementContext(
          pipelineContext = pipelineContext,
          logger          = new IndentedLogger(PDFTemplateProcessor.Logger),
          contentByte     = null,
          acroFields      = stamper.getAcroFields,
          pageWidth       = 0,
          pageHeight      = 0,
          pageNumber      = -1,
          variables       = Map(),
          element         = configRoot,
          contextSeq      = Seq(instanceDocumentInfo),
          contextPosition = 1,
          offsetX         = 0,
          offsetY         = 0,
          fontFamily      = "Courier",
          fontSize        = 14,
          fontPitch       = 15.9f
        )

      // Add substitution fonts for Acrobat fields
      for (element <- configRoot.elements("substitution-font")) {
        val fontFamilyOrPath = decodeURL(element.attributeValue("font-family"), CharsetNames.Utf8)
        val embed            = element.attributeValue("embed") == "true"

        try initialContext.acroFields.addSubstitutionFont(createFont(fontFamilyOrPath, embed))
        catch {
          case NonFatal(t) =>
            warn("could not load font", Seq(
              "font-family" -> fontFamilyOrPath,
              "embed"       -> embed.toString,
              "throwable"   -> OrbeonFormatter.format(t)))(initialContext.logger)
        }
      }

      // Iterate through template pages
      for (pageNumber <- 1 to templateReader.getNumberOfPages) {

        val pageSize = templateReader.getPageSize(pageNumber)

        val variables = Map[String, ValueRepresentation](
          "page-count"  -> new Int64Value(templateReader.getNumberOfPages),
          "page-number" -> new Int64Value(pageNumber),
          "page-width"  -> new FloatValue(pageSize.getWidth),
          "page-height" -> new FloatValue(pageSize.getHeight)
        )

        // Context for the page
        val pageContext = initialContext.copy(
          contentByte = stamper.getOverContent(pageNumber),
          pageWidth   = pageSize.getWidth,
          pageHeight  = pageSize.getHeight,
          pageNumber  = pageNumber,
          variables   = variables
        )

        handleElements(pageContext, configRoot.jElements.asScala)
      }

      // no document.close() ?
    }
  }

  // How to handle known elements
  val Handlers: Map[String, ElementContext => Unit] = Map(
    "group"   -> handleGroup,
    "repeat"  -> handleRepeat,
    "field"   -> handleField,
    "barcode" -> handleBarcode,
    "image"   -> handleImage
  )

  def handleElements(context: ElementContext, statements: Iterable[Element]): Unit =
    // Iterate through statements
    for (element <- statements) {

      // Context for this element
      val newContext = context.copy(element = element)

      // Check whether this statement applies to the current page
      def hasPageNumber = newContext.att("page") ne null
      def pageNumberMatches = Option(newContext.att("page")) exists (_.toInt == newContext.pageNumber)

      if (! hasPageNumber || pageNumberMatches)
        Handlers.get(element.getName) foreach (_.apply(newContext))
    }

  def handleGroup(context: ElementContext): Unit = {

    val xpathContext =
      Option(context.att("ref")) match {
        case Some(ref) =>
          Option(context.evaluateSingle(ref).asInstanceOf[Item]) map (ref => (Seq(ref), 1))
        case None =>
          Some(context.contextSeq, context.contextPosition)
      }

    // Handle group only if we have a context
    xpathContext foreach { case (contextSeq, contextPosition) =>
      val newGroupContext =
        context.copy(
          contextSeq      = contextSeq,
          contextPosition = contextPosition,
          offsetX         = context.resolveFloat("offset-x",     context.offsetX, context.offsetX),
          offsetY         = context.resolveFloat("offset-y",     context.offsetY, context.offsetY),
          fontPitch       = context.resolveFloat("font-pitch",   0f, context.fontPitch),
          fontFamily      = context.resolveString("font-family", context.fontFamily),
          fontSize        = context.resolveFloat("font-size",    0f, context.fontSize))

      handleElements(newGroupContext, newGroupContext.element.jElements.asScala)
    }
  }

  def handleRepeat(context: ElementContext): Unit =  {
    val ref = Option(context.att("ref")) getOrElse context.att("nodeset")
    val iterations = context.evaluate(ref)

    for (iterationIndex <- 1 to iterations.size) {

      val offsetIncrementX = context.resolveFloat("offset-x", 0f, 0f)
      val offsetIncrementY = context.resolveFloat("offset-y", 0f, 0f)

      val iterationContext = context.copy(
        contextSeq      = iterations,
        contextPosition = iterationIndex,
        offsetX         = context.offsetX + (iterationIndex - 1) * offsetIncrementX,
        offsetY         = context.offsetY + (iterationIndex - 1) * offsetIncrementY
      )

      handleElements(iterationContext, context.element.jElements.asScala)
    }
  }

  private val FieldTypesWithValues = Set(
    AcroFields.FIELD_TYPE_RADIOBUTTON,
    AcroFields.FIELD_TYPE_LIST,
    AcroFields.FIELD_TYPE_COMBO,
    AcroFields.FIELD_TYPE_CHECKBOX // NOTE: Checkboxes are not linked: each checkbox is its own control.
  )

  def handleField(context: ElementContext): Unit =
    Option(context.att("acro-field-name")) match {
      case Some(fieldNameExpr) =>
        // Acrobat field
        val fieldName = context.evaluateAsString(fieldNameExpr)

        if (findFieldPage(context.acroFields, fieldName) contains context.pageNumber) {
          Option(context.acroFields.getFieldItem(fieldName)) foreach { _ =>
            // Field exists
            val exportValue = Option(context.att("export-value"))
            val valueExpr   = exportValue orElse Option(context.att("value")) getOrElse context.att("ref")
            val value       = context.evaluateAsString(valueExpr)

            // NOTE: We can obtain the list of allowed values with:
            //
            //   context.acroFields.getAppearanceStates(fieldName)
            //
            // This also returns (sometimes? always?) an "Off" value.

            val fieldType = context.acroFields.getFieldType(fieldName)

            // export-value -> set field types with values
            // value        -> set field types without values
            if (exportValue.isDefined == FieldTypesWithValues(fieldType))
              context.acroFields.setField(fieldName, value)
          }
        }
      case None =>
        // Overlay text
        val leftPosition   = context.resolveAVT("left", "left-position")
        val topPosition    = context.resolveAVT("top", "top-position")
        val size           = context.resolveAVT("size")
        val value          = Option(context.att("value")) getOrElse context.att("ref")
        val fontAttributes = context.getFontAttributes

        val baseFont = createFont(fontAttributes.fontFamily, fontAttributes.embed)

        // Write value
        context.contentByte.beginText()

        context.contentByte.setFontAndSize(baseFont, fontAttributes.fontSize)
        val xPosition = leftPosition.toFloat + context.offsetX
        val yPosition = context.pageHeight - (topPosition.toFloat + context.offsetY)

        // Get value from instance
        Option(context.evaluateAsString(value)) foreach { text =>
          // Iterate over characters and print them
          val len = math.min(text.length, Option(size) map (_.toInt) getOrElse Integer.MAX_VALUE)
          for (j <- 0 until len)
            context.contentByte.showTextAligned(
              PdfContentByte.ALIGN_CENTER,
              text.substring(j, j + 1),
              xPosition + j.toFloat * fontAttributes.fontPitch,
              yPosition,
              0
            )
        }

        context.contentByte.endText()
    }

  def handleBarcode(context: ElementContext): Unit =  {

    val value          = Option(context.att("value")) getOrElse context.att("ref")
    val barcodeType    = Option(context.att("type")) getOrElse "CODE39"
    val height         = Option(context.att("height")) map (_.toFloat) getOrElse 10.0f
    val xPosition      = context.resolveAVT("left").toFloat + context.offsetX
    val yPosition      = context.pageHeight - context.resolveAVT("top").toFloat + context.offsetY
    val text           = context.evaluateAsString(value)

    val fontAttributes = context.getFontAttributes
    val baseFont = createFont(fontAttributes.fontFamily, fontAttributes.embed)

    val barcode = createBarCode(barcodeType)

    barcode.setCode(text)
    barcode.setBarHeight(height)
    barcode.setFont(baseFont)
    barcode.setSize(fontAttributes.fontSize)

    val barcodeImage = barcode.createImageWithBarcode(context.contentByte, null, null)
    barcodeImage.setAbsolutePosition(xPosition, yPosition)
    context.contentByte.addImage(barcodeImage)
  }

  def handleImage(context: ElementContext): Unit =  {

    lazy val image = {
      val hrefAttribute = context.att("href")
      Option(ProcessorImpl.getProcessorInputSchemeInputName(hrefAttribute)) match {
        case Some(inputName) =>
          val os = new ByteArrayOutputStream
          readInputAsSAX(context.pipelineContext, inputName, new BinaryTextXMLReceiver(os))
          Image.getInstance(os.toByteArray)
        case None =>

          val url = URI.create(hrefAttribute)

          val externalContext = NetUtils.getExternalContext

          val cxr =
            Connection.connectNow(
              method          = GET,
              url             = url,
              credentials     = None,
              content         = None,
              headers         = Connection.buildConnectionHeadersCapitalizedIfNeeded(
                url                      = url,
                hasCredentials           = false,
                customHeaders            = URLGeneratorBase.extractHeaders(context.element),
                headersToForward         = Connection.headersToForwardFromProperty,
                cookiesToForward         = Connection.cookiesToForwardFromProperty,
                getHeader                = Connection.getHeaderFromRequest(externalContext.getRequest))(
                logger                   = context.logger,
                externalContext          = externalContext,
                coreCrossPlatformSupport = CoreCrossPlatformSupport
              ),
              loadState       = true,
              saveState       = true,
              logBody         = false)(
              logger          = context.logger,
              externalContext = externalContext
            )

          ConnectionResult.withSuccessConnection(cxr, closeOnSuccess = true) { is =>
            // NOTE: iText's Image.getInstance() closes the local URL's InputStream
            val tempUrl = FileItemSupport.inputStreamToAnyURI(is, ExpirationScope.Request)._1
            Image.getInstance(URLFactory.createURL(tempUrl))
          }
      }
    }

    Option(context.att("acro-field-name")) match {
      case Some(fieldNameStr) =>
        // Acrobat field
        val fieldName = context.evaluateAsString(fieldNameStr)

        if (findFieldPage(context.acroFields, fieldName) contains context.pageNumber) {
          Option(context.acroFields.getFieldPositions(fieldName)) foreach { positions =>

            val rectangle = new Rectangle(positions(1), positions(2), positions(3), positions(4))
            image.scaleToFit(rectangle.getWidth, rectangle.getHeight)

            val yPosition = positions(2) + rectangle.getHeight - image.getScaledHeight

            image.setAbsolutePosition(
              positions(1) + (rectangle.getWidth - image.getScaledWidth) / 2,
              yPosition
            )

            context.contentByte.addImage(image)
            context.acroFields.removeField(fieldName)
          }
        }
      case None =>
        // By position
        val xPosition = context.resolveAVT("left").toFloat + context.offsetX
        val yPosition = context.pageHeight - (context.resolveAVT("top").toFloat + context.offsetY)

        image.setAbsolutePosition(xPosition, yPosition)
        Option(context.resolveAVT("scale-percent")) foreach
          (scalePercent => image.scalePercent(scalePercent.toFloat))

        Option(context.resolveAVT("dpi")) foreach { dpi =>
          val dpiInt = dpi.toInt
          image.setDpi(dpiInt, dpiInt)
        }

        context.contentByte.addImage(image)
    }
  }
}

object PDFTemplateProcessor {

  val Logger: log4s.Logger = LoggerFactory.createLogger(classOf[PDFTemplateProcessor])
  val PDFTemplateModelNamespaceURI = "http://www.orbeon.com/oxf/pdf-template/model"

  def createBarCode(barcodeType: String): Barcode = barcodeType match {
    case "CODE39"  => new Barcode39
    case "CODE128" => new Barcode128
    case "EAN"     => new BarcodeEAN
    case _         => new Barcode39
  }

  case class FontAttributes(fontPitch: Float, fontFamily: String, fontSize: Float, embed: Boolean)

  case class ElementContext(
    pipelineContext : PipelineContext,
    logger          : IndentedLogger,
    contentByte     : PdfContentByte,
    acroFields      : AcroFields,
    pageWidth       : Float,
    pageHeight      : Float,
    pageNumber      : Int,
    variables       : Map[String, ValueRepresentation],
    element         : Element,
    contextSeq      : scala.collection.Seq[Item],
    contextPosition : Int,
    offsetX         : Float,
    offsetY         : Float,
    fontFamily      : String,
    fontSize        : Float,
    fontPitch       : Float
  ) {

    private def contextItem = contextSeq(contextPosition - 1)
    private def jVariables = variables.asJava
    private def functionLibrary = FunctionLibrary.instance

    def att(name: String): String = element.attributeValue(name)

    def resolveFloat(name: String, offset: Float, default: Float): Float =
      Option(resolveAVT(name)) map
        (offset + _.toFloat) getOrElse default

    def resolveString(name: String, current: String): String =
      Option(resolveAVT(name)) map
        identity getOrElse current

    def evaluateSingle(xpath: String): NodeInfo =
      XPathCache.evaluateSingle(
        contextSeq.asJava,
        contextPosition,
        xpath,
        NamespaceMapping(element.getNamespaceContextNoDefault),
        jVariables,
        functionLibrary,
        null,
        null,
        element.getData.asInstanceOf[LocationData],
        null
      ).asInstanceOf[NodeInfo]

    def evaluate(xpath: String): scala.collection.Seq[Item] =
      XPathCache.evaluate(
        contextSeq.asJava,
        contextPosition,
        xpath,
        NamespaceMapping(element.getNamespaceContextNoDefault),
        jVariables,
        functionLibrary,
        null,
        null,
        element.getData.asInstanceOf[LocationData],
        null
      ).asInstanceOf[JList[Item]].asScala

    def evaluateAsString(xpath: String): String =
      XPathCache.evaluateAsString(
        contextSeq.asJava,
        contextPosition,
        xpath,
        NamespaceMapping(element.getNamespaceContextNoDefault),
        jVariables,
        functionLibrary,
        null,
        null,
        element.getData.asInstanceOf[LocationData],
        null
      )

    def resolveAVT(attributeName: String, otherAttributeName: String = null): String =
      Option(att(attributeName)) orElse Option(Option(otherAttributeName) map att orNull) map
        (
          XPathCache.evaluateAsAvt(
            contextItem,
            _,
            NamespaceMapping(element.getNamespaceContextNoDefault),
            jVariables,
            functionLibrary,
            null,
            null,
            element.getData.asInstanceOf[LocationData],
            null
          )
        ) orNull

    def getFontAttributes: FontAttributes = {
      val newFontPitch  = Option(resolveAVT("font-pitch", "spacing")) map (_.toFloat) getOrElse fontPitch
      val newFontFamily = Option(resolveAVT("font-family"))                           getOrElse fontFamily
      val newFontSize   = Option(resolveAVT("font-size"))             map (_.toFloat) getOrElse fontSize

      FontAttributes(newFontPitch, newFontFamily, newFontSize, att("embed") == "true")
    }
  }

  // Create a font
  def createFont(fontFamilyOrPath: String, embed: Boolean): BaseFont =
    BaseFont.createFont(fontFamilyOrPath, findFontEncoding(fontFamilyOrPath), embed)

  // PDF built-in fonts
  val BuiltinFonts = Set(
    "Courier",
    "Courier-Bold",
    "Courier-Oblique",
    "Courier-BoldOblique",
    "Helvetica",
    "Helvetica-Bold",
    "Helvetica-Oblique",
    "Helvetica-BoldOblique",
    "Symbol",
    "Times-Roman",
    "Times-Bold",
    "Times-Italic",
    "Times-BoldItalic",
    "ZapfDingbats"
  )

  // Find an encoding suitable for the given font family
  def findFontEncoding(fontFamilyName: String): String = {

    // The reason we do this is that specifying Identity-H or Identity-V with a Type1 font always fails as iText
    // tries to find an actual character encoding based on the value passed. For other font types, Identity-H and
    // Identity-V are handled.
    def isType1Font(name: String) =
      BuiltinFonts(name) || (name.splitTo(""".""").lastOption map (_.toLowerCase) exists Set("afm", "pfm"))

    if (isType1Font(fontFamilyName))
      BaseFont.CP1252
    else
      BaseFont.IDENTITY_H
  }

  def findFieldPage(acroFields: AcroFields, fieldName: String): Option[Int] =
    for {
      item <- Option(acroFields.getFieldItem(fieldName))
      if item.size > 0
      page = item.getPage(0).intValue
    } yield
      page
}