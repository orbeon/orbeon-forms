package org.orbeon.oxf.rewrite

import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.xml.{XMLConstants, XMLReceiver}
import org.orbeon.oxf.xml.saxrewrite.State
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


/**
 * Essentially this corresponds to the norewrite mode of oxf-rewrite.xsl. i.e. Just forwards events to the
 * destination content handler until we finish the initial element (depth < 0) or until it encounters
 * `@url-norewrite='false'`. In the first case transitions to the previous state and in the second case it transitions
 * to new RewriteState(this, contentHandler, response, haveScriptAncestor).
 */
class NoRewriteState private[rewrite] (
  previousState : State2,
  xmlReceiver   : XMLReceiver,
  response      : URLRewriter,
  scriptDepth   : Int,
  rewriteURI    : String
) extends State2(previousState, xmlReceiver, response, scriptDepth, rewriteURI) {

  override protected def startElementStart(ns: String, localName: String, qName: String, _atts: Attributes): State = {

    var atts = _atts

    val noRewriteIndex = atts.getIndex(XMLConstants.OPS_FORMATTING_URI, Rewrite.NOREWRITE_ATT)
    val noRewriteValue = atts.getValue(noRewriteIndex)

    if (noRewriteValue != null) {
      // Remove `f:url-norewrite` attribute
      val attributesImpl = new AttributesImpl(atts)
      attributesImpl.removeAttribute(noRewriteIndex)
      atts = attributesImpl
    }

    if ("false" == noRewriteValue) {
      val stt = new RewriteState(this, xmlReceiver, response, scriptDepth, rewriteURI)
      stt.startElement(ns, localName, qName, atts)
    } else {
      scriptDepthOnStart(ns, localName)
      val newAtts = RewriteState.getAttributesFromDefaultNamespace(atts)
      xmlReceiver.startElement(ns, localName, qName, newAtts)
      this
    }
  }
}
