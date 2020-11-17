package org.orbeon.oxf.xml.saxrewrite

import org.orbeon.oxf.xml.XMLReceiver
import org.xml.sax.Attributes


class FragmentRootState(previousState: State, xmlReceiver: XMLReceiver)
  extends State(previousState, xmlReceiver) {

  protected var nextState: State = this

  override protected def startElementStart(ns: String, lnam: String, qnam: String, atts: Attributes): State =
    if (nextState eq this)
      this
    else
      nextState.startElement(ns, lnam, qnam, atts)

  def setNextState(nextState: State): Unit =
    this.nextState = nextState
}