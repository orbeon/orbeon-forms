/**
 *  Copyright (C) 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.converter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ExternalContext.Response;
import org.orbeon.oxf.portlet.PortletExternalContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.saxrewrite.RootFilter;
import org.orbeon.oxf.xml.saxrewrite.State;
import org.orbeon.oxf.xml.saxrewrite.StatefullHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * <!-- AbstractRewrite -->
 * Java impl of oxf-rewrite.xsl.  Uses GOF state pattern + SAX to get the job done.
 * The state machine ad hoc and relies a bit on the simplicity of the transformation that we are
 * perfoming.
 *
 * Wrt the transformation, here is the comment from oxf-rewrite.xsl :
 * This stylesheet rewrites HTML or XHTML for servlets and portlets. URLs are parsed, so it must
 * be made certain that the URLs are well-formed. Absolute URLs are not modified. Relative or
 * absolute paths are supported, as well as the special case of a URL starting with a query string
 * (e.g. "?name=value"). This last syntax is supported by most Web browsers.
 *
 * A. For portlets, it does the following:
 * 1. Rewrite form/@action to WSRP action URL encoding
 * 2. Rewrite a/@href and link/@href to WSRP render encoding
 * 3. Rewrite img/@src, input[@type='image']/@src and script/@src to WSRP resource URL encoding
 * 4. If no form/@method is supplied, force an HTTP POST
 * 5. Escape any wsrp_rewrite occurence in text not within a script or
 * SCRIPT element to wsrp_rewritewsrp_rewrite. WSRP 1.0 does not appear to
 * specify a particular escape sequence, but we use this one in PresentationServer Portal. The
 * escaped sequence is recognized by the PresentationServer Portlet and restored to the
 * original sequence, so it is possible to include the string wsrp_rewrite within documents.
 * 6. Occurrences of wsrp_rewrite found within script or SCRIPT elements, as well as occurrences
 * within attributes, are left untouched. This allows them to be recognized by the
 * PresentationServer Portlet and rewritten.
 *
 * Known issues for portlets:
 *
 * o The input document should not contain;
 * o elements and attribute containing wsrp_rewrite
 * o namespace URIs containing wsrp_rewrite
 * o processing instructions containing wsrp_rewrite
 *
 * B. For servlets, it resrites the URLs to be absolute paths, and prepends the context path.
 */
abstract class AbstractRewrite extends ProcessorImpl {
    /**
     * <!-- REWRITE_IN -->
     * Name of the input that receives the content that is to be rewritten.
     */
    private static final String REWRITE_IN = "rewrite-in";
    /**
     * <!-- SCRIPT_ELT -->
     * What you think.
     */
    static final String SCRIPT_ELT = "script";
    /**
     * <!-- ACTION_ATT -->
     * What you think.
     */
    static final String ACTION_ATT = "action";
    /**
     * <!-- METHOD_ATT -->
     * What you think.
     */
    static final String METHOD_ATT = "method";
    /**
     * <!-- HREF_ATT -->
     * What you think.
     */
    static final String HREF_ATT = "href";
    /**
     * <!-- SRC_ATT -->
     * What you think.
     */
    static final String SRC_ATT = "src";
    /**
     * <!-- BACKGROUND_ATT -->
     * What you think.
     */
    static final String BACKGROUND_ATT = "background";
    /**
     * <!-- NOREWRITE_ATT -->
     * What you think.
     */
    static final String NOREWRITE_ATT = "url-norewrite";

    /**
     * <!-- State2 -->
     * Base state.  Simply forwards data to the destination content handler and returns itself.
     * That is unless the ( element ) depth becomes negative after an end element event.  In this
     * case the previous state is returned.  This means btw that we are really only considering
     * state changes on start and end element events.
     */
    protected static abstract class State2 extends State {
        /**
         * <!-- response -->
         * Performs the URL rewrites.
         */
        protected final Response response;
        /**
         * <!-- isPortlet -->
         * A sub-state, if you will.  Didn't implement this as a sub-class of State as it doesn't
         * change during the course of a transformation.
         *
         * @see AbstractRewrite
         */
        protected final boolean isPortlet;
        /**
         * <!-- scriptDepth -->
         * Could have been another State.  However since the value is determined in one state and
         * then used by a 'descendent' state doing so would have meant that descendent would have
         * to walk it's ancestors to get the value.  So, since making this a field instead of
         * a separate State sub-class was easier to implement and is faster a field was used.
         *
         * @see AbstractRewrite
         */
        protected int scriptDepth;
        /**
         * <!-- rewriteURI -->
         *
         * @see
         */
        protected final String rewriteURI;

        /**
         * <!-- State -->
         *
         * @param stt        The previous state.
         * @param cntntHndlr The destination for the rewrite transformation.
         * @param rspns      Used to perform URL rewrites.
         * @param isPrtlt    Whether or not the context is a portlet context.
         * @param scrptDpth  How below script elt we are.
         * @param rwrtURI    uri of elements ( i.e. xhtml uri or "" ) of elements that need
         *                   rewriting.
         * @see #previous
         * @see #contentHandler
         * @see #response
         * @see #isPortlet
         */
        State2(final State stt, final ContentHandler cntntHndlr, final Response rspns
                , final boolean isPrtlt, final int scrptDpth, final String rwrtURI) {
            super(stt, cntntHndlr);
            response = rspns;
            isPortlet = isPrtlt;
            scriptDepth = scrptDpth;
            rewriteURI = rwrtURI;
        }

        /**
         * <!-- scriptDepthOnStart -->
         * Adjusts scriptDepth
         *
         * @see #scriptDepth
         */
        final void scriptDepthOnStart(final String ns, final String lnam) {
            if (rewriteURI.equals(ns) && SCRIPT_ELT.equals(lnam)) {
                scriptDepth++;
            }
        }

        /**
         * <!-- scriptDepthOnEnd -->
         * Adjusts scriptDepth
         *
         * @see #scriptDepth
         */
        final void scriptDepthOnEnd(final String ns, final String lnam) {
            if (rewriteURI.equals(ns) && SCRIPT_ELT.equals(lnam)) {
                scriptDepth--;
            }
        }

        /**
         * <!-- endElementStart -->
         *
         * @see State2
         */
        protected void endElementStart(final String ns, final String lnam, final String qnam)
                throws SAXException {
            scriptDepthOnEnd(ns, lnam);
            super.endElementStart(ns, lnam, qnam);
        }
    }

    /**
     * <!-- RewriteState -->
     * The rewrite state.  Essentially this corresponds to the default mode of oxf-rewrite.xsl.
     * Basically this :
     * <ul>
     * <li>Rewrites attribs in start element event when need be.
     * <li>Accumulates text from characters events so that proper char content rewriting can
     * happen.
     * <li>On an event that indicates the end of potentially rewritable text, e.g. start element,
     * rewrites and forwards the accumlated characters.
     * <li>When explicit no write is indicated, e.g. we see attrib no-rewrite=true, then
     * transition to the NoRewriteState.
     * </ul>
     */
    static class RewriteState extends State2 {
        /**
         * <!-- WSRP_REWRITE_REPLACMENT_CHARS -->
         * Chars useD to replace "wsrp_rewrite" in text.
         *
         * @see AbstractRewrite
         * @see RewriteState
         * @see #flushCharacters()
         */
        private static final char[] WSRP_REWRITE_REPLACMENT_CHARS = new char[]
                {'w', 's', 'r', 'p', '_', 'r', 'e', 'w', 'r', 'i', 't', 'e', 'w', 's', 'r', 'p', '_', 'r'
                        , 'e', 'w', 'r', 'i', 't', 'e',};
        /**
         * <!-- wsrprewriteMatcher -->
         * Used to find wsrp_rewrite in char data.
         *
         * @see RewriteState
         */
        private final Matcher wsrprewriteMatcher;
        /**
         * <!-- charactersBuf -->
         * Used to accumlate characters from characters event.  Lazily init'd in characters.
         * Btw we use CharacterBuffer instead of StringBuffer because :
         * <ul>
         * <li>We want something that works in JDK 1.4.</li>
         * <li>
         * We don't want the performance penalty of StringBuffer's synchronization in JDK 1.5.
         * <li>
         * </ul>
         *
         * Btw if we didn't care about 1.4 we could use StringBuilder instead.
         *
         * @see RewriteState
         * @see #characters(char[], int, int)
         * @see #flushCharacters()
         */
        private java.nio.CharBuffer charactersBuf;

        /**
         * <!-- RewriteState -->
         * Calls super( ... ) and initializes wsrprewriteMatcher with "wsrp_rewrite"
         *
         * @param rwrtURI
         */
        RewriteState(final State stt, final ContentHandler cntntHndlr, final Response rspns
                , final boolean isPrtlt, final int scrptDpth, final String rwrtURI) {
            super(stt, cntntHndlr, rspns, isPrtlt, scrptDpth, rwrtURI);
            final Pattern ptrn = Pattern.compile("wsrp_rewrite");
            wsrprewriteMatcher = ptrn.matcher("");
        }

        /**
         * <!-- handleEltWithResource -->
         * Handler for {http://www.w3.org/1999/xhtml}{elt name}.  Assumes namespace test has already
         * happened.  Implements :
         * <pre>
         *   <xsl:template match="xhtml:{elt name}[@{res attrib name}]" >
         *     <xsl:copy>
         *       <xsl:copy-of select="@*[namespace-uri() = '']"/>
         *         <xsl:attribute name="{res attrib name}">
         *           <xsl:value-of select="context:rewriteActionURL(@{res attrib name})"/>
         *         </xsl:attribute>
         *         <xsl:apply-templates/>
         *       </xsl:copy>
         *   </xsl:template>
         * </pre>
         *
         * If match is satisfied then modified event is sent to destination contentHandler.
         *
         * @return null if match is not satisfied and this otherwise.
         * @throws SAXException if destination contentHandler throws SAXException
         * @see AbstractRewrite
         */
        private State2 handleEltWithResource
                (final String elt, final String resAtt, final String ns, final String lnam
                        , final String qnam, final Attributes atts)
                throws SAXException {
            State2 ret = null;
            done :
            if (elt.equals(lnam)) {

                final String res = atts.getValue("", resAtt);
                if (res == null) break done;

                ret = this;
                final AttributesImpl newAtts = XMLUtils.getAttribsFromDefaultNamespace(atts);
                final String newRes = response.rewriteResourceURL(res, false);
                final int idx = newAtts.getIndex("", resAtt);
                newAtts.setValue(idx, newRes);
                contentHandler.startElement(ns, lnam, qnam, newAtts);
            }
            return ret;
        }

        /**
         * <!-- handleA -->
         * Handler for {http://www.w3.org/1999/xhtml}a.  Assumes namespace test has already
         * happened.  Implements :
         * <pre>
         *   <xsl:template match="xhtml:a[@href]" >
         *     <xsl:copy>
         *       <xsl:copy-of select="@*[namespace-uri() = '']"/>
         *         <xsl:attribute name="href">
         *           <xsl:choose>
         *             <xsl:when test="not(@f:url-type) or @f:url-type = 'render'">
         *               <xsl:value-of select="context:rewriteRenderURL(@href)"/>
         *             </xsl:when>
         *             <xsl:when test="@f:url-type = 'action'">
         *               <xsl:value-of select="context:rewriteActionURL(@href)"/>
         *             </xsl:when>
         *             <xsl:when test="@f:url-type = 'resource'">
         *               <xsl:value-of select="context:rewriteResourceURL(@href)"/>
         *             </xsl:when>
         *           </xsl:choose>
         *         </xsl:attribute>
         *       <xsl:apply-templates/>
         *     </xsl:copy>
         *   </xsl:template>
         * </pre>
         *
         * If match is satisfied then modified event is sent to destination contentHandler.
         *
         * @return null if match is not satisfied and this otherwise.
         * @throws SAXException if destination contentHandler throws SAXException
         * @see AbstractRewrite
         */
        private State2 handleA
                (final String ns, final String lnam, final String qnam, final Attributes atts)
                throws SAXException {
            State2 ret = null;
            done :
            if ("a".equals(lnam)) {

                final String href = atts.getValue("", HREF_ATT);
                if (href == null) break done;

                ret = this;
                final AttributesImpl newAtts = XMLUtils.getAttribsFromDefaultNamespace(atts);
                final String url_typ = atts.getValue(XMLConstants.OPS_FORMATTING_URI, "url-type");

                final String newHref;
                if (url_typ == null || "render".equals(url_typ)) {
                    newHref = response.rewriteRenderURL(href);
                } else if ("action".equals(url_typ)) {
                    newHref = response.rewriteActionURL(href);
                } else if ("resource".equals(url_typ)) {
                    newHref = response.rewriteResourceURL(href, false);
                } else {
                    newHref = null;
                }
                final int idx = newAtts.getIndex("", HREF_ATT);
                if (newHref == null && idx != -1) {
                    newAtts.removeAttribute(idx);
                } else {
                    newAtts.setValue(idx, newHref);
                }
                contentHandler.startElement(ns, lnam, qnam, newAtts);
            }
            return ret;
        }

        /**
         * <!-- handleArea -->
         * Handler for {http://www.w3.org/1999/xhtml}area.  Assumes namespace test has already
         * happened.  Implements :
         * <pre>
         *   <xsl:template match="xhtml:area[@href]" >
         *     <xsl:copy>
         *       <xsl:copy-of select="@*[namespace-uri() = '']"/>
         *         <xsl:attribute name="href">
         *           <xsl:value-of select="context:rewriteActionURL(@href)"/>
         *         </xsl:attribute>
         *         <xsl:apply-templates/>
         *       </xsl:copy>
         *   </xsl:template>
         * </pre>
         *
         * If match is satisfied then modified event is sent to destination contentHandler.
         *
         * @return null if match is not satisfied and this otherwise.
         * @throws SAXException if destination contentHandler throws SAXException
         * @see AbstractRewrite
         */
        private State2 handleArea
                (final String ns, final String lnam, final String qnam, final Attributes atts)
                throws SAXException {
            State2 ret = null;
            done :
            if ("area".equals(lnam)) {

                final String href = atts.getValue("", HREF_ATT);
                if (href == null) break done;

                ret = this;
                final AttributesImpl newAtts = XMLUtils.getAttribsFromDefaultNamespace(atts);
                final String newHref = response.rewriteActionURL(href);
                final int idx = newAtts.getIndex("", HREF_ATT);
                newAtts.setValue(idx, newHref);
                contentHandler.startElement(ns, lnam, qnam, newAtts);
            }
            return ret;
        }

        /**
         * <!-- handleInput -->
         * Handler for {http://www.w3.org/1999/xhtml}input.  Assumes namespace test has already
         * happened.  Implements :
         * <pre>
         *   <xsl:template match="xhtml:input[@type='image' and @src]" >
         *     <xsl:copy>
         *       <xsl:copy-of select="@*[namespace-uri() = '']"/>
         *         <xsl:attribute name="src">
         *           <xsl:value-of select="context:rewriteActionURL(@src)"/>
         *         </xsl:attribute>
         *         <xsl:apply-templates/>
         *       </xsl:copy>
         *   </xsl:template>
         * </pre>
         *
         * If match is satisfied then modified event is sent to destination contentHandler.
         *
         * @return null if @type='image' test is not satisfied and
         *         handleEltWithResource( "input", "src", ... ) otherwise.
         * @throws SAXException if destination contentHandler throws SAXException
         * @see AbstractRewrite
         */
        private State2 handleInput
                (final String ns, final String lnam, final String qnam, final Attributes atts)
                throws SAXException {
            final State2 ret;
            final String typ = atts.getValue("", "type");
            if ("image".equals(typ)) {
                ret = handleEltWithResource("input", SRC_ATT, ns, lnam, qnam, atts);
            } else {
                ret = null;
            }
            return ret;
        }

        /**
         * <!-- handleForm -->
         * Handler for {http://www.w3.org/1999/xhtml}input.  Assumes namespace test has already
         * happened.  Implements :
         * <pre>
         *   <xsl:template match="form | xhtml:form">
         *     <xsl:copy>
         *       <xsl:copy-of select="@*[namespace-uri() = '']"/>
         *       <xsl:choose>
         *         <xsl:when test="@action">
         *           <xsl:attribute name="action">
         *             <xsl:value-of select="context:rewriteActionURL(@action)"/>
         *           </xsl:attribute>
         *         </xsl:when>
         *         <xsl:otherwise>
         *           <xsl:attribute name="action">
         *             <xsl:value-of select="context:rewriteActionURL('')"/>
         *           </xsl:attribute>
         *         </xsl:otherwise>
         *       </xsl:choose>
         *       <!-- Default is POST instead of GET for portlets -->
         *       <xsl:if test="not(@method) and $container-type/* = 'portlet'">
         *         <xsl:attribute name="method">post</xsl:attribute>
         *       </xsl:if>
         *       <xsl:choose>
         *         <xsl:when test="@portlet:is-portlet-form = 'true'">
         *           <xsl:apply-templates mode="norewrite"/>
         *         </xsl:when>
         *         <xsl:otherwise>
         *           <xsl:apply-templates/>
         *         </xsl:otherwise>
         *       </xsl:choose>
         *     </xsl:copy>
         *   </xsl:template>
         * </pre>
         *
         * If match is satisfied then modified event is sent to destination contentHandler.
         *
         * @return null match is not satisfied,
         *         new NoRewriteState( this, contentHandler, response, isPortlet
         *         , haveScriptAncestor ) if is-portlet-form='true', and this
         *         otherwise.
         * @throws SAXException if destination contentHandler throws SAXException
         * @see AbstractRewrite
         */
        private State2 handleForm
                (final String ns, final String lnam, final String qnam, final Attributes atts)
                throws SAXException {
            final State2 ret;
            if ("form".equals(lnam)) {

                final AttributesImpl newAtts = XMLUtils.getAttribsFromDefaultNamespace(atts);

                final String actn = newAtts.getValue("", ACTION_ATT);
                final String newActn;
                if (actn == null) {
                    newActn = response.rewriteActionURL("");
                    newAtts.addAttribute("", ACTION_ATT, ACTION_ATT, "", newActn);
                } else {
                    final int idx = newAtts.getIndex("", ACTION_ATT);
                    newActn = response.rewriteActionURL(actn);
                    newAtts.setValue(idx, newActn);
                }

                if (atts.getValue("", METHOD_ATT) == null && isPortlet) {
                    newAtts.addAttribute("", METHOD_ATT, METHOD_ATT, "", "post");
                }

                final String isPrtltFrm
                        = atts.getValue("http://orbeon.org/oxf/xml/portlet", "is-portlet-form");
                if ("true".equals(isPrtltFrm)) {
                    ret = new NoRewriteState
                            (this, contentHandler, response, isPortlet, scriptDepth, rewriteURI);
                } else {
                    ret = this;
                }
                contentHandler.startElement(ns, lnam, qnam, newAtts);
            } else {
                ret = null;
            }
            return ret;
        }

        /**
         * <!-- flushCharacters -->
         * If we have accumlated character data rewrite it and forward it.  Implements :
         * <pre>
         *   <xsl:template match="text()">
         *     <xsl:value-of
         *       select="replace(current(), 'wsrp_rewrite', 'wsrp_rewritewsrp_rewrite')"/>
         *     <xsl:apply-templates/>
         *   </xsl:template>
         * </pre>
         * If there no character data has been accumulated do nothing.  Also clears buffer.
         *
         * @see AbstractRewrite
         * @see RewriteState
         */
        private void flushCharacters() throws SAXException {
            final int bfLen = charactersBuf == null ? 0 : charactersBuf.position();
            if (bfLen > 0) {
                charactersBuf.flip();
                final char[] chs = charactersBuf.array();
                final int chsStrt = charactersBuf.arrayOffset();
                wsrprewriteMatcher.reset(charactersBuf);
                int last = 0;
                while (wsrprewriteMatcher.find()) {
                    final int strt = wsrprewriteMatcher.start();
                    final int len = strt - last;
                    contentHandler.characters(chs, chsStrt + last, len);
                    contentHandler.characters
                            (WSRP_REWRITE_REPLACMENT_CHARS, 0, WSRP_REWRITE_REPLACMENT_CHARS.length);
                    last = wsrprewriteMatcher.end();
                }
                if (last < bfLen) {
                    final int len = bfLen - last;
                    contentHandler.characters(chs, chsStrt + last, len);
                }
                charactersBuf.clear();
            }
        }

        /**
         * <!-- endElement -->
         * Just calls flushCharacters and super.endElement( ... )
         *
         * @see State2#endElement(String, String, String)
         */
        protected void endElementStart(final String ns, final String lnam, final String qnam)
                throws SAXException {
            flushCharacters();
            super.endElementStart(ns, lnam, qnam);
        }

        /**
         * <!-- startElementStart -->
         * Just calls flushCharacters then tests the event data.  If
         * <ul>
         * <li>
         *
         * @url-norewrite='true' then forward the event to the destination content handler and
         * return new NoRewriteState( ... ), otherwise
         * </li>
         * <li>
         * if ns.equals( XHTML_URI ) then
         * <ul>
         * <li>if one of the handleXXX methods returns non-null do nothing, otherwise</li>
         * <li>
         * forward the event to the destination content handler and return this, otherwise
         * </li>
         * </ul>
         * </li>
         * <li>
         * if the element is {http://orbeon.org/oxf/xml/formatting}rewrite then implement :
         * <pre>
         *       <xsl:choose>
         *         <xsl:when test="@type = 'action'">
         *           <xsl:value-of select="context:rewriteActionURL(@url)"/>
         *         </xsl:when>
         *         <xsl:when test="@type = 'render'">
         *           <xsl:value-of select="context:rewriteRenderURL(@url)"/>
         *         </xsl:when>
         *         <xsl:otherwise>
         *           <xsl:value-of select="context:rewriteResourceURL(@url)"/>
         *         </xsl:otherwise>
         *       </xsl:choose>
         *     </pre>
         * Note this means that we forward characters to the destination content handler instead
         * of a start element event, otherwise
         * </li>
         * <li>
         * simply forward the event as is to the destination content handler and return this.
         * </li>
         * </ul>
         * @see State2#skippedEntity(String)
         */
        protected State startElementStart
                (final String ns, final String lnam, final String qnam, final Attributes atts)
                throws SAXException {
            final String no_urlrewrite
                    = atts.getValue(XMLConstants.OPS_FORMATTING_URI, NOREWRITE_ATT);
            State ret = null;
            flushCharacters();
            done :
            if ("true".equals(no_urlrewrite)) {
                final State stt = new NoRewriteState
                        (this, contentHandler, response, isPortlet, scriptDepth, rewriteURI);
                ret = stt.startElement(ns, lnam, qnam, atts);
            } else if (XMLConstants.OPS_FORMATTING_URI.equals(ns) && "rewrite".equals(lnam)) {
                final String typ = atts.getValue("", "type");
                final String url = atts.getValue("", "url");
                if (url != null) {
                    final String newURL;
                    if ("action".equals(typ)) {
                        newURL = response.rewriteActionURL(url);
                    } else if ("render".equals(typ)) {
                        newURL = response.rewriteRenderURL(url);
                    } else {
                        newURL = response.rewriteResourceURL(url, false);
                    }
                    final char[] chs = newURL.toCharArray();
                    contentHandler.characters(chs, 0, chs.length);
                }
            } else {
                scriptDepthOnStart(ns, lnam);
                if (rewriteURI.equals(ns)) {
                    ret = handleA(ns, lnam, qnam, atts);
                    if (ret != null) break done;

                    ret = handleForm(ns, lnam, qnam, atts);
                    if (ret != null) break done;

                    ret = handleArea(ns, lnam, qnam, atts);
                    if (ret != null) break done;

                    ret = handleEltWithResource("link", HREF_ATT, ns, lnam, qnam, atts);
                    if (ret != null) break done;

                    ret = handleEltWithResource("img", SRC_ATT, ns, lnam, qnam, atts);
                    if (ret != null) break done;

                    ret = handleEltWithResource("frame", SRC_ATT, ns, lnam, qnam, atts);
                    if (ret != null) break done;
                    
                    ret = handleEltWithResource("iframe", SRC_ATT, ns, lnam, qnam, atts);
                    if (ret != null) break done;

                    ret = handleEltWithResource(SCRIPT_ELT, SRC_ATT, ns, lnam, qnam, atts);
                    if (ret != null) break done;

                    ret = handleInput(ns, lnam, qnam, atts);
                    if (ret != null) break done;

                    ret = handleEltWithResource("td", BACKGROUND_ATT, ns, lnam, qnam, atts);
                    if (ret != null) break done;

                    ret = handleEltWithResource("body", BACKGROUND_ATT, ns, lnam, qnam, atts);
                    if (ret != null) break done;
                }
                ret = this;
                contentHandler.startElement(ns, lnam, qnam, atts);
            }
            return ret;
        }

        /**
         * <!-- characters -->
         * If haveScriptAncestor then just forward data to destination contentHandler.  Otherwise
         * store that data in the buffer and do not forward.  Also manages init'ing and growing
         * charactersBuf as need be.
         *
         * @see #charactersBuf
         * @see AbstractRewrite
         * @see RewriteState
         */
        public State characters(final char[] ch, final int strt, final int len)
                throws SAXException {
            if (scriptDepth > 0) {
                contentHandler.characters(ch, strt, len);
            } else {
                final int bufLen = charactersBuf == null ? 0 : charactersBuf.position();
                final int cpcty = bufLen + (len * 2);
                if (charactersBuf == null || charactersBuf.remaining() < cpcty) {
                    final java.nio.CharBuffer newBuf = java.nio.CharBuffer.allocate(cpcty);
                    if (charactersBuf != null) {
                        charactersBuf.flip();
                        newBuf.put(charactersBuf);
                    }
                    charactersBuf = newBuf;
                }
                charactersBuf.put(ch, strt, len);
            }
            return this;
        }

        /**
         * <!-- ignorableWhitespace -->
         * Just calls flushCharacters and super.ignorableWhitespace( ... )
         *
         * @see State2#ignorableWhitespace(char[], int, int)
         */
        public State ignorableWhitespace(final char[] ch, final int strt, final int len)
                throws SAXException {
            flushCharacters();
            return super.ignorableWhitespace(ch, strt, len);
        }

        /**
         * <!-- processingInstruction -->
         * Just calls flushCharacters and super.processingInstruction( ... )
         *
         * @see State2#processingInstruction(String, String)
         */
        public State processingInstruction(final String trgt, final String dat)
                throws SAXException {
            flushCharacters();
            return super.processingInstruction(trgt, dat);
        }

        /**
         * <!-- skippedEntity -->
         * Just calls flushCharacters and super.skippedEntity( ... )
         *
         * @see State2#skippedEntity(String)
         */
        public State skippedEntity(final String nam) throws SAXException {
            flushCharacters();
            return super.skippedEntity(nam);
        }
    }

    /**
     * <!-- NoRewriteState -->
     * Essentially this corresponds to the norewrite mode of oxf-rewrite.xsl.  i.e.  Just forwards
     * events to the destination content handler until we finish the initial element ( depth < 0 )
     * or until it encounters @url-norewrite='false'.  In the first case transitions to the previous
     * state and in the second case it transitions to
     * new RewriteState( this, contentHandler, response, isPortlet, haveScriptAncestor ).
     */
    static class NoRewriteState extends State2 {
        NoRewriteState(final State2 stt, final ContentHandler cntntHndlr, final Response rspns
                , final boolean isPrtlt, final int scrptDpth, final String rwrtURI) {
            super(stt, cntntHndlr, rspns, isPrtlt, scrptDpth, rwrtURI);
        }

        /**
         * <!-- startElement -->
         *
         * @see NoRewriteState
         */
        protected State startElementStart
                (final String ns, final String lnam, final String qnam, final Attributes atts)
                throws SAXException {
            final String no_urlrewrite
                    = atts.getValue(XMLConstants.OPS_FORMATTING_URI, NOREWRITE_ATT);
            final State ret;
            if ("false".equals(no_urlrewrite)) {
                final State stt = new RewriteState
                        (this, contentHandler, response, isPortlet, scriptDepth, rewriteURI);
                ret = stt.startElement(ns, lnam, qnam, atts);
            } else {
                scriptDepthOnStart(ns, lnam);
                final Attributes newAtts = XMLUtils.getAttribsFromDefaultNamespace(atts);
                contentHandler.startElement(ns, lnam, qnam, newAtts);
                ret = this;
            }
            return ret;
        }
    }

    /**
     * <!-- RewriteOutput -->
     *
     * @see #readImpl(PipelineContext, ContentHandler)
     */
    private final class RewriteOutput extends ProcessorImpl.CacheableTransformerOutputImpl {

        final String rewriteURI;

        /**
         * <!-- RewriteOutput -->
         * Just calls super( ... )
         */
        private RewriteOutput(final Class cls, final String nam, final String rwrtURI) {
            super(cls, nam);
            rewriteURI = rwrtURI;
        }

        /**
         * <!-- readImpl -->
         * Creates a StatefullHandler and uses that to translate the events from the input,
         * rewrite-in, and then send them to the contentHandler ( the output ).
         *
         * @see AbstractRewrite
         * @see StatefullHandler
         */
        public void readImpl(final PipelineContext ctxt, final ContentHandler cntntHndlr) {
            final ExternalContext extrnlCtxt
                    = (ExternalContext) ctxt.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

            final Response rspns = extrnlCtxt.getResponse();

            // Do the conversion
            final boolean isPrtlt = extrnlCtxt instanceof PortletExternalContext;
            final RootFilter initStt = new RootFilter(null, cntntHndlr);
            final State aftRt = new RewriteState
                    (initStt, cntntHndlr, rspns, isPrtlt, 0, rewriteURI);
            initStt.setNextState(aftRt);

            final StatefullHandler stFlHndlr
                    = new StatefullHandler(initStt);

            readInputAsSAX(ctxt, REWRITE_IN, stFlHndlr);
        }
    }

    /**
     * <!-- rewriteURI -->
     * Namespace of the elements that are to be rewritten.
     *
     * @see #AbstractRewrite(String)
     */
    final String rewriteURI;

    /**
     * <!-- AbstractRewrite -->
     * Just declares input 'rewrite-in' and output 'rewrite-out'.
     *
     * @param rwrtURI e.g. "http://www.w3.org/1999/xhtml" or ""
     */
    public AbstractRewrite(final String rwrtURI) {
        rewriteURI = rwrtURI;
        final ProcessorInputOutputInfo in = new ProcessorInputOutputInfo(REWRITE_IN);
        addInputInfo(in);
        final ProcessorInputOutputInfo out = new ProcessorInputOutputInfo("rewrite-out");
        addOutputInfo(out);
    }

    /**
     * <!-- createOutput -->
     *
     * @return new RewriteOutput( cls, nam ) after adding it with addOutput.
     */
    public ProcessorOutput createOutput(final String nam) {
        final Class cls = getClass();
        final ProcessorOutput ret = new RewriteOutput(cls, nam, rewriteURI);
        addOutput(nam, ret);
        return ret;
    }
}
