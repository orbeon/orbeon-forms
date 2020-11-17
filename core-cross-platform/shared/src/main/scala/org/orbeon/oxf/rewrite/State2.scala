package org.orbeon.oxf.rewrite

import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.oxf.xml.saxrewrite.State


/**
 * Base state. Simply forwards data to the destination content handler and returns itself. That is unless the
 * (element) depth becomes negative after an end element event. In this case the previous state is returned. This
 * means btw that we are really only considering state changes on start and end element events.
 */
abstract class State2 private[rewrite] (
  previousState : State,
  xmlReceiver   : XMLReceiver,
  val response  : URLRewriter,

  /**
   * Could have been another State. However since the value is determined in one state and then used by a
   * 'descendant' state doing so would have meant that descendant would have to walk it's ancestors to get the
   * value. So, since making this a field instead of a separate State sub-class was easier to implement and is
   * faster a field was used.
   */
  var scriptDepth: Int,
  val rewriteURI: String
) extends State(previousState, xmlReceiver) {

  final private[rewrite] def scriptDepthOnStart(ns: String, localName: String): Unit =
    if (rewriteURI == ns && Rewrite.SCRIPT_ELT == localName)
      scriptDepth += 1

  final private[rewrite] def scriptDepthOnEnd(ns: String, localName: String): Unit =
    if (rewriteURI == ns && Rewrite.SCRIPT_ELT == localName)
      scriptDepth -= 1

  override protected def endElementStart(ns: String, localName: String, qName: String): Unit = {
    scriptDepthOnEnd(ns, localName)
    super.endElementStart(ns, localName, qName)
  }
}