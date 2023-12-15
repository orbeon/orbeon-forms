package org.orbeon.oxf.xforms.event

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.event.Dispatch.dispatchEvent
import org.orbeon.oxf.xforms.event.events.{XXFormsBindingErrorEvent, XXFormsValueChangedEvent, XXFormsXPathErrorEvent}
import org.orbeon.xforms.BindingErrorReason
import org.orbeon.xforms.XFormsCrossPlatformSupport

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.control.{ControlThrowable, NonFatal}


object EventCollector {

  type ErrorEventCollector = XFormsEvent => Unit

  private val MaxRecursion = 10

  class Buffer extends ErrorEventCollector {

    private val eventsToDispatch = mutable.ListBuffer[XFormsEvent]()

    def apply(event: XFormsEvent): Unit =
      eventsToDispatch += event

    def dispatchCollectedEvents(): Unit = {

      var totalEventsDispatched = 0

      @tailrec
      def dispatchCollectedEventsImpl(currentCollector: EventCollector.Buffer, level: Int): Unit = {
        totalEventsDispatched += currentCollector.eventsToDispatch.size
        val newCollector = new EventCollector.Buffer
        for (event <- currentCollector.eventsToDispatch)
          dispatchEvent(event, newCollector)
        if (newCollector.eventsToDispatch.nonEmpty && level < MaxRecursion)
          dispatchCollectedEventsImpl(newCollector, level + 1)
        else if (newCollector.eventsToDispatch.nonEmpty)
          throw new IllegalStateException(
            s"too much recursion while processing error events collected: ${totalEventsDispatched + newCollector.eventsToDispatch.size} events"
          )
      }

      dispatchCollectedEventsImpl(this, 0)
    }
  }

  def withBufferCollector[T](body: ErrorEventCollector => T): T = {
    val collector = new EventCollector.Buffer
    val result = body(collector)
    collector.dispatchCollectedEvents()
    result
  }

  def withFailFastCollector[T](
    contextMessage: => String,
    eventTarget   : XFormsEventTarget,
    collector     : ErrorEventCollector,
    default       : => T
  )(
    body          : ErrorEventCollector => T
  ): T = {

    val controlThrowable = new ControlThrowable {}

    class Wrapper(val t: Throwable) extends ControlThrowable

    val localCollector = new ErrorEventCollector {
      def apply(event: XFormsEvent): Unit = {
        try {
          collector(event)
        } catch {
          case NonFatal(t) =>
            throw new Wrapper(t)
        }
        throw controlThrowable
      }
    }
    try {
      body(localCollector)
    } catch {
      case t: Wrapper =>
        // Original collector opted to throw
        throw t.t
      case t if t eq controlThrowable => // `ControlThrowable` doesn't match `NonFatal()`!
        // We threw above after first call to original collector, which means that the first call was handled
        default
      case NonFatal(t) =>
        // Body threw
        collector(
          new XXFormsBindingErrorEvent( // Q: Does this make sense?
            target          = eventTarget,
            locationDataOpt = None,
            reason          = BindingErrorReason.Other(Option(XFormsCrossPlatformSupport.getRootThrowable(t).getMessage).getOrElse(contextMessage))
          )
        )
        default
    }
  }

  trait ThrowTrait extends ErrorEventCollector {
    def apply(event: XFormsEvent): Unit = {
      event match {
        case e: XXFormsBindingErrorEvent =>
          throw new OXFException(e.reasonOpt.map(_.message).getOrElse("unknown binding error"))
        case e: XXFormsXPathErrorEvent =>
          e.throwableOpt match {
            case Some(t) =>
              throw t
            case None    =>
              throw new OXFException(e.combinedMessage)
          }
        case e: XXFormsValueChangedEvent =>
          withBufferCollector(dispatchEvent(e, _))
        case _ =>
          // Callers should only pass the above events
          throw new IllegalStateException
      }
    }
  }

  object Throw    extends ThrowTrait
  object ToReview extends ThrowTrait // cases to review in the future

  object Ignore extends ErrorEventCollector {
    def apply(event: XFormsEvent): Unit = ()
  }
}