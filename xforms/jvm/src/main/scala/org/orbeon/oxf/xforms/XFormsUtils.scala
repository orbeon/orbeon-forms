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
package org.orbeon.oxf.xforms

import java.io.StringReader
import java.net.{URI, URISyntaxException}

import javax.xml.transform.dom.{DOMResult, DOMSource}
import javax.xml.transform.{Result, TransformerException}
import org.ccil.cowan.tagsoup.HTMLSchema
import org.orbeon.dom._
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.processor.DebugProcessor
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{NetUtils, URLRewriterUtils, XPathCache}
import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.oxf.xml.dom.{IOSupport, LocationData}
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo, VirtualNode}
import org.orbeon.xforms.XFormsNames
import org.w3c.dom
import org.xml.sax.InputSource

import scala.util.control.NonFatal

object XFormsUtils {

  // Used by tests
  //@XPathFunction
  def encodeXMLAsDOM(node: org.w3c.dom.Node): String =
    try
      EncodeDecode.encodeXML(TransformerUtils.domToDom4jDocument(node), XFormsProperties.isGZIPState, true, false)
    catch {
      case e: TransformerException =>
        throw new OXFException(e)
    }

  private val TagSoupHtmlSchema = new HTMLSchema

  private def htmlStringToResult(value: String, locationData: LocationData, result: Result): Unit = {
    try {
      val xmlReader = new org.ccil.cowan.tagsoup.Parser
      xmlReader.setProperty(org.ccil.cowan.tagsoup.Parser.schemaProperty, TagSoupHtmlSchema)
      xmlReader.setFeature(org.ccil.cowan.tagsoup.Parser.ignoreBogonsFeature, true)
      val identity = TransformerUtils.getIdentityTransformerHandler
      identity.setResult(result)
      xmlReader.setContentHandler(identity)
      val inputSource = new InputSource
      inputSource.setCharacterStream(new StringReader(value))
      xmlReader.parse(inputSource)
    } catch {
      case NonFatal(_) =>
        throw new ValidationException(s"Cannot parse value as text/html for value: `$value`", locationData)
    }
    //			r.setFeature(Parser.CDATAElementsFeature, false);
    //			r.setFeature(Parser.namespacesFeature, false);
    //			r.setFeature(Parser.ignoreBogonsFeature, true);
    //			r.setFeature(Parser.bogonsEmptyFeature, false);
    //			r.setFeature(Parser.defaultAttributesFeature, false);
    //			r.setFeature(Parser.translateColonsFeature, true);
    //			r.setFeature(Parser.restartElementsFeature, false);
    //			r.setFeature(Parser.ignorableWhitespaceFeature, true);
    //			r.setProperty(Parser.scannerProperty, new PYXScanner());
    //          r.setProperty(Parser.lexicalHandlerProperty, h);
  }

  def htmlStringToDocumentTagSoup(value: String, locationData: LocationData): dom.Document = {
    val document = XMLParsing.createDocument
    val domResult = new DOMResult(document)
    htmlStringToResult(value, locationData, domResult)
    document
  }

  def htmlStringToDom4jTagSoup(value: String, locationData: LocationData): Document = {
    val documentResult = new LocationDocumentResult
    htmlStringToResult(value, locationData, documentResult)
    documentResult.getDocument
  }

  // TODO: implement server-side plain text output with <br> insertion
  //    public static void streamPlainText(final ContentHandler contentHandler, String value, LocationData locationData, final String xhtmlPrefix) {
  //        // 1: Split string along 0x0a and remove 0x0d (?)
  //        // 2: Output string parts, and between them, output <xhtml:br> element
  //        try {
  //            contentHandler.characters(filteredValue.toCharArray(), 0, filteredValue.length());
  //        } catch (SAXException e) {
  //            throw new OXFException(e);
  //        }
  //    }
  def streamHTMLFragment(xmlReceiver: XMLReceiver, value: String, locationData: LocationData, xhtmlPrefix: String): Unit = {
    if (value.nonAllBlank) {
      // don't parse blank values
      val htmlDocument = htmlStringToDocumentTagSoup(value, locationData)
      // Stream fragment to the output
      if (htmlDocument != null)
        TransformerUtils.sourceToSAX(new DOMSource(htmlDocument), new HTMLBodyXMLReceiver(xmlReceiver, xhtmlPrefix))
    }
  }

  def resolveRenderURL(
    containingDocument : XFormsContainingDocument,
    currentElement     : Element,
    url                : String,
    skipRewrite        : Boolean
  ): String = {
    val resolvedURI = resolveXMLBase(containingDocument, currentElement, url)
    val resolvedURIStringNoPortletFragment = uriToStringRemoveFragmentForPortletAndEmbedded(containingDocument, resolvedURI)
    if (skipRewrite)
      resolvedURIStringNoPortletFragment
    else
      NetUtils.getExternalContext.getResponse.rewriteRenderURL(resolvedURIStringNoPortletFragment, null, null)
  }

  def resolveActionURL(containingDocument: XFormsContainingDocument, currentElement: Element, url: String): String = {
    val resolvedURI = resolveXMLBase(containingDocument, currentElement, url)
    val resolvedURIStringNoPortletFragment = uriToStringRemoveFragmentForPortletAndEmbedded(containingDocument, resolvedURI)
    NetUtils.getExternalContext.getResponse.rewriteActionURL(resolvedURIStringNoPortletFragment, null, null)
  }

  private def uriToStringRemoveFragmentForPortletAndEmbedded(containingDocument: XFormsContainingDocument, resolvedURI: URI): String =
    if ((containingDocument.isPortletContainer || containingDocument.isEmbedded) && resolvedURI.getFragment != null) {
      // Page was loaded from a portlet or embedding API and there is a fragment, remove it
      try new URI(resolvedURI.getScheme, resolvedURI.getRawAuthority, resolvedURI.getRawPath, resolvedURI.getRawQuery, null).toString
      catch {
        case e: URISyntaxException =>
          throw new OXFException(e)
      }
    } else
      resolvedURI.toString

  /**
   * Resolve a resource URL including xml:base resolution.
   *
   * @param containingDocument current document
   * @param element            element used to start resolution (if null, no resolution takes place)
   * @param url                URL to resolve
   * @param rewriteMode        rewrite mode (see ExternalContext.Response)
   * @return resolved URL
   */
  def resolveResourceURL(containingDocument: XFormsContainingDocument, element: Element, url: String, rewriteMode: Int): String = {
    val resolvedURI = resolveXMLBase(containingDocument, element, url)
    NetUtils.getExternalContext.getResponse.rewriteResourceURL(resolvedURI.toString, rewriteMode)
  }

  /**
   * Resolve a resource URL including xml:base resolution.
   *
   * @param containingDocument current document
   * @param element            element used to start resolution (if null, no resolution takes place)
   * @param url                URL to resolve
   * @param rewriteMode        rewrite mode (see ExternalContext.Response)
   * @return resolved URL
   */
  def resolveServiceURL(containingDocument: XFormsContainingDocument, element: Element, url: String, rewriteMode: Int): String = {
    val resolvedURI = resolveXMLBase(containingDocument, element, url)
    URLRewriterUtils.rewriteServiceURL(NetUtils.getExternalContext.getRequest, resolvedURI.toString, rewriteMode)
  }

  /**
   * Resolve a URI string against an element, taking into account ancestor xml:base attributes for
   * the resolution, and using the document's request URL as a base.
   *
   * @param containingDocument current document
   * @param element            element used to start resolution (if null, no resolution takes place)
   * @param uri                URI to resolve
   * @return resolved URI
   */
  def resolveXMLBase(containingDocument: XFormsContainingDocument, element: Element, uri: String): URI =
    try {
      element.resolveXMLBase(Option(containingDocument.getRequestPath), uri)
    } catch {
      case e: URISyntaxException =>
        throw new ValidationException(
          s"Error while resolving URI: `$uri`",
          e,
          if (element ne null)
            element.getData.asInstanceOf[LocationData]
          else
            null
        )
    }

  /**
   * Resolve attribute value templates (AVTs).
   *
   * @param xpathContext   current XPath context
   * @param contextNode    context node for evaluation
   * @param attributeValue attribute value
   * @return resolved attribute value
   */
  def resolveAttributeValueTemplates(
    containingDocument : XFormsContainingDocument,
    xpathContext       : XPathCache.XPathContext,
    contextNode        : NodeInfo,
    attributeValue     : String
  ): String = {
    if (attributeValue == null)
      return null
    XPathCache.evaluateAsAvt(xpathContext, contextNode, attributeValue, containingDocument.getRequestStats.getReporter)
  }

  /**
   * Resolve f:url-norewrite attributes on this element, taking into account ancestor f:url-norewrite attributes for
   * the resolution.
   *
   * @param element element used to start resolution
   * @return true if rewriting is turned off, false otherwise
   */
  def resolveUrlNorewrite(element: Element): Boolean = {
    var currentElement = element
    do {
      val urlNorewriteAttribute = currentElement.attributeValue(XMLConstants.FORMATTING_URL_NOREWRITE_QNAME)
      // Return the first ancestor value found
      if (urlNorewriteAttribute != null) return "true" == urlNorewriteAttribute
      currentElement = currentElement.getParent
    } while (currentElement ne null)
    // Default is to rewrite
    false
  }

  /**
   * Log a message and Document.
   *
   * @param debugMessage the message to display
   * @param document     the Document to display
   */
  def logDebugDocument(debugMessage: String, document: Document): Unit =
    DebugProcessor.logger.info(debugMessage + ":\n" + IOSupport.domToPrettyStringJava(document))

  /**
   * Prefix an id with the container namespace if needed. If the id is null, return null.
   *
   * @param containingDocument current ContainingDocument
   * @param id                 id to prefix
   * @return prefixed id or null
   */
  def namespaceId(containingDocument: XFormsContainingDocument, id: CharSequence): String =
    if (id == null)
      null
    else
      containingDocument.getContainerNamespace + id

  /**
   * Remove the container namespace prefix if possible. If the id is null, return null.
   *
   * @param containingDocument current ContainingDocument
   * @param id                 id to de-prefix
   * @return de-prefixed id if possible or null
   */
  def deNamespaceId(containingDocument: XFormsContainingDocument, id: String): String = {
    if (id == null)
      return null
    val containerNamespace = containingDocument.getContainerNamespace
    if (containerNamespace.length > 0 && id.startsWith(containerNamespace))
      id.substring(containerNamespace.length)
    else
      id
  }

  /**
   * Return LocationData for a given node, null if not found.
   *
   * @param node node containing the LocationData
   * @return LocationData or null
   */
  def getNodeLocationData(node: Node): LocationData = {
    val data =
      node match {
        case elem: Element  => elem.getData
        case att: Attribute => att.getData
        case _              => null
      }
    // TODO: other node types
    data match {
      case ld: LocationData => ld
      case id: InstanceData => id.getLocationData
      case _                => null
    }
  }

  /**
   * Return the underlying Node from the given NodeInfo, possibly converting it to a Dom4j Node. Changes to the returned Node may or may not
   * reflect on the original, depending on its type.
   *
   * @param nodeInfo NodeInfo to process
   * @return Node
   */
  def getNodeFromNodeInfoConvert(nodeInfo: NodeInfo): Node =
    nodeInfo match {
      case vn: VirtualNode => vn.getUnderlyingNode.asInstanceOf[Node]
      case _ =>
        if (nodeInfo.getNodeKind == org.w3c.dom.Node.ATTRIBUTE_NODE)
          Attribute(QName(nodeInfo.getLocalPart, Namespace(nodeInfo.getPrefix, nodeInfo.getURI)), nodeInfo.getStringValue)
        else
          TransformerUtils.tinyTreeToDom4j(if (nodeInfo.getParent.isInstanceOf[DocumentInfo]) nodeInfo.getParent
        else
          nodeInfo
      )
    }

  /**
   * Return the underlying Node from the given NodeInfo if possible. If not, throw an exception with the given error
   * message.
   *
   * @param nodeInfo     NodeInfo to process
   * @param errorMessage error message to throw
   * @return Node if found
   */
  def getNodeFromNodeInfo(nodeInfo: NodeInfo, errorMessage: String): Node = {
    if (! nodeInfo.isInstanceOf[VirtualNode])
      throw new OXFException(errorMessage)
    nodeInfo.asInstanceOf[VirtualNode].getUnderlyingNode.asInstanceOf[Node]
  }

  def maybeAVT(attributeValue: String): Boolean =
    attributeValue.indexOf('{') != -1

  def getNamespacedFormId(containingDocument: XFormsContainingDocument): String =
    namespaceId(containingDocument, "xforms-form")

  def getElementId(element: Element): String =
    element.attributeValue(XFormsNames.ID_QNAME)
}