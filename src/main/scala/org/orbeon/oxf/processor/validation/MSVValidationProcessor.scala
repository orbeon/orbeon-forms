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
package org.orbeon.oxf.processor.validation

import org.orbeon.datatypes.LocationData
import org.orbeon.dom.Document
import org.orbeon.msv.iso_relax.verifier.*
import org.orbeon.msv.verifier.jarv.Const
import org.orbeon.msv.verifier.jarv.TheFactoryImpl
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.*
import org.orbeon.oxf.processor.ProcessorImpl.*
import org.orbeon.oxf.processor.generator.DOMGenerator
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.xml.*
import org.orbeon.oxf.xml.dom.{IOSupport, XmlLocationData}
import org.xml.sax.*
import org.xml.sax.helpers.AttributesImpl

import java.io.StringReader
import javax.xml.parsers.SAXParserFactory


class MSVValidationProcessor(val schemaId: String) extends ProcessorImpl {

  import MSVValidationProcessor.*

  def this() = this(null)

  addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA))
  // NOTE: Don't set a schema here, because that will trigger the insertion of a validation processor in the
  // pipeline engine recursively, causing a StackOverflowError.
  addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG))
  addInputInfo(new ProcessorInputOutputInfo(InputSchema))
  addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA))

  override def createOutput(name: String): ProcessorOutput = {
    val output = new CacheableTransformerOutputImpl(this, name) {
      override def readImpl(context: PipelineContext, xmlReceiver: XMLReceiver): Unit =
        try {
          // Read config input to determine if we should decorate or not
          // Would be nice to validate it, but we can't simply use the schema on the input as usual (recursion)
          val configDoc      = readCacheInputAsOrbeonDom(context, INPUT_CONFIG)
          val decorateOutput = Option(XPathUtils.selectStringValueNormalize(configDoc, "/config/decorate")).flatMap(_.toBooleanOption).getOrElse(false)

          val schema = readCacheInputAsObject[Schema](
            context,
            getInputByName(InputSchema),
            (context: PipelineContext, input: ProcessorInput) => try {
              val startTime =
                if (Logger.isDebugEnabled) {
                  Logger.debug(s"Reading Schema: $schemaId")
                  System.currentTimeMillis()
                } else 0L

              val schemaDoc      = readInputAsOrbeonDom(context, input)
              val locator        = schemaDoc.getRootElement.getData.asInstanceOf[LocationData]
              val schemaSystemId = if (locator != null && locator.file != null) locator.file else null

              // Be sure to set our own XML parser factory
              val verifierFactory = new TheFactoryImpl(factory)
              verifierFactory.setEntityResolver((publicId: String, systemId: String) => {
                val url = URLFactory.createURL(schemaSystemId, systemId)
                val i   = new InputSource(url.openStream())
                i.setSystemId(url.toString)
                i
              })
              verifierFactory.setFeature(Const.PANIC_MODE_FEATURE, v = false)
              val is = new InputSource(new StringReader(IOSupport.domToStringJava(schemaDoc)))
              is.setSystemId(schemaSystemId)

              // Just a precaution, as the factory is not thread-safe. Does this make sense?
              val compiledSchema = MSVValidationProcessor.synchronized {
                verifierFactory.compileSchema(is)
              }

              if (Logger.isDebugEnabled)
                Logger.debug(s"$schemaId : Schema compiled in ${System.currentTimeMillis() - startTime}")

              compiledSchema
            } catch {
              case vce: VerifierConfigurationException =>
                throw new OXFException(vce.getCauseException)
              case e: Exception =>
                throw new OXFException(e)
            }
          )

          val verifier = schema.newVerifier()
          verifier.setErrorHandler(new ErrorHandler {

            private def generateErrorElement(ve: SchemaValidationException): Unit =
              if (decorateOutput && ve != null) {
                val systemId = Option(ve.firstLocationData).map(_.file).orNull
                val a        = new AttributesImpl
                a.addAttribute("", MessageAttribute, MessageAttribute, "CDATA", ve.message)
                a.addAttribute("", SystemIdAttribute, SystemIdAttribute, "CDATA", if (systemId == null) "" else systemId)
                a.addAttribute("", LineAttribute, LineAttribute, "CDATA", Integer.toString(ve.firstLocationData.line))
                a.addAttribute("", ColumnAttribute, ColumnAttribute, "CDATA", Integer.toString(ve.firstLocationData.col))

                val qName = s"$OrbeonErrorPrefix:$ErrorElement"
                xmlReceiver.startElement(OrbeonErrorNs, ErrorElement, qName, a)
                xmlReceiver.endElement(OrbeonErrorNs, ErrorElement, qName)
              } else {
                throw ve
              }

            override def error(exception: SAXParseException): Unit =
              generateErrorElement(new SchemaValidationException(s"Error ${exception.getMessage}(schema: $schemaId)", XmlLocationData(exception)))

            override def fatalError(exception: SAXParseException): Unit =
              generateErrorElement(new SchemaValidationException(s"Fatal Error ${exception.getMessage}(schema: $schemaId)", XmlLocationData(exception)))

            override def warning(exception: SAXParseException): Unit =
              generateErrorElement(new SchemaValidationException(s"Warning ${exception.getMessage}(schema: $schemaId)", XmlLocationData(exception)))
          })

          // NOTE: VerifierHandler only supports ContentHandler, not LexicalHandler. Comments are never validated.
          val verifierHandler = verifier.getVerifierHandler
          val xmlReceivers    = new TeeXMLReceiver(new ExceptionWrapperXMLReceiver(verifierHandler, "validating document"), xmlReceiver)

          val startTime =
            if (Logger.isDebugEnabled)
              System.currentTimeMillis()
            else 0L

          readInputAsSAX(context, INPUT_DATA, xmlReceivers)

          if (Logger.isDebugEnabled)
            Logger.debug(s"$schemaId validation completed in ${System.currentTimeMillis() - startTime}")

        } catch {
          case e: OXFException => throw e
          case e: Exception    => throw new OXFException(e)
        }
    }
    addOutput(name, output)
    output
  }
}

object MSVValidationProcessor {

  private val Logger = LoggerFactory.createLogger(classOf[MSVValidationProcessor])

  final val OrbeonErrorNs: String     = "http://orbeon.org/oxf/xml/validation"
  final val OrbeonErrorPrefix: String = "v"
  final val ErrorElement: String      = "error"
  final val MessageAttribute: String  = "message"
  final val SystemIdAttribute: String = "system-id"
  final val LineAttribute: String     = "line"
  final val ColumnAttribute: String   = "column"
  final val InputSchema: String       = "schema"

  @annotation.static val NO_DECORATION_CONFIG: DOMGenerator = {
    val configDoc = Document("config")
    configDoc.getRootElement.addElement("decorate").setText("false")
    new DOMGenerator(configDoc, "no decorate cfg", DOMGenerator.ZeroValidity, DOMGenerator.DefaultContext)
  }

  @annotation.static val DECORATION_CONFIG: DOMGenerator = {
    val configDoc = Document("config")
    configDoc.getRootElement.addElement("decorate").setText("true")
    new DOMGenerator(configDoc, "decorate cfg", DOMGenerator.ZeroValidity, DOMGenerator.DefaultContext)
  }

  val NoDecorationConfig: DOMGenerator = NO_DECORATION_CONFIG
  val DecorationConfig: DOMGenerator   = DECORATION_CONFIG

  // 02/06/2004 d : If we don't do anything VM would just convert unchecked exceptions thrown
  //                from here into ExceptionInInitializerError without setting the cause.
  //                This of course makes diagnosing reports from the field a major pain.
  private val factory: SAXParserFactory =
    try
      XMLParsing.createSAXParserFactory(ParserConfiguration.XIncludeOnly)
    catch {
      case e: Error            => throw new ExceptionInInitializerError(e)
      case e: RuntimeException => throw new ExceptionInInitializerError(e)
    }
}
