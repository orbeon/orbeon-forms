package org.orbeon.oxf.xml.saxrewrite

import org.orbeon.oxf.xml.XMLReceiver
import org.xml.sax.Attributes

/**
 * Ignores everything before start element except for processing instructions. On startElement switches to nextState.
 *
 * So if this is used as the initial state then the result is that the prologue and epilogue are ignored (except
 * processing instructions) while the root element is passed to the next state. nextState is initialized to this,
 * consequently nothing interesting will happen unless setNext is called.
 */
class DocumentRootState(previousState: State, xmlReceiver: XMLReceiver)
  extends State(previousState, xmlReceiver) {

  protected var nextState: State = this

  protected def startElementStart(ns: String, lnam: String, qnam: String, atts: Attributes): State =
    if (nextState eq this)
      this
    else
      nextState.startElement(ns, lnam, qnam, atts)

  def setNextState(nextState: State): Unit =
    this.nextState = nextState

  override def characters         (ch: Array[Char], strt: Int, len: Int): State = this
  override def ignorableWhitespace(ch: Array[Char], strt: Int, len: Int): State = this
}