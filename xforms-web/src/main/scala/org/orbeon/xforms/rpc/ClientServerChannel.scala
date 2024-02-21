package org.orbeon.xforms.rpc

import cats.data.NonEmptyList
import org.orbeon.xforms
import org.orbeon.xforms.Upload
import org.scalajs.dom

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration


trait ClientServerChannel {

  def sendEvents(
    requestFormId    : xforms.Form,
    eventsToSend     : NonEmptyList[WireAjaxEvent],
    sequenceNumberOpt: Option[Int],
    showProgress     : Boolean,
    ignoreErrors     : Boolean
  ): Future[dom.Document]

  def addFile(
    upload: Upload,
    file  : dom.raw.File,
    wait  : FiniteDuration
  ): Future[Unit]

  def cancel(
    doAbort  : Boolean,
    eventName: String
  ): Unit
}
