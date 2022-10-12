/**
 * Copyright (C) 2011 Orbeon, Inc.
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

package org.orbeon.oxf.xforms.processor

import net.sf.ehcache.{Element => EhElement}
import org.orbeon.dom.QName
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils.StringOps
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml._
import org.orbeon.xforms.{Constants, XFormsCrossPlatformSupport}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

import scala.collection.mutable.{Buffer, LinkedHashSet}


// Aggregate CSS and JS resources under `<head>`.
class AssetsAggregator extends ProcessorImpl {

  self =>

  import AssetsAggregator._

  addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA))
  addOutputInfo(new ProcessorInputOutputInfo(ProcessorImpl.OUTPUT_DATA))

  override def createOutput(name: String) =
    addOutput(name, new ProcessorOutputImpl(self, name) {
      override def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver) =
        readInputAsSAX(pipelineContext, ProcessorImpl.INPUT_DATA,
          if (! XFormsGlobalProperties.isCombinedResources) xmlReceiver else new SimpleForwardingXMLReceiver(xmlReceiver) {

            sealed trait HeadElement {
              val name: String
              val attributes: Attributes
              def text: Option[String] = None
            }
            case class ReferenceElement(name: String, attributes: Attributes) extends HeadElement
            case class InlineElement   (name: String, attributes: Attributes) extends HeadElement {
              val content = new StringBuilder
              override def text = Some(content.toString)
            }

            // State
            var level = 0
            var inHead = false
            var filter = false

            var currentInlineElement: InlineElement = _

            // Resources gathered
            val baselineCSS     = LinkedHashSet[String]()
            val baselineJS      = LinkedHashSet[String]()
            val supplementalCSS = LinkedHashSet[String]()
            val supplementalJS  = LinkedHashSet[String]()

            val preservedCSS    = Buffer[HeadElement]()
            val preservedJS     = Buffer[HeadElement]()

            // Whether we are in separate deployment as in that case we don't combine paths to user resources
            val request  = XFormsCrossPlatformSupport.externalContext.getRequest
            val response = XFormsCrossPlatformSupport.externalContext.getResponse
            val isSeparateDeployment = URLRewriterUtils.isSeparateDeployment(request)

            // In this mode, resources are described in JSON within a <div>
            val isPortlet = request.getContainerType == "portlet"
            val namespaceOpt = isPortlet option response.getNamespacePrefix
            val isMinimal = XFormsGlobalProperties.isMinimalResources

            // Whether a path is a user resource in separate deployment
            def isSeparatePath(path: String) = isSeparateDeployment && ! URLRewriterUtils.isPlatformPath(path)

            override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {
              level += 1

              if (level == 2 && localname == "head") {
                inHead = true
              } else if (level == 3 && inHead) {

                implicit def attributesToSAXAttribute(attributes: Attributes) = new {
                  def getValue(qName: QName) = attributes.getValue(qName.namespace.uri, qName.localName)
                }

                lazy val href = attributes.getValue("href")
                lazy val src = attributes.getValue("src")
                lazy val resType = attributes.getValue("type")
                lazy val cssClasses = attributes.getValue("class")
                lazy val isNorewrite = attributes.getValue(XMLConstants.FORMATTING_URL_NOREWRITE_QNAME) == "true"
                // Ideally different media should be aggregated separately. For now we just preserve != "all".
                lazy val media = Option(attributes.getValue("media")) getOrElse "all"

                lazy val rel = Option(attributes.getValue("rel")) getOrElse "" toLowerCase

                // Gather resources that match
                localname match {
                  case "link" if (href ne null) && ((resType eq null) || resType == "text/css") && rel == "stylesheet" =>
                    if (isSeparatePath(href) || PathUtils.urlHasProtocol(href) || media != "all" || isNorewrite || cssClasses == "xforms-standalone-resource")
                      preservedCSS += ReferenceElement(localname, new AttributesImpl(attributes))
                    else
                      (if (cssClasses == "xforms-baseline") baselineCSS else supplementalCSS) += href
                    filter = true
                  case "script" if (src ne null) && ((resType eq null) || resType == "text/javascript") =>
                    if (isSeparatePath(src) || PathUtils.urlHasProtocol(src) || isNorewrite || cssClasses == "xforms-standalone-resource")
                      preservedJS += ReferenceElement(localname, new AttributesImpl(attributes))
                    else
                      (if (cssClasses == "xforms-baseline") baselineJS else supplementalJS) += src
                    filter = true
                  case "style" =>
                    currentInlineElement = InlineElement(localname, new AttributesImpl(attributes))
                    preservedCSS += currentInlineElement
                    filter = true
                  case "script" if src eq null =>
                    currentInlineElement = InlineElement(localname, new AttributesImpl(attributes))
                    preservedJS += currentInlineElement
                    filter = true
                  case _ =>
                }
              }

              if (! filter)
                super.startElement(uri, localname, qName, attributes)
            }

            override def endElement(uri: String, localname: String, qName: String): Unit = {

              lazy val xhtmlPrefix = XMLUtils.prefixFromQName(qName)

              implicit val receiver: XMLReceiver = xmlReceiver

              def outputPreservedElement(e: HeadElement): Unit =
                withElement(
                  localName = e.name,
                  prefix    = xhtmlPrefix,
                  uri       = XMLConstants.XHTML_NAMESPACE_URI,
                  atts      = e.attributes
                ) {
                  e.text foreach text
                }

              def outputCSS(): Unit = {

                def outputCSSElement(resource: String): Unit =
                  element("link", xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "href" -> resource :: LinkBaseAtts)

                aggregate(baselineCSS, outputCSSElement, namespaceOpt,  isCSS = true)
                aggregate(supplementalCSS -- baselineCSS, outputCSSElement, namespaceOpt, isCSS = true)
                preservedCSS foreach outputPreservedElement
              }

              def outputJS(hasInlineScript: Boolean): Unit = {

                val attsBase =
                  if (hasInlineScript)
                    ScriptBaseAtts
                else
                    "defer" -> "defer" :: ScriptBaseAtts

                def outputJSElement(resource: String): Unit =
                  element("script", xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "src" -> resource :: attsBase)

                aggregate(baselineJS, outputJSElement, namespaceOpt, isCSS = false)
                aggregate(supplementalJS -- baselineJS, outputJSElement, namespaceOpt, isCSS = false)
                preservedJS foreach outputPreservedElement
              }

              if (level == 2 && localname == "head") {

                // 1. Combined and inline CSS
                outputCSS()

                // 2. Combined and inline JS
                outputJS(
                  preservedJS exists {
                    case _: InlineElement => true
                    case _ =>                false
                  }
                )

                // Close head element
                super.endElement(uri, localname, qName)

                inHead = false

              } else if (filter && level == 3 && inHead) {
                currentInlineElement = null
                filter = false
              } else {
                super.endElement(uri, localname, qName)
              }

              level -= 1
            }

            override def characters(chars: Array[Char], start: Int, length: Int) =
              if (filter && level == 3 && inHead && (currentInlineElement ne null))
                currentInlineElement.content.appendAll(chars, start, length)
              else
                super.characters(chars, start, length)
          })

      override def getKeyImpl(pipelineContext: PipelineContext) =
        ProcessorImpl.getInputKey(pipelineContext, getInputByName(ProcessorImpl.INPUT_DATA))

      override def getValidityImpl(pipelineContext: PipelineContext) =
        ProcessorImpl.getInputValidity(pipelineContext, getInputByName(ProcessorImpl.INPUT_DATA))
    })
}

object AssetsAggregator extends Logging {

  val ScriptBaseAtts =
    List(
      "type"  -> "text/javascript"
    )

  val LinkBaseAtts =
    List(
      "rel"   -> "stylesheet",
      "type"  -> "text/css",
      "media" -> "all"
    )

  // Output combined resources
  def aggregate[T](
    resources     : scala.collection.Set[String],
    outputElement : String => T,
    namespaceOpt  : Option[String],
    isCSS         : Boolean
  ): Option[T] =
    resources.nonEmpty option {

      implicit val logger = Loggers.getIndentedLogger("resources")

      // If there is at least one non-platform path, we also hash the app version number
      val hasAppResource = ! (resources forall URLRewriterUtils.isPlatformPath)
      val appVersion = URLRewriterUtils.getApplicationResourceVersion

      // All resource paths are hashed
      val itemsToHash = resources ++ (if (hasAppResource && appVersion.nonAllBlank) Set(appVersion) else Set())
      val resourcesHash = SecureUtils.digestString(itemsToHash mkString "|", "hex")

      // Cache mapping so that resource can be served by resource server
      Caches.resourcesCache.put(new EhElement(resourcesHash, resources.toArray)) // use Array which is compact, serializable and usable from Java

      // Extension and optional namespace parameter
      def extension = if (isCSS) ".css" else ".js"
      def namespace = namespaceOpt map (s"?${Constants.EmbeddingNamespaceParameter}=" + _) getOrElse ""

      // Output link to resource
      val path = "" :: "xforms-server" ::
        (URLRewriterUtils.isResourcesVersioned list URLRewriterUtils.getOrbeonVersionForClient) :::
        "orbeon-" + resourcesHash + extension + namespace :: Nil mkString "/"

      debug("aggregating resources", Seq(
        "isCSS"          -> isCSS.toString,
        "hasAppResource" -> hasAppResource.toString,
        "appVersion"     -> appVersion,
        "resourcesHash"  -> resourcesHash,
        "namespaceOpt"   -> namespaceOpt.orNull,
        "resources"      -> (resources mkString " | ")
      ))

      outputElement(path)
    }
}