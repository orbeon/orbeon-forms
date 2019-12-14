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

import net.sf.ehcache.{Element ⇒ EhElement}
import org.apache.commons.lang3.StringUtils
import org.orbeon.dom.QName
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xml._
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

import scala.collection.mutable.{Buffer, LinkedHashSet}

/**
  * Aggregate CSS and JS resources under <head>.
  */
class AssetsAggregator extends ProcessorImpl {

  import AssetsAggregator._

  addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA))
  addOutputInfo(new ProcessorInputOutputInfo(ProcessorImpl.OUTPUT_DATA))

  override def createOutput(name: String) =
    addOutput(name, new ProcessorOutputImpl(AssetsAggregator.this, name) {
      override def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver) =
        readInputAsSAX(pipelineContext, ProcessorImpl.INPUT_DATA,
          if (! XFormsProperties.isCombinedResources) xmlReceiver else new SimpleForwardingXMLReceiver(xmlReceiver) {

            sealed trait HeadElement {
              val name: String
              val attributes: Attributes
              def text: Option[String] = None
            }
            case class ReferenceElement(name: String, attributes: Attributes) extends HeadElement
            case class InlineElement(name: String, attributes: Attributes) extends HeadElement {
              var content = new StringBuilder
              override def text = Some(content.toString)
            }

            val attributesImpl = new AttributesImpl

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
            val request  = NetUtils.getExternalContext.getRequest
            val response = NetUtils.getExternalContext.getResponse
            val isSeparateDeployment = URLRewriterUtils.isSeparateDeployment(request)

            // In this mode, resources are described in JSON within a <div>
            val isPortlet = request.getContainerType == "portlet"
            val namespaceOpt = isPortlet option response.getNamespacePrefix
            val isMinimal = XFormsProperties.isMinimalResources

            // Whether a path is a user resource in separate deployment
            def isSeparatePath(path: String) = isSeparateDeployment && ! URLRewriterUtils.isPlatformPath(path)

            override def startElement(uri: String, localname: String, qName: String, attributes: Attributes) = {
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
                  case "link" if (href ne null) && ((resType eq null) || resType == "text/css") && rel == "stylesheet" ⇒
                    if (isSeparatePath(href) || NetUtils.urlHasProtocol(href) || media != "all" || isNorewrite || cssClasses == "xforms-standalone-resource")
                      preservedCSS += ReferenceElement(localname, new AttributesImpl(attributes))
                    else
                      (if (cssClasses == "xforms-baseline") baselineCSS else supplementalCSS) += href
                    filter = true
                  case "script" if (src ne null) && ((resType eq null) || resType == "text/javascript") ⇒
                    if (isSeparatePath(src) || NetUtils.urlHasProtocol(src) || isNorewrite || cssClasses == "xforms-standalone-resource")
                      preservedJS += ReferenceElement(localname, new AttributesImpl(attributes))
                    else
                      (if (cssClasses == "xforms-baseline") baselineJS else supplementalJS) += src
                    filter = true
                  case "style" ⇒
                    currentInlineElement = InlineElement(localname, new AttributesImpl(attributes))
                    preservedCSS += currentInlineElement
                    filter = true
                  case "script" if src eq null ⇒
                    currentInlineElement = InlineElement(localname, new AttributesImpl(attributes))
                    preservedJS += currentInlineElement
                    filter = true
                  case _ ⇒
                }
              }

              if (! filter)
                super.startElement(uri, localname, qName, attributes)
            }

            override def endElement(uri: String, localname: String, qName: String) = {

              lazy val xhtmlPrefix = XMLUtils.prefixFromQName(qName)
              lazy val helper = new XMLReceiverHelper(xmlReceiver)

              // Configurable function to output an element
              def outputElement(getAttributes: String ⇒ Array[String], elementName: String)(resource: String) = {
                attributesImpl.clear()
                XMLReceiverHelper.populateAttributes(attributesImpl, getAttributes(resource))
                helper.element(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, elementName, attributesImpl)
              }

              def outputPreservedElement(e: HeadElement) = {
                helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, e.name, e.attributes)
                e.text foreach helper.text
                helper.endElement()
              }

              def outputScriptCSSAsJSON() = {

                // NOTE: oxf:xhtml-rewrite usually takes care of URL rewriting, but not in JSON content.
                // So we rewrite here.
                def rewritePath(path: String) = response.rewriteResourceURL(path, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)

                def appendJS(path: String) = """{"src":"""" + rewritePath(path) + """"}"""
                def appendCSS(path: String) = """{"src":"""" + rewritePath(path) + """"}"""

                def appendPreservedElement(e: HeadElement) =
                  e match {
                    case ref: ReferenceElement ⇒
                      val srcHref = Option(ref.attributes.getValue("src")) getOrElse ref.attributes.getValue("href")
                      Some("""{"src":"""" + srcHref + """"}""")
                    case inline: InlineElement ⇒
                      inline.text map ("""{"text":"""" + JSON.quoteValue(_) + """"}""")
                  }

                val builder = new StringBuilder
                builder append """{"scripts":["""

                builder append
                  (aggregate(baselineJS, appendJS, namespaceOpt, isCSS = false) ++
                    aggregate(supplementalJS -- baselineJS, appendJS, namespaceOpt, isCSS = false) ++
                      (preservedJS flatMap (appendPreservedElement(_).toList)) mkString ",")

                builder append """],"styles":["""

                builder append
                  (aggregate(baselineCSS, appendCSS, namespaceOpt, isCSS = true) ++
                    aggregate(supplementalCSS -- baselineCSS, appendCSS, namespaceOpt, isCSS = true) ++
                      (preservedCSS flatMap (appendPreservedElement(_).toList)) mkString ",")

                builder append """]}"""

                helper.startElement(xhtmlPrefix, uri, "div", Array("class", "orbeon-portlet-resources"))
                helper.text(builder.toString)
                helper.endElement()
              }

              def outputCSS() = {
                val outputCSSElement = outputElement(resource ⇒ Array("rel", "stylesheet", "href", resource, "type", "text/css", "media", "all"), "link")(_)
                aggregate(baselineCSS, outputCSSElement, namespaceOpt,  isCSS = true)
                aggregate(supplementalCSS -- baselineCSS, outputCSSElement, namespaceOpt, isCSS = true)
                preservedCSS foreach outputPreservedElement
              }

              def outputJS() = {
                val outputJSElement =
                  outputElement(
                    resource ⇒ Array("type", "text/javascript", "src", resource , "defer", "defer"),
                    "script"
                  )(_)

                aggregate(baselineJS, outputJSElement, namespaceOpt, isCSS = false)
                aggregate(supplementalJS -- baselineJS, outputJSElement, namespaceOpt, isCSS = false)
                preservedJS foreach outputPreservedElement
              }

              if (level == 2 && localname == "head") {

                // 1. Combined and inline CSS
                outputCSS()

                // 2. Combined and inline JS
                outputJS()

                // Close head element
                super.endElement(uri, localname, qName)

                inHead = false
              } else if (level == 2 && localname == "body") {

                // Close body element
                super.endElement(uri, localname, qName)

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

  // Output combined resources
  def aggregate[T](
    resources               : scala.collection.Set[String],
    outputElement           : String ⇒ T,
    namespaceOpt            : Option[String],
    isCSS                   : Boolean
  ): Option[T] =
    resources.nonEmpty option {

      implicit val logger = Loggers.getIndentedLogger("resources")

      // If there is at least one non-platform path, we also hash the app version number
      val hasAppResource = ! (resources forall URLRewriterUtils.isPlatformPath)
      val appVersion = URLRewriterUtils.getApplicationResourceVersion

      // All resource paths are hashed
      val itemsToHash = resources ++ (if (hasAppResource && StringUtils.isNotBlank(appVersion)) Set(appVersion) else Set())
      val resourcesHash = SecureUtils.digestString(itemsToHash mkString "|", "hex")

      // Cache mapping so that resource can be served by resource server
      Caches.resourcesCache.put(new EhElement(resourcesHash, resources.toArray)) // use Array which is compact, serializable and usable from Java

      // Extension and optional namespace parameter
      def extension = if (isCSS) ".css" else ".js"
      def namespace = namespaceOpt map ("?ns=" + _) getOrElse ""

      // Output link to resource
      val path = "" :: "xforms-server" ::
        (URLRewriterUtils.isResourcesVersioned list URLRewriterUtils.getOrbeonVersionForClient) :::
        "orbeon-" + resourcesHash + extension + namespace :: Nil mkString "/"

      debug("aggregating resources", Seq(
        "isCSS"          → isCSS.toString,
        "hasAppResource" → hasAppResource.toString,
        "appVersion"     → appVersion,
        "resourcesHash"  → resourcesHash,
        "namespaceOpt"   → namespaceOpt.orNull,
        "resources"      → (resources mkString " | ")
      ))

      outputElement(path)
    }
}