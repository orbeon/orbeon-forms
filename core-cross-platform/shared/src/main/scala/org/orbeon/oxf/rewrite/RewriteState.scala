package org.orbeon.oxf.rewrite

import java.nio.Buffer
import java.util
import java.util.StringTokenizer
import org.orbeon.oxf.externalcontext.{URLRewriter, UrlRewriteMode}
import org.orbeon.oxf.util.StringUtils
import org.orbeon.oxf.xml.saxrewrite.State
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiver}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


/**
 * The rewrite state. Essentially this corresponds to the default mode of `oxf-rewrite.xsl`.
 * Basically this:
 *
 * - Rewrites attributes in start element event when need be.
 * - Accumulates text from characters events so that proper char content rewriting can happen.
 * - On an event that indicates the end of potentially rewritable text, e.g. start element,
 *   rewrites and forwards the accumulated characters.
 * - When explicit no write is indicated, e.g. we see attributes `no-rewrite=true`, then
 *   transition to the `NoRewriteState`.
 */
class RewriteState private[rewrite] (
  val stt     : State,
  xmlReceiver : XMLReceiver,
  response    : URLRewriter,
  scriptDepth : Int,
  rewriteURI  : String
) extends State2(stt, xmlReceiver, response, scriptDepth, rewriteURI) {

  /**
   * Used to accumulate characters from characters event. Lazily init'd in characters.
   * <p>
   * NOTE: We use CharBuffer for historical reasons. Since we support Java 1.5 and up, we could use StringBuilder.
   */
  private var charactersBuf: java.nio.CharBuffer = null
  final private val fObjectParent = new util.ArrayList[Boolean]

  //    /**
  //     * Handler for {http://www.w3.org/1999/xhtml}{elt name}.  Assumes namespace test has already
  //     * happened.  Implements :
  //     * <pre>
  //     *   <xsl:template match="xhtml:{elt name}[@{res attrib name}]" >
  //     *     <xsl:copy>
  //     *       <xsl:copy-of select="@*[namespace-uri() = '']"/>
  //     *         <xsl:attribute name="{res attrib name}">
  //     *           <xsl:value-of select="context:rewriteResourceURL(@{res attrib name})"/>
  //     *         </xsl:attribute>
  //     *         <xsl:apply-templates/>
  //     *       </xsl:copy>
  //     *   </xsl:template>
  //     * </pre>
  //     * <p>
  //     * If match is satisfied then modified event is sent to destination contentHandler.
  //     *
  //     * @return null if match is not satisfied and this otherwise.
  //     * @throws SAXException if destination contentHandler throws SAXException
  //     */
  private def handleEltWithResource(elt: String, resAtt: String, ns: String, lnam: String, qnam: String, atts: Attributes): State2 = {
    var ret: State2 = null
    if (elt == lnam) {
      val res = atts.getValue("", resAtt)
      if (res == null)
        return null
      ret = this
      val newAtts = RewriteState.getAttributesFromDefaultNamespace(atts)
      val newRes = response.rewriteResourceURL(res, UrlRewriteMode.AbsolutePathOrRelative)
      val idx = newAtts.getIndex("", resAtt)
      newAtts.setValue(idx, newRes)
      xmlReceiver.startElement(ns, lnam, qnam, newAtts)
    }
    ret
  }

  /**
   * Handle xhtml:object
   */
  private def handleObject(ns: String, lnam: String, qnam: String, atts: Attributes): State2 = {
    var ret: State2 = null
    if (Rewrite.OBJECT_ELT == lnam) {
      val codebaseAttribute = atts.getValue("", "codebase")
      val classidAttribute  = atts.getValue("", "classid")
      val dataAttribute     = atts.getValue("", "data")
      val usemapAttribute   = atts.getValue("", "usemap")
      val archiveAttribute  = atts.getValue("", "archive") // space-separated
      if (classidAttribute == null && codebaseAttribute == null && dataAttribute == null && usemapAttribute == null && archiveAttribute == null)
        return null
      ret = this
      val newAtts = RewriteState.getAttributesFromDefaultNamespace(atts)
      if (codebaseAttribute != null) {
        val newAttribute = response.rewriteResourceURL(codebaseAttribute, UrlRewriteMode.AbsolutePathOrRelative)
        val idx = newAtts.getIndex("", "codebase")
        newAtts.setValue(idx, newAttribute)
      } else {
        // We don't rewrite these attributes if there is a codebase
        if (classidAttribute != null) {
          val newAttribute = response.rewriteResourceURL(classidAttribute, UrlRewriteMode.AbsolutePathOrRelative)
          val idx = newAtts.getIndex("", "classid")
          newAtts.setValue(idx, newAttribute)
        }
        if (dataAttribute != null) {
          val newAttribute = response.rewriteResourceURL(dataAttribute, UrlRewriteMode.AbsolutePathOrRelative)
          val idx = newAtts.getIndex("", "data")
          newAtts.setValue(idx, newAttribute)
        }
        if (usemapAttribute != null) {
          val newAttribute = response.rewriteResourceURL(usemapAttribute, UrlRewriteMode.AbsolutePathOrRelative)
          val idx = newAtts.getIndex("", "usemap")
          newAtts.setValue(idx, newAttribute)
        }
        if (archiveAttribute != null) {
          val st = new StringTokenizer(archiveAttribute, " ")
          val sb = new StringBuilder(archiveAttribute.length * 2)
          var first = true
          while (st.hasMoreTokens) {
            val currentArchive = StringUtils.trimAllToEmpty(st.nextToken)
            val newArchive = response.rewriteResourceURL(currentArchive, UrlRewriteMode.AbsolutePathOrRelative)
            if (! first)
              sb.append(' ')
            sb.append(newArchive)
            first = false
          }
          val idx = newAtts.getIndex("", "archive")
          newAtts.setValue(idx, sb.toString)
        }
      }
      xmlReceiver.startElement(ns, lnam, qnam, newAtts)
    }
    ret
  }

  /**
   * Handle xhtml:applet
   */
  private def handleApplet(ns: String, lnam: String, qnam: String, atts: Attributes): State2 = {
    var ret: State2 = null
    if ("applet" == lnam) {
      val codebaseAttribute = atts.getValue("", "codebase")
      val archiveAttribute  = atts.getValue("", "archive") // comma-separated

      if (archiveAttribute == null && codebaseAttribute == null)
        return null

      ret = this
      val newAtts = RewriteState.getAttributesFromDefaultNamespace(atts)
      if (codebaseAttribute != null) {
        val newAttribute = response.rewriteResourceURL(codebaseAttribute, UrlRewriteMode.AbsolutePathOrRelative)
        val idx = newAtts.getIndex("", "codebase")
        newAtts.setValue(idx, newAttribute)
      } else {
        // We don't rewrite the @archive attribute if there is a codebase
        val st = new StringTokenizer(archiveAttribute, ",")
        val sb = new StringBuilder(archiveAttribute.length * 2)
        var first = true
        while (st.hasMoreTokens) {
          val currentArchive = StringUtils.trimAllToEmpty(st.nextToken)
          val newArchive = response.rewriteResourceURL(currentArchive, UrlRewriteMode.AbsolutePathOrRelative)
          if (! first)
            sb.append(' ')
          sb.append(newArchive)
          first = false
        }
        val idx = newAtts.getIndex("", "archive")
        newAtts.setValue(idx, sb.toString)
      }
      xmlReceiver.startElement(ns, lnam, qnam, newAtts)
    }
    ret
  }

  /**
   * Handle xhtml:param
   */
  private def handleParam(ns: String, lnam: String, qnam: String, atts: Attributes): State2 = {
    var ret: State2 = null
    val inObject = fObjectParent.size >= 2 && fObjectParent.get(fObjectParent.size - 2).booleanValue
    if (inObject && "param" == lnam) {
      val nameAttribute = atts.getValue("", "name")
      val valueAttribute = atts.getValue("", "value")
      if (nameAttribute == null || valueAttribute == null)
        return null
      ret = this
      val newAtts = RewriteState.getAttributesFromDefaultNamespace(atts)
      if ("archive" == StringUtils.trimAllToEmpty(nameAttribute)) {
        val st = new StringTokenizer(valueAttribute, ",")
        val sb = new StringBuilder(valueAttribute.length * 2)
        var first = true
        while (st.hasMoreTokens) {
          val currentArchive = StringUtils.trimAllToEmpty(st.nextToken)
          val newArchive = response.rewriteResourceURL(currentArchive, UrlRewriteMode.AbsolutePathOrRelative)
          if (! first)
            sb.append(' ')
          sb.append(newArchive)
          first = false
        }
        val idx = newAtts.getIndex("", "value")
        newAtts.setValue(idx, sb.toString)
      }
      xmlReceiver.startElement(ns, lnam, qnam, newAtts)
    }
    ret
  }

  //     * Handler for {http://www.w3.org/1999/xhtml}a.  Assumes namespace test has already
  //     *   <xsl:template match="xhtml:a[@href]" >
  //     *         <xsl:attribute name="href">
  //     *           <xsl:choose>
  //     *             <xsl:when test="not(@f:url-type) or @f:url-type = 'render'">
  //     *               <xsl:value-of select="context:rewriteRenderURL(@href)"/>
  //     *             </xsl:when>
  //     *             <xsl:when test="@f:url-type = 'action'">
  //     *               <xsl:value-of select="context:rewriteActionURL(@href)"/>
  //     *             <xsl:when test="@f:url-type = 'resource'">
  //     *               <xsl:value-of select="context:rewriteResourceURL(@href)"/>
  //     *           </xsl:choose>
  //     *       <xsl:apply-templates/>
  //     *     </xsl:copy>
  private def handleA(ns: String, lnam: String, qnam: String, atts: Attributes): State2 = {
    var ret: State2 = null
    if ("a" == lnam) {
      val href = atts.getValue("", Rewrite.HREF_ATT)
      if (href == null)
        return null
      ret = this
      val newAtts = RewriteState.getAttributesFromDefaultNamespace(atts)
      val urlType = atts.getValue(XMLConstants.OPS_FORMATTING_URI, "url-type")
      val portletMode = atts.getValue(XMLConstants.OPS_FORMATTING_URI, "portlet-mode")
      val windowState = atts.getValue(XMLConstants.OPS_FORMATTING_URI, "window-state")
      val newHref =
        if (urlType == null || "render" == urlType)
          response.rewriteRenderURL(href, portletMode, windowState)
        else if ("action" == urlType)
          response.rewriteActionURL(href, portletMode, windowState)
        else if ("resource" == urlType)
          response.rewriteResourceURL(href, UrlRewriteMode.AbsolutePathOrRelative)
        else
          null
      val idx = newAtts.getIndex("", Rewrite.HREF_ATT)
      if (newHref == null && idx != -1)
        newAtts.removeAttribute(idx)
      else
        newAtts.setValue(idx, newHref)
      xmlReceiver.startElement(ns, lnam, qnam, newAtts)
    }
    ret
  }

  //     * Handler for {http://www.w3.org/1999/xhtml}area.  Assumes namespace test has already
  //     *   <xsl:template match="xhtml:area[@href]" >
  //     *           <xsl:value-of select="context:rewriteActionURL(@href)"/>
  private def handleArea(ns: String, lnam: String, qnam: String, atts: Attributes): State2 = {
    var ret: State2 = null
    if ("area" == lnam) {
      val href = atts.getValue("", Rewrite.HREF_ATT)
      if (href == null)
        return null
      ret = this
      val newAtts = RewriteState.getAttributesFromDefaultNamespace(atts)
      val newHref = response.rewriteActionURL(href)
      val idx = newAtts.getIndex("", Rewrite.HREF_ATT)
      newAtts.setValue(idx, newHref)
      xmlReceiver.startElement(ns, lnam, qnam, newAtts)
    }
    ret
  }

  //     * Handler for {http://www.w3.org/1999/xhtml}input.  Assumes namespace test has already
  //     *   <xsl:template match="xhtml:input[@type='image' and @src]" >
  //     *         <xsl:attribute name="src">
  //     *           <xsl:value-of select="context:rewriteActionURL(@src)"/>
  //     * @return null if @type='image' test is not satisfied and
  //     * handleEltWithResource( "input", "src", ... ) otherwise.
  private def handleInput(ns: String, lnam: String, qnam: String, atts: Attributes): State2 =
    if ("image" == atts.getValue("", "type"))
      handleEltWithResource("input", Rewrite.SRC_ATT, ns, lnam, qnam, atts)
    else
      null

  //     * Handler for {http://www.w3.org/1999/xhtml}form.  Assumes namespace test has already
  //     *   <xsl:template match="form | xhtml:form">
  //     *       <xsl:choose>
  //     *         <xsl:when test="@action">
  //     *           <xsl:attribute name="action">
  //     *             <xsl:value-of select="context:rewriteActionURL(@action)"/>
  //     *           </xsl:attribute>
  //     *         </xsl:when>
  //     *         <xsl:otherwise>
  //     *             <xsl:value-of select="context:rewriteActionURL('')"/>
  //     *         </xsl:otherwise>
  //     *       </xsl:choose>
  //     *       <!-- Default is POST instead of GET for portlets -->
  //     *       <xsl:if test="not(@method) and $container-type/* = 'portlet'">
  //     *         <xsl:attribute name="method">post</xsl:attribute>
  //     *       </xsl:if>
  //     * @return null match is not satisfied, this otherwise.
  private def handleForm(ns: String, lnam: String, qnam: String, atts: Attributes): State2 = {
    var ret: State2 = null
    if ("form" == lnam) {
      val newAtts = RewriteState.getAttributesFromDefaultNamespace(atts)
      val action = newAtts.getValue("", Rewrite.ACTION_ATT)
      var newAction: String = null
      if (action == null) {
        newAction = response.rewriteActionURL("")
        newAtts.addAttribute("", Rewrite.ACTION_ATT, Rewrite.ACTION_ATT, "", newAction)
      } else {
        val idx = newAtts.getIndex("", Rewrite.ACTION_ATT)
        newAction = response.rewriteActionURL(action)
        newAtts.setValue(idx, newAction)
      }
      if (atts.getValue("", Rewrite.METHOD_ATT) == null)
        newAtts.addAttribute("", Rewrite.METHOD_ATT, Rewrite.METHOD_ATT, "", "post")
      ret = this
      xmlReceiver.startElement(ns, lnam, qnam, newAtts)
    } else
      ret = null
    ret
  }

  //     * If we have accumulated character data rewrite it and forward it.  Implements :
  //     *   <xsl:template match="text()">
  //     *     <xsl:value-of
  //     *       select="replace(current(), 'wsrp_rewrite', 'wsrp_rewritewsrp_rewrite')"/>
  //     *     <xsl:apply-templates/>
  //     * If there no character data has been accumulated do nothing.  Also clears buffer.
  private def flushCharacters(): Unit = {
    val bfLen = if (charactersBuf == null) 0 else (charactersBuf.position: Int)
    if (bfLen > 0) {
      charactersBuf.asInstanceOf[Buffer].flip // cast: see #4682
      val chs = charactersBuf.array
      val chsStrt = charactersBuf.arrayOffset
      val last = 0
      if (last < bfLen) {
        val len = bfLen - last
        xmlReceiver.characters(chs, chsStrt + last, len)
      }
      charactersBuf.asInstanceOf[Buffer].clear
    }
  }

  override protected def endElementStart(ns: String, lnam: String, qnam: String): Unit = {
    fObjectParent.remove(fObjectParent.size - 1)
    flushCharacters()
    super.endElementStart(ns, lnam, qnam)
  }

  //     * Just calls flushCharacters then tests the event data.  If
  //     * <ul>
  //     * <li>
  //     * @url-norewrite='true' then forward the event to the destination content handler and
  //     * return new NoRewriteState( ... ), otherwise
  //     * </li>
  //     * if ns.equals( XHTML_URI ) then
  //     * <li>if one of the handleXXX methods returns non-null do nothing, otherwise</li>
  //     * forward the event to the destination content handler and return this, otherwise
  //     * </ul>
  //     * if the element is {http://orbeon.org/oxf/xml/formatting}rewrite then implement :
  //     *         <xsl:when test="@type = 'action'">
  //     *           <xsl:value-of select="context:rewriteActionURL(@url)"/>
  //     *         <xsl:when test="@type = 'render'">
  //     *           <xsl:value-of select="context:rewriteRenderURL(@url)"/>
  //     *           <xsl:value-of select="context:rewriteResourceURL(@url)"/>
  //     *     </pre>
  //     * Note this means that we forward characters to the destination content handler instead
  //     * of a start element event, otherwise
  //     * simply forward the event as is to the destination content handler and return this.
  override protected def startElementStart(ns: String, lnam: String, qnam: String, _atts: Attributes): State = {

    var atts = _atts

    fObjectParent.add(Rewrite.OBJECT_ELT == lnam && XMLConstants.XHTML_NAMESPACE_URI == ns)
    val noRewriteIndex = atts.getIndex(XMLConstants.OPS_FORMATTING_URI, Rewrite.NOREWRITE_ATT)
    val noRewriteValue = atts.getValue(noRewriteIndex)
    var ret: State = null
    flushCharacters()
    if (noRewriteValue != null) { // Remove f:url-norewrite attribute
      val attributesImpl = new AttributesImpl(atts)
      attributesImpl.removeAttribute(noRewriteIndex)
      atts = attributesImpl
    }

    if ("true" == noRewriteValue) {
      val stt = new NoRewriteState(this, xmlReceiver, response, scriptDepth, rewriteURI)
      ret = stt.startElement(ns, lnam, qnam, atts)
    } else if (XMLConstants.OPS_FORMATTING_URI == ns && "rewrite" == lnam) {
      val typ = atts.getValue("", "type")
      val url = atts.getValue("", "url")
      if (url != null) {
        val newURL =
          if ("action" == typ)
            response.rewriteActionURL(url)
          else if ("render" == typ)
            response.rewriteRenderURL(url)
          else
            response.rewriteResourceURL(url, UrlRewriteMode.AbsolutePathOrRelative)
        val chs = newURL.toCharArray
        xmlReceiver.characters(chs, 0, chs.length)
      }
    } else {
      scriptDepthOnStart(ns, lnam)
      if (rewriteURI.equals(ns)) {
        ret = handleA(ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleForm(ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleArea(ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleEltWithResource("link", Rewrite.HREF_ATT, ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleEltWithResource("img", Rewrite.SRC_ATT, ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleEltWithResource("video", Rewrite.POSTER_ATT, ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleEltWithResource("video", Rewrite.SRC_ATT, ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleEltWithResource("source", Rewrite.SRC_ATT, ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleEltWithResource("frame", Rewrite.SRC_ATT, ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleEltWithResource("iframe", Rewrite.SRC_ATT, ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleEltWithResource(Rewrite.SCRIPT_ELT, Rewrite.SRC_ATT, ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleInput(ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleEltWithResource("td", Rewrite.BACKGROUND_ATT, ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleEltWithResource("body", Rewrite.BACKGROUND_ATT, ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleObject(ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleApplet(ns, lnam, qnam, atts)
        if (ret != null) return ret
        ret = handleParam(ns, lnam, qnam, atts)
        if (ret != null) return ret
        // Not valid in HTML, but useful for e.g. Dojo contentPane
        ret = handleEltWithResource("div", Rewrite.HREF_ATT, ns, lnam, qnam, atts)
        if (ret != null) return ret
      }
      ret = this
      xmlReceiver.startElement(ns, lnam, qnam, atts)
    }
    ret
  }

  /**
   * If haveScriptAncestor then just forward data to destination contentHandler. Otherwise store that data in the
   * buffer and do not forward. Also manages init'ing and growing charactersBuf as need be.
   */
  override def characters(ch: Array[Char], strt: Int, len: Int): State = {
    if (scriptDepth > 0) xmlReceiver.characters(ch, strt, len)
    else {
      val bufLen =
        if (charactersBuf == null)
          0
        else
          (charactersBuf.position: Int)
      val cpcty = bufLen + (len * 2)
      if (charactersBuf == null || charactersBuf.remaining < cpcty) {
        val newBuf = java.nio.CharBuffer.allocate(cpcty)
        if (charactersBuf != null) {
          charactersBuf.asInstanceOf[Buffer].flip
          newBuf.put(charactersBuf)
        }
        charactersBuf = newBuf
      }
      charactersBuf.put(ch, strt, len)
    }
    this
  }

  override def ignorableWhitespace(ch: Array[Char], strt: Int, len: Int): State = {
    flushCharacters()
    super.ignorableWhitespace(ch, strt, len)
  }

  override def processingInstruction(trgt: String, dat: String): State = {
    flushCharacters()
    super.processingInstruction(trgt, dat)
  }

  override def skippedEntity(nam: String): State = {
    flushCharacters()
    super.skippedEntity(nam)
  }
}

object RewriteState {
  // Return a new `AttributesImpl` containing  all attributes that were in src attributes and that were
  // in the default namespace.
  def getAttributesFromDefaultNamespace(attributes: Attributes): AttributesImpl = {
    val ret = new AttributesImpl
    val size = attributes.getLength
    for (i <- 0 until size) {
      val ns = attributes.getURI(i)
      if (ns == "") {
        val localName = attributes.getLocalName(i)
        val qName     = attributes.getQName(i)
        val typ       = attributes.getType(i)
        val value     = attributes.getValue(i)
        ret.addAttribute(ns, localName, qName, typ, value)
      }
    }
    ret
  }
}