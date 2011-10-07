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

import org.orbeon.oxf.pipeline.api.{XMLReceiver, PipelineContext}
import org.orbeon.oxf.xml._
import java.lang.String
import org.xml.sax.Attributes
import net.sf.ehcache.{Element ⇒ EhElement }
import org.orbeon.oxf.common.Version
import org.xml.sax.helpers.AttributesImpl
import org.orbeon.oxf.util._
import scala.collection.JavaConversions._
import org.orbeon.oxf.processor._
import collection.mutable.{Buffer, LinkedHashSet}
import org.orbeon.oxf.xforms._
import org.dom4j.QName
import org.apache.commons.lang.StringUtils
import ResourcesAggregator._

/**
 * Aggregate CSS and JS resources under <head>.
 */
class ResourcesAggregator extends ProcessorImpl {

    addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA))
    addOutputInfo(new ProcessorInputOutputInfo(ProcessorImpl.OUTPUT_DATA))

    override def createOutput(name: String) = {
        val output = new ProcessorOutputImpl(ResourcesAggregator.this, name) {
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
                        val baselineCSS = LinkedHashSet[String]()
                        val baselineJS = LinkedHashSet[String]()
                        val supplementalCSS = LinkedHashSet[String]()
                        val supplementalJS = LinkedHashSet[String]()

                        val preservedCSS = Buffer[HeadElement]()
                        val preservedJS = Buffer[HeadElement]()

                        // Whether we are in separate deployment as in that case we don't combine paths to user resources
                        val isSeparateDeployment = URLRewriterUtils.isSeparateDeployment(NetUtils.getExternalContext.getRequest)

                        // In this mode, resources are described in JSON within a <div>
                        val isAsyncPortletLoad = NetUtils.getExternalContext.getRequest.getContainerType == "portlet" && XFormsProperties.isAsyncPortletLoad
                        val isMinimal = XFormsProperties.isMinimalResources
                        val asyncPortletLoadScripts = if (isAsyncPortletLoad) XFormsFeatures.getAsyncPortletLoadScripts map (_.getResourcePath(isMinimal)) else Array.empty[String]

                        // Whether a path is a user resource in separate deployment
                        def isSeparatePath(path: String) = isSeparateDeployment && ! URLRewriterUtils.isPlatformPath(path)

                        override def startElement(uri: String, localname: String, qName: String, attributes: Attributes) = {
                            level += 1

                            if (level == 2 && localname == "head") {
                                inHead = true
                            } else if (level == 3 && inHead) {

                                implicit def attributesToSAXAttribute(attributes: Attributes) = new {
                                    def getValue(qName: QName) = attributes.getValue(qName.getNamespaceURI, qName.getName)
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
                                        if (isSeparatePath(href) || NetUtils.urlHasProtocol(href) || media != "all" || isNorewrite)
                                            preservedCSS += ReferenceElement(localname, new AttributesImpl(attributes))
                                        else
                                            (if (cssClasses == "xforms-baseline") baselineCSS else supplementalCSS) += href
                                        filter = true
                                    case "script" if (src ne null) && ((resType eq null) || resType == "text/javascript") ⇒
                                        if (isSeparatePath(src) || NetUtils.urlHasProtocol(src) || isNorewrite)
                                            preservedJS += ReferenceElement(localname, new AttributesImpl(attributes))
                                        else
                                            (if (cssClasses == "xforms-baseline") baselineJS else supplementalJS) += src
                                        filter = true
                                    case "style" ⇒
                                        currentInlineElement = InlineElement(localname, new AttributesImpl(attributes))
                                        preservedCSS += currentInlineElement
                                        filter = true
                                    case "script" if (src eq null) ⇒
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
                            lazy val helper = new ContentHandlerHelper(xmlReceiver)

                            // Configurable function to output an element
                            def outputElement(getAttributes: String ⇒ Array[String], elementName: String)(resource: String) = {
                                attributesImpl.clear()
                                ContentHandlerHelper.populateAttributes(attributesImpl, getAttributes(resource))
                                helper.element(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, elementName, attributesImpl)
                            }

                            def outputPreservedElement(e: HeadElement) = {
                                helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, e.name, e.attributes)
                                e.text foreach (helper.text(_))
                                helper.endElement()
                            }
                            
                            def outputScriptCSSAsJSON() = {

                                def rewritePath(path: String) = NetUtils.getExternalContext.getResponse.rewriteResourceURL(path, false)

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

//                                def outputStuff()
//                                outputStuff(baselineJS, supplementalJS, preservedJS, false, appendJS, appendPreservedElement)

                                builder append
                                    (aggregate(baselineJS -- asyncPortletLoadScripts, false, appendJS) ++
                                        aggregate(supplementalJS -- baselineJS -- asyncPortletLoadScripts, false, appendJS) ++
                                            (preservedJS flatMap (appendPreservedElement(_).toSeq)) mkString ",")
                                
                                builder append """],"styles":["""

                                builder append
                                    (aggregate(baselineCSS, true, appendCSS) ++
                                        aggregate(supplementalCSS -- baselineCSS, true, appendCSS) ++
                                            (preservedCSS flatMap (appendPreservedElement(_).toSeq)) mkString ",")
                                
                                builder append """]}"""

                                helper.startElement(xhtmlPrefix, uri, "div", Array("class", "orbeon-portlet-resources"))
                                helper.text(builder.toString)
                                helper.endElement()
                            }

                            def outputCSS() = {
                                val outputCSSElement = outputElement(resource ⇒ Array("rel", "stylesheet", "href", resource, "type", "text/css", "media", "all"), "link") _
                                aggregate(baselineCSS, true, outputCSSElement)
                                aggregate(supplementalCSS -- baselineCSS, true, outputCSSElement)
                                preservedCSS foreach (outputPreservedElement(_))
                            }

                            def outputJS() = {
                                val outputJSElement = outputElement(resource ⇒ Array("type", "text/javascript", "src", resource), "script") _
                                aggregate(baselineJS -- asyncPortletLoadScripts, false, outputJSElement)
                                aggregate(supplementalJS -- baselineJS -- asyncPortletLoadScripts, false, outputJSElement)
                                preservedJS foreach (outputPreservedElement(_))
                            }

                            if (level == 2 && localname == "head") {

                                if (! isAsyncPortletLoad) {
                                    // 1. Combined and inline CSS
                                    outputCSS()

                                    // 2. Combined and inline JS
                                    if (! XFormsProperties.isJavaScriptAtBottom)
                                        outputJS()
                                }

                                // Close head element
                                super.endElement(uri, localname, qName)

                                inHead = false
                            } else if (level == 2 && localname == "body") {

                                // Place info about scripts and CSS just before the end of the body
                                if (isAsyncPortletLoad)
                                    outputScriptCSSAsJSON()

                                // Close body element
                                super.endElement(uri, localname, qName)

                                // Combined and inline JS
                                // Scripts at the bottom of the page. This is not valid HTML, but it is a recommended practice for
                                // performance as of early 2008. See http://developer.yahoo.com/performance/rules.html#js_bottom
                                if (! isAsyncPortletLoad && XFormsProperties.isJavaScriptAtBottom)
                                    outputJS()

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
        }
        addOutput(name, output)
        output
    }
}

object ResourcesAggregator {
    // Output combined resources
    def aggregate[T](resources: scala.collection.Set[String], isCSS: Boolean, outputElement: String ⇒ T): Option[T] = {
        if (resources.nonEmpty) {
            // If there is at least one non-platform path, we also hash the app version number
            val hasAppResource = resources exists (! URLRewriterUtils.isPlatformPath(_))
            val appVersion = URLRewriterUtils.getApplicationResourceVersion

            // All resource paths are hashed
            val itemsToHash = resources ++ (if (hasAppResource && StringUtils.isNotBlank(appVersion)) Set(appVersion) else Set())
            val resourcesHash = ScalaUtils.digest("SHA-1", Seq(itemsToHash mkString "|"))

            // Cache mapping so that resource can be served by oxf:resource-server
            Caches.resourcesCache.put(new EhElement(resourcesHash, resources.toArray)) // use Array which is serializable and usable from Java

            // Output link to resource
            val path = "" :: "xforms-server" ::
                (if (URLRewriterUtils.isResourcesVersioned) List(Version.getVersionNumber) else Nil) :::
                "orbeon-" + resourcesHash + (if (isCSS) ".css" else ".js") :: Nil mkString "/"

            val result = outputElement(path)

            // Store on disk if requested to make the resource available to external software, like Apache
            if (XFormsProperties.isCacheCombinedResources) {
                val resourcesConfig = resources.toSeq map (r ⇒ new XFormsFeatures.ResourceConfig(r, r))

                assert(resourcesConfig.head.getResourcePath(false) == resources.head) // set order is tricky so make sure order is kept

                val combinedLastModified = XFormsResourceServer.computeCombinedLastModified(resourcesConfig, false)
                XFormsResourceServer.cacheResources(resourcesConfig, path, combinedLastModified, isCSS, false)
            }

            Some(result)
        } else
            None
    }
}