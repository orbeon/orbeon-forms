package org.orbeon.xforms

import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import org.orbeon.xforms.facade.{BroadcastChannel, MessageEvent}

import scala.collection.mutable

object Session {
  import Private._

  sealed trait SessionStatus
  case object SessionActive        extends SessionStatus
  case object SessionAboutToExpire extends SessionStatus
  case object SessionExpired       extends SessionStatus

  def initialize(configuration: rpc.ConfigurationProperties, sessionExpirationTriggerMillis: Long): Unit = {
    configurationOpt = Some(Configuration(
      sessionId                      = configuration.sessionId,
      sessionHeartbeatEnabled        = configuration.sessionHeartbeatEnabled,
      maxInactiveIntervalMillis      = configuration.maxInactiveIntervalMillis,
      sessionExpirationTriggerMillis = sessionExpirationTriggerMillis,
      sessionExpirationMarginMillis  = configuration.sessionExpirationMarginMillis
    ))
  }

  def updateWithLocalNewestEventTime(): Unit =
    configurationOpt.foreach(sessionActivity(_, local = true, AjaxClient.newestEventTime))

  def sessionHasExpired(): Unit =
    configurationOpt.foreach(sessionExpiration)

  def logout(): Unit =
    configurationOpt.foreach(sessionLogout(_, local = true))

  def expired: Boolean = sessionStatus == SessionExpired

  type SessionUpdateListener = SessionUpdate => Unit
  case class SessionUpdate(
    sessionStatus                  : SessionStatus,
    sessionHeartbeatEnabled        : Boolean,
    approxSessionExpiredTimeMillis : Long
  )

  def addSessionUpdateListener(listener: SessionUpdateListener): Unit    = sessionUpdateListeners += listener
  def removeSessionUpdateListener(listener: SessionUpdateListener): Unit = sessionUpdateListeners -= listener

  private object Private {
    case class Configuration(
      sessionId                     : String,
      sessionHeartbeatEnabled       : Boolean,
      maxInactiveIntervalMillis     : Long,
      sessionExpirationTriggerMillis: Long,
      sessionExpirationMarginMillis : Long
    )

    private sealed trait SessionMessage { def sessionId: String }
    private case class SessionActivity(override val sessionId: String, localNewestEventTime: Long) extends SessionMessage
    private case class SessionLogout  (override val sessionId: String)                             extends SessionMessage

    var configurationOpt: Option[Configuration]  = None
    var sessionStatus: SessionStatus             = SessionActive

    private var maxNewestEventTimeAmongAllPages: Long = -1L

    private lazy val sessionActivityBroadcastChannel = {
      val broadcastChannel = new BroadcastChannel("orbeon-session-activity")

      broadcastChannel.onmessage = (event: MessageEvent) => {
        for {
          sessionMessage <- sessionMessageFromJson(event.data.asInstanceOf[String])
          configuration  <- configurationOpt
          if sessionMessage.sessionId == configuration.sessionId
        } {
          sessionMessage match {
            case SessionActivity(_, localNewestEventTime) => sessionActivity(configuration, local = false, localNewestEventTime)
            case SessionLogout(_)                         => sessionLogout  (configuration, local = false)
          }
        }
      }

      broadcastChannel
    }

    private def sessionMessageFromJson(jsonString: String): Either[Error, SessionMessage] = {
      // This could be done in a cleaner way by using circe's discriminator feature, but it is not available in JS.
      parser.parse(jsonString).flatMap { json =>
        json.as[SessionActivity] match {
          case Right(sessionActivity) => Right(sessionActivity)
          case Left(_)                => json.as[SessionLogout]
        }
      }
    }

    val sessionUpdateListeners: mutable.ListBuffer[SessionUpdateListener] = mutable.ListBuffer.empty

    private def fireSessionUpdate(sessionUpdate: SessionUpdate): Unit = sessionUpdateListeners.foreach(_(sessionUpdate))

    def sessionActivity(configuration: Configuration, local: Boolean, newestEventTime: Long): Unit = {
      // Once the session is expired, we don't do anything anymore
      if (sessionStatus != SessionExpired) {
        maxNewestEventTimeAmongAllPages = math.max(maxNewestEventTimeAmongAllPages, newestEventTime)

        val elapsedMillisSinceLastEvent = System.currentTimeMillis() - maxNewestEventTimeAmongAllPages

        // Update session status
        sessionStatus =
          if (elapsedMillisSinceLastEvent < configuration.sessionExpirationTriggerMillis)
            SessionActive
          else if (elapsedMillisSinceLastEvent > configuration.maxInactiveIntervalMillis)
            SessionExpired
          else
            SessionAboutToExpire

        if (sessionStatus == SessionAboutToExpire && configuration.sessionHeartbeatEnabled) {
          // Trigger session heartbeat. This is the main location where this is done. This could be implemented
          // as a listener as well.
          AjaxClient.sendHeartBeat()
        }

        if (local) {
          // Broadcast our local newestEventTime value to other pages
          val message = SessionActivity(configuration.sessionId, newestEventTime).asJson.noSpaces
          sessionActivityBroadcastChannel.postMessage(message)
        }

        // Reason for the margin: we prefer to display that the session is expired a bit sooner, while the session might
        // actually still be active, rather than a bit later, to avoid a situation where the user tries to renew an
        // inactive session.

        val approxSessionExpiredTimeMillis =
          math.max(
            maxNewestEventTimeAmongAllPages + configuration.maxInactiveIntervalMillis - configuration.sessionExpirationMarginMillis,
            System.currentTimeMillis()
          )

        fireSessionUpdate(SessionUpdate(
          sessionStatus                  = sessionStatus,
          sessionHeartbeatEnabled        = configuration.sessionHeartbeatEnabled,
          approxSessionExpiredTimeMillis = approxSessionExpiredTimeMillis
        ))
      }
    }

    def sessionExpiration(configuration: Configuration): Unit = {
      // Once the session is expired, we don't do anything anymore
      if (sessionStatus != SessionExpired) {
        sessionStatus = SessionExpired

        fireSessionUpdate(SessionUpdate(
          sessionStatus                  = sessionStatus,
          sessionHeartbeatEnabled        = configuration.sessionHeartbeatEnabled,
          approxSessionExpiredTimeMillis = System.currentTimeMillis()
        ))
      }
    }

    def sessionLogout(configuration: Configuration, local: Boolean): Unit = {
      // Once the session is expired, we don't do anything anymore
      if (sessionStatus != SessionExpired) {
        sessionStatus = SessionExpired

        if (local) {
          // Broadcast our local logout to other pages
          val message = SessionLogout(configuration.sessionId).asJson.noSpaces
          sessionActivityBroadcastChannel.postMessage(message)
        } else {
          // If the logout comes from another page, process the logout locally
          fireSessionUpdate(SessionUpdate(
            sessionStatus                  = sessionStatus,
            sessionHeartbeatEnabled        = configuration.sessionHeartbeatEnabled,
            approxSessionExpiredTimeMillis = System.currentTimeMillis()
          ))
        }
      }
    }
  }
}
