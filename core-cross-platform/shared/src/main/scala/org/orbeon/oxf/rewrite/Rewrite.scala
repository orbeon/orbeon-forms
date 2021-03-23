package org.orbeon.oxf.rewrite

import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.oxf.xml.saxrewrite.{DocumentRootState, FragmentRootState, StatefulHandler}


/**
 * Java impl of oxf-rewrite.xsl. Uses GOF state pattern + SAX to get the job done. The state machine ad hoc and relies a
 * bit on the simplicity of the transformation that we are performing.
 *
 * Wrt the transformation, here is the comment from oxf-rewrite.xsl :
 *
 * This stylesheet rewrites HTML or XHTML for servlets and portlets. URLs are parsed, so it must be made certain that
 * the URLs are well-formed. Absolute URLs are not modified. Relative or absolute paths are supported, as well as the
 * special case of a URL starting with a query string (e.g. "?name=value"). This last syntax is supported by most Web
 * browsers.
 *
 * A. For portlets, it does the following:
 *
 * 1. Rewrite form/@action to WSRP action URL encoding
 * 2. Rewrite a/@href and link/@href to WSRP render encoding
 * 3. Rewrite img/@src, input[@type='image']/@src and script/@src to WSRP resource URL encoding
 * 4. If no form/@method is supplied, force an HTTP POST
 * 5. Escape any wsrp_rewrite occurrence in text not within a script or
 *
 * SCRIPT element to wsrp_rewritewsrp_rewrite. WSRP 1.0 does not appear to specify a particular escape sequence, but we
 * use this one in Orbeon Forms Portal. The escaped sequence is recognized by the Orbeon Forms Portlet and restored to
 * the original sequence, so it is possible to include the string wsrp_rewrite within documents.
 *
 * 6. Occurrences of wsrp_rewrite found within script or SCRIPT elements, as well as occurrences within attributes,
 * are left untouched. This allows them to be recognized by the Orbeon Forms Portlet and rewritten.
 *
 * Known issues for portlets:
 *
 * - The input document should not contain;
 * - elements and attribute containing wsrp_rewrite
 * - namespace URIs containing wsrp_rewrite
 * - processing instructions containing wsrp_rewrite
 *
 * B. For servlets, it rewrites the URLs to be absolute paths, and prepends the context path.
 */
object Rewrite {

  private[rewrite] val SCRIPT_ELT = "script"
  private[rewrite] val OBJECT_ELT = "object"
  private[rewrite] val ACTION_ATT = "action"
  private[rewrite] val METHOD_ATT = "method"
  private[rewrite] val HREF_ATT = "href"
  private[rewrite] val SRC_ATT = "src"
  private[rewrite] val BACKGROUND_ATT = "background"
  private[rewrite] val NOREWRITE_ATT = "url-norewrite"

  def getRewriteXMLReceiver(
    rewriter    : URLRewriter,
    xmlReceiver : XMLReceiver,
    fragment    : Boolean,
    rewriteURI  : String
  ): XMLReceiver =
    new StatefulHandler(
      if (fragment) {
        // Start directly with rewrite state
        val fragmentRootState = new FragmentRootState(null, xmlReceiver)
        val afterRootState    = new RewriteState(fragmentRootState, xmlReceiver, rewriter, 0, rewriteURI)
        fragmentRootState.setNextState(afterRootState)
        fragmentRootState
      } else {
        // Start with root filter
        val documentRootState = new DocumentRootState(null, xmlReceiver)
        val afterRootState    = new RewriteState(documentRootState, xmlReceiver, rewriter, 0, rewriteURI)
        documentRootState.setNextState(afterRootState)
        documentRootState
      }
    )
}