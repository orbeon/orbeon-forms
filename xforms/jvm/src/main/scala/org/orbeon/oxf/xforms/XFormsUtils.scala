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
import java.{lang => jl, util => ju}

import cats.implicits.catsSyntaxOptionId
import javax.xml.transform.dom.{DOMResult, DOMSource}
import javax.xml.transform.{Result, TransformerException}
import org.ccil.cowan.tagsoup.HTMLSchema
import org.orbeon.dom._
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.processor.DebugProcessor
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.{NetUtils, URLRewriterUtils, XPathCache}
import org.orbeon.oxf.xforms.XFormsContextStackSupport._
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis
import org.orbeon.oxf.xforms.control.controls.{XFormsOutputControl, XXFormsAttributeControl}
import org.orbeon.oxf.xforms.model.{DataModel, InstanceData}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom4j.{Dom4jUtils, LocationData, LocationDocumentResult}
import org.orbeon.saxon.om.{DocumentInfo, Item, NodeInfo, VirtualNode}
import org.orbeon.xforms.XFormsNames
import org.w3c.dom
import org.xml.sax.InputSource

import org.orbeon.oxf.util.StringUtils._

import scala.collection.JavaConverters._
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

  /**
   * Get the value of a child element known to have only static content.
   *
   * @param childElement element to evaluate (xf:label, etc.)
   * @param acceptHTML   whether the result may contain HTML
   * @param containsHTML whether the result actually contains HTML (null allowed)
   * @return string containing the result of the evaluation
   */
  def getStaticChildElementValue(prefix: String, childElement: Element, acceptHTML: Boolean, containsHTML: Array[Boolean]): String = {

    assert(childElement != null)

    // No HTML found by default
    if (containsHTML != null) containsHTML(0) = false

    // Try to get inline value
    val sb = new jl.StringBuilder(20)

    // Visit the subtree and serialize
    // NOTE: It is a little funny to do our own serialization here, but the alternative is to build a DOM and
    // serialize it, which is not trivial because of the possible interleaved xf:output's. Furthermore, we
    // perform a very simple serialization of elements and text to simple (X)HTML, not full-fledged HTML or XML
    // serialization.
    Dom4jUtils.visitSubtree(childElement, new LHHAElementVisitorListener(prefix, acceptHTML, containsHTML, sb, childElement))
    if (acceptHTML && containsHTML != null && !containsHTML(0)) {
      // We went through the subtree and did not find any HTML
      // If the caller supports the information, return a non-escaped string so we can optimize output later
      sb.toString.unescapeXmlMinimal
    } else {
      // We found some HTML, just return it
      sb.toString
    }
  }

  /**
   * Get the value of an element by trying single-node binding, value attribute, linking attribute, and inline value
   * (including nested XHTML and xf:output elements).
   *
   * This may return an HTML string if HTML is accepted and found, or a plain string otherwise.
   *
   * @param container         current XBLContainer
   * @param contextStack      context stack for XPath evaluation
   * @param sourceEffectiveId source effective id for id resolution
   * @param childElement      element to evaluate (xf:label, etc.)
   * @param acceptHTML        whether the result may contain HTML
   * @param containsHTML      whether the result actually contains HTML (null allowed)
   * @return string containing the result of the evaluation, null if evaluation failed (see comments)
   */
  def getElementValue(
    container         : XBLContainer,
    contextStack      : XFormsContextStack,
    sourceEffectiveId : String,
    childElement      : Element,
    acceptHTML        : Boolean,
    defaultHTML       : Boolean,
    containsHTML      : Array[Boolean]
  ): Option[String] = {

    if (containsHTML ne null)
      containsHTML(0) = defaultHTML

    def maybeEscapeValue(value: String): String =
      value match {
        case v if acceptHTML && containsHTML == null => v.escapeXmlMinimal
        case v => v
      }

    val currentBindingContext = contextStack.getCurrentBindingContext

    // Try to get single node binding
    def fromBinding: Option[Option[String]] =
      currentBindingContext.newBind option {
        currentBindingContext.singleItemOpt map DataModel.getValue map maybeEscapeValue
      }

    // Try to get value attribute
    // NOTE: This is an extension attribute not standard in XForms 1.0 or 1.1
    def fromValueAtt: Option[Option[String]]  =
      childElement.attributeValueOpt(XFormsNames.VALUE_QNAME) map { valueAttribute =>
        val currentNodeset = currentBindingContext.nodeset
        if (! currentNodeset.isEmpty) {
          val stringResultOpt =
            try
              XPathCache.evaluateAsStringOpt(
                contextItems       = currentNodeset,
                contextPosition    = currentBindingContext.position,
                xpathString        = valueAttribute,
                namespaceMapping   = container.getNamespaceMappings(childElement),
                variableToValueMap = contextStack.getCurrentBindingContext.getInScopeVariables,
                functionLibrary    = container.getContainingDocument.functionLibrary,
                functionContext    = contextStack.getFunctionContext(sourceEffectiveId),
                baseURI            = null,
                locationData       = childElement.getData.asInstanceOf[LocationData],
                reporter           = container.getContainingDocument.getRequestStats.getReporter
              )
            catch {
              case NonFatal(t) =>
                XFormsError.handleNonFatalXPathError(container, t)
                "".some
            }

          stringResultOpt map maybeEscapeValue
        } else
          // There is a value attribute but the evaluation context is empty
          None
      }

    def fromNestedContent: Option[String] = {
      val sb = new jl.StringBuilder(20)
      Dom4jUtils.visitSubtree(
        childElement,
        new XFormsUtils.LHHAElementVisitorListener(
          container         = container,
          contextStack      = contextStack,
          sourceEffectiveId = sourceEffectiveId,
          acceptHTML        = acceptHTML,
          defaultHTML       = defaultHTML,
          containsHTML      = containsHTML,
          sb                = sb,
          childElement      = childElement
        )
      )

      maybeEscapeValue(sb.toString).some
    }

    fromBinding orElse fromValueAtt getOrElse fromNestedContent
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
   * Resolve a URI string against an element, taking into account ancestor xml:base attributes for
   * the resolution, and using the document's request URL as a base.
   *
   * @param containingDocument current document
   * @param element            element used to start resolution (if null, no resolution takes place)
   * @param uri                URI to resolve
   * @return resolved URI
   */
  def resolveXMLBase(containingDocument: XFormsContainingDocument, element: Element, uri: String): URI =
    resolveXMLBase(element, containingDocument.getRequestPath, uri)

  /**
   * Resolve a URI string against an element, taking into account ancestor xml:base attributes for
   * the resolution.
   *
   * @param element element used to start resolution (if null, no resolution takes place)
   * @param baseURI optional base URI
   * @param uri     URI to resolve
   * @return resolved URI
   */
  def resolveXMLBase(element: Element, baseURI: String, uri: String): URI = try {

    // Allow for null Element
    if (element == null)
      return new URI(uri)

    val xmlBaseValues = new ju.ArrayList[String]
    // Collect xml:base values
    var currentElement = element
    do {
      val xmlBaseAttribute = currentElement.attributeValue(XMLConstants.XML_BASE_QNAME)
      if (xmlBaseAttribute != null)
        xmlBaseValues.add(xmlBaseAttribute)
      currentElement = currentElement.getParent
    } while (currentElement != null)

    // Append base if needed
    if (baseURI != null) xmlBaseValues.add(baseURI)

    // Go from root to leaf
    ju.Collections.reverse(xmlBaseValues)
    xmlBaseValues.add(uri)

    // Resolve paths from root to leaf
    var result: URI = null
    for (currentXMLBase <- xmlBaseValues.asScala) {
      val currentXMLBaseURI = new URI(currentXMLBase)
      result =
        if (result == null)
          currentXMLBaseURI
        else
          result.resolve(currentXMLBaseURI)
    }
    result
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
    DebugProcessor.logger.info(debugMessage + ":\n" + Dom4jUtils.domToPrettyStringJava(document))

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
          DocumentFactory.createAttribute(QName.apply(nodeInfo.getLocalPart, Namespace(nodeInfo.getPrefix, nodeInfo.getURI)), nodeInfo.getStringValue)
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
    if (!nodeInfo.isInstanceOf[VirtualNode])
      throw new OXFException(errorMessage)
    nodeInfo.asInstanceOf[VirtualNode].getUnderlyingNode.asInstanceOf[Node]
  }

  val VoidElements = Set(
    // HTML 5: http://www.w3.org/TR/html5/syntax.html#void-elements
    "area", "base", "br", "col", "command", "embed", "hr", "img", "input", "keygen", "link", "meta", "param", "source", "track", "wbr", // Legacy
    "basefont", "frame", "isindex"
  )

  private class LHHAElementVisitorListener private (
    prefix            : String,
    container         : XBLContainer,
    contextStack      : XFormsContextStack,
    sourceEffectiveId : String,
    acceptHTML        : Boolean,
    defaultHTML       : Boolean,
    containsHTML      : Array[Boolean],
    sb                : jl.StringBuilder,
    childElement      : Element,
    hostLanguageAVTs  : Boolean,
  ) extends Dom4jUtils.VisitorListener {

    thisLHHAElementVisitorListener =>

    // Constructor for "static" case, i.e. when we know the child element cannot have dynamic content
    def this(
      prefix       : String,
      acceptHTML   : Boolean,
      containsHTML : Array[Boolean],
      sb           : jl.StringBuilder,
      childElement : Element
    ) {
      this(
        prefix            = prefix,
        container         = null,
        contextStack      = null,
        sourceEffectiveId = null,
        acceptHTML        = acceptHTML,
        defaultHTML       = false,
        containsHTML      = containsHTML,
        sb                = sb,
        childElement      = childElement,
        hostLanguageAVTs  = false
      )
    }

    // Constructor for "dynamic" case, i.e. when we know the child element can have dynamic content
    def this(
      container         : XBLContainer,
      contextStack      : XFormsContextStack,
      sourceEffectiveId : String,
      acceptHTML        : Boolean,
      defaultHTML       : Boolean,
      containsHTML      : Array[Boolean],
      sb                : jl.StringBuilder,
      childElement      : Element
    ) {
      this(
        prefix            = container.getFullPrefix,
        container         = container,
        contextStack      = contextStack,
        sourceEffectiveId = sourceEffectiveId,
        acceptHTML        = acceptHTML,
        defaultHTML       = defaultHTML,
        containsHTML      = containsHTML,
        sb                = sb,
        childElement      = childElement,
        hostLanguageAVTs  = XFormsProperties.isHostLanguageAVTs
      )
    }

    private var lastIsStart = false

    override def startElement(element: Element) {

      implicit val ctxStack: XFormsContextStack = contextStack

      if (element.getQName == XFormsNames.XFORMS_OUTPUT_QNAME) {
        // This is an xf:output nested among other markup
        val outputControl: XFormsOutputControl =
          new XFormsOutputControl(container, null, element, null) {
            // Override this as super.getContextStack() gets the containingDocument's stack, and here we need whatever is the current stack
            // Probably need to modify super.getContextStack() at some point to NOT use the containingDocument's stack
            override def getContextStack: XFormsContextStack =
              thisLHHAElementVisitorListener.contextStack

            override def getEffectiveId: String =
              // Return given source effective id, so we have a source effective id for resolution of index(), etc.
              sourceEffectiveId

            override def isAllowedBoundItem(item: Item): Boolean = DataModel.isAllowedBoundItem(item)
          }
        val isHTMLMediatype = !defaultHTML && LHHAAnalysis.isHTML(element) || defaultHTML && !LHHAAnalysis.isPlainText(element)
        withBinding(element, sourceEffectiveId, outputControl.getChildElementScope(element)) { bindingContext =>
          outputControl.setBindingContext(
            bindingContext = bindingContext,
            parentRelevant = true,
            update         = false,
            restoreState   = false,
            state          = None
          )
        }
        if (outputControl.isRelevant) if (acceptHTML) if (isHTMLMediatype) {
          if (containsHTML != null) containsHTML(0) = true // this indicates for sure that there is some nested HTML
          sb.append(outputControl.getExternalValue())
        }
        else { // Mediatype is not HTML so we don't escape
          sb.append(outputControl.getExternalValue().escapeXmlMinimal)
        }
        else if (isHTMLMediatype) { // HTML is not allowed here, better tell the user
          throw new OXFException("HTML not allowed within element: " + childElement.getName)
        }
        else sb.append(outputControl.getExternalValue())
      } else {
        // This is a regular element, just serialize the start tag to no namespace
        // If HTML is not allowed here, better tell the user
        if (!acceptHTML) throw new OXFException("Nested XHTML or XForms not allowed within element: " + childElement.getName)
        if (containsHTML != null) containsHTML(0) = true
        sb.append('<')
        sb.append(element.getName)
        val attributes = element.attributes
        if (attributes.size > 0) {
          for (attribute <- attributes.asScala) {
            val currentAttribute = attribute.asInstanceOf[Attribute]
            val currentAttributeName = currentAttribute.getName
            val currentAttributeValue = currentAttribute.getValue
            val resolvedValue =
              if (hostLanguageAVTs && maybeAVT(currentAttributeValue)) {
                // This is an AVT, use attribute control to produce the output
                val attributeControl = new XXFormsAttributeControl(container, element, currentAttributeName, currentAttributeValue, element.getName)

                withBinding(element, sourceEffectiveId, attributeControl.getChildElementScope(element)) { bindingContext =>
                  attributeControl.setBindingContext(
                    bindingContext = bindingContext,
                    parentRelevant = true,
                    update = false,
                    restoreState = false,
                    state = None
                  )
                }
                attributeControl.getExternalValue()
              } else if (currentAttributeName == "id") {
                // This is an id, prefix if needed
                prefix + currentAttributeValue
              } else {
                // Simply use control value
                currentAttributeValue
              }
            // Only consider attributes in no namespace
            if ("" == currentAttribute.getNamespaceURI) {
              sb.append(' ')
              sb.append(currentAttributeName)
              sb.append("=\"")
              if (resolvedValue != null) sb.append(resolvedValue.escapeXmlMinimal)
              sb.append('"')
            }
          }
        }
        sb.append('>')
        lastIsStart = true
      }
    }

    override def endElement(element: Element) {
      val elementName = element.getName
      if ((!lastIsStart || ! VoidElements(elementName)) && !(element.getQName == XFormsNames.XFORMS_OUTPUT_QNAME)) {
        // This is a regular element, just serialize the end tag to no namespace
        // UNLESS the element was just opened. This means we output <br>, not <br></br>, etc.
        sb.append("</")
        sb.append(elementName)
        sb.append('>')
      }
      lastIsStart = false
    }

    override def text(text: Text) {
      sb.append(if (acceptHTML) text.getStringValue.escapeXmlMinimal
      else text.getStringValue
      )
      lastIsStart = false
    }
  }

  def maybeAVT(attributeValue: String): Boolean =
    attributeValue.indexOf('{') != -1

  def getNamespacedFormId(containingDocument: XFormsContainingDocument): String =
    namespaceId(containingDocument, "xforms-form")

  def getElementId(element: Element): String =
    element.attributeValue(XFormsNames.ID_QNAME)
}