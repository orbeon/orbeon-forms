package org.orbeon.xbl

import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom.html

import scala.scalajs.js


object FrWizard { // otherwise this conflicts with the server-side `xbl.Wizard`

  XBL.declareCompanion("fr|wizard", js.constructorOf[WizardCompanion])

  class WizardCompanion(containerElem: html.Element) extends XBLCompanion {

    override def init(): Unit = ()
    override def destroy(): Unit = ()

    private var listeners: List[js.Function1[PageChangeEvent, Any]] = Nil

    def addPageChangeListener(listener: js.Function1[PageChangeEvent, Any]): Unit =
      listeners ::= listener

    def removePageChangeListener(listener: js.Function1[PageChangeEvent, Any]): Unit =
      listeners = listeners.filterNot(_ eq listener)

    def _dispatchPageChangeEvent(event: PageChangeEvent): Unit =
      listeners.foreach(_.apply(event))
  }

  trait PageChangeEvent extends js.Object {
    val pageName : String
    val pageIndex: js.UndefOr[Int]
  }
}
