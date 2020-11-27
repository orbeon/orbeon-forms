package org.orbeon.xforms.offline.demo

import cats.data.NonEmptyList
import org.orbeon.xforms.rpc.{ClientServerChannel, WireAjaxEvent}
import org.scalajs.dom

import scala.concurrent.Future


object LocalClientServerChannel extends ClientServerChannel[dom.Document] {
  def sendEvents(
    requestFormId     : String,
    eventsToSend      : NonEmptyList[WireAjaxEvent],
    sequenceNumberOpt : Option[Int],
    showProgress      : Boolean,
    ignoreErrors      : Boolean
  ): Future[dom.Document] = {

    ???

  }
}