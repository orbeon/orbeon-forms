package org.orbeon.xforms

import cats.effect.IO
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.xforms.Session.broadcastChannelAvailable
import org.scalajs.dom.{BroadcastChannel, MessageEvent}

import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.util.Success


object DuplicateTab {

  import Private.*

  // `Future` used so we can register a callback
  val duplicateTabDetectedF: Future[Unit] = duplicateTabDetectedP.future

  def pingAndRegisterAllHandlers(namespacedFormId: String, uuid: String): Option[IO[Unit]] =
    newBroadcastChannel(TabActivityBroadcastChannel).map { broadcastChannel =>
      IO(postTabOpenPingMessage(broadcastChannel, namespacedFormId: String, uuid: String))
        .flatMap(_ => registerDuplicatedTabHandlers(broadcastChannel, namespacedFormId, uuid))
    }

  def waitForReplyWithTimeout[T](tabDuplicationReplyIo: IO[T], timeout: Duration, timeoutContinuation: IO[Unit]): IO[Unit] =
    tabDuplicationReplyIo
      .timeoutTo(
        timeout,
        timeoutContinuation
      )
      .*>(IO(duplicateTabDetectedP.complete(Success(()))))

  def registerPingHandler(namespacedFormId: String, uuid: String): Unit =
    newBroadcastChannel(TabActivityBroadcastChannel).foreach { tabActivityBroadcastChannel =>

      val _namespacedFormId = namespacedFormId
      val _uuid = uuid

      tabActivityBroadcastChannel.onmessage =
        (event: MessageEvent) => {
          val tabMessage = event.data.asInstanceOf[TabMessage]
          if (tabMessage.namespacedFormId == namespacedFormId && tabMessage.uuid == uuid)
            tabMessage.messageType match {
              case TabOpenPingMessageType =>
                // Another tab has opened with the same namespacedFormId and uuid.
                // We need to send back a message to the channel to indicate that we are the original tab.
                tabActivityBroadcastChannel.postMessage(new TabMessage {
                  override val messageType     : String = TabOpenReplyMessageType
                  override val namespacedFormId: String = _namespacedFormId
                  override val uuid            : String = _uuid
                })
              case _ =>
            }
        }
    }

  // 2025-08-19: `BroadcastChannel` is not available with JSDOM/Node 16 yet.
  def newBroadcastChannel(name: String): Option[BroadcastChannel] =
    broadcastChannelAvailable.option(new BroadcastChannel(name))

  def registerDuplicatedTabHandlers(
    tabActivityBroadcastChannel: BroadcastChannel,
    namespacedFormId           : String,
    uuid                       : String
  ): IO[Unit] = {

    val _namespacedFormId = namespacedFormId
    val _uuid = uuid

    IO.async_[Unit] { callback =>
      tabActivityBroadcastChannel.onmessage =
        (event: MessageEvent) => {
          val tabMessage = event.data.asInstanceOf[TabMessage]
          if (tabMessage.namespacedFormId == namespacedFormId && tabMessage.uuid == uuid)
            tabMessage.messageType match {
              case TabOpenPingMessageType =>
                // Another tab has opened with the same namespacedFormId and uuid.
                // We need to send back a message to the channel to indicate that we are the original tab.
                tabActivityBroadcastChannel.postMessage(new TabMessage {
                  override val messageType     : String = TabOpenReplyMessageType
                  override val namespacedFormId: String = _namespacedFormId
                  override val uuid            : String = _uuid
                })
              case TabOpenReplyMessageType =>
                // Another tab has replied to our ping, meaning that it is the original tab and we are not
                tabActivityBroadcastChannel.close() // channel is no longer needed
                callback(Right(()))
              case _ =>
            }
        }
    }
  }

  def postTabOpenPingMessage(
    tabActivityBroadcastChannel: BroadcastChannel,
    namespacedFormId           : String,
    uuid                       : String
  ): Unit = {

    val _namespacedFormId = namespacedFormId
    val _uuid = uuid

    // If we think we may have been cloned, we send a message to the channel to reach other tabs with the same
    // namespacedFormId/uuid.
    tabActivityBroadcastChannel.postMessage(new TabMessage {
      override val messageType     : String = TabOpenPingMessageType
      override val namespacedFormId: String = _namespacedFormId
      override val uuid            : String = _uuid
    })
  }

  private object Private {

    val TabActivityBroadcastChannel  = "orbeon-tab-activity"
    val TabOpenPingMessageType  = "tab-open-ping"
    val TabOpenReplyMessageType = "tab-open-reply"

    // Use a JavaScript object for message as we cannot guarantee that serialization will work across
    // Scala.js/Orbeon Forms versions.
    trait TabMessage extends js.Object {
      val messageType     : String
      val namespacedFormId: String
      val uuid            : String
    }

    // Use `Promise`/`Future` so we get one-time notification
    val duplicateTabDetectedP = Promise[Unit]()
  }
}
