package org.orbeon.xforms.rpc

import cats.data.NonEmptyList
import org.orbeon.xforms
import org.scalajs.dom

import scala.concurrent.Future


trait ClientServerChannel {

  def sendEvents(
    requestFormId     : xforms.Form,
    eventsToSend      : NonEmptyList[WireAjaxEvent],
    sequenceNumberOpt : Option[Int],
    showProgress      : Boolean,
    ignoreErrors      : Boolean
  ): Future[dom.Document]
}
