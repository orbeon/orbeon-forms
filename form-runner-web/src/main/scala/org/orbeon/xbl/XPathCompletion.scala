package org.orbeon.xbl

import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{AjaxClient, AjaxEvent, DocumentAPI, EventListenerSupport}
import org.scalajs.dom
import org.scalajs.dom.CustomEvent
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import scalatags.JsDom.all.*

import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.|


object XPathCompletion {

  XBL.declareCompanion("fb|xpath-completion", js.constructorOf[XPathCompletionCompanion])

  private class XPathCompletionCompanion(containerElem: dom.html.Element) extends XBLCompanion {

    private var pendingPromiseOpt: Option[Promise[Completions]] = None

    private object EventSupport extends EventListenerSupport

    override def init(): Unit = {

      val expander = containerElem.querySelector("text-expander")

      EventSupport.addListener(expander, "text-expander-change", (event: CustomEvent) => {
        val details = event.detail.asInstanceOf[TextExpanderChangeEventDetail]

        if (details.key == "$") {

          val p = Promise[Completions]()
          pendingPromiseOpt = Some(p)
          details.provide(p.future.toJSPromise)

          AjaxClient.fireEvent(
            AjaxEvent(
              eventName = "fb-query-completions",
              targetId  = this.containerElem.id,
              properties = Map(
                "text" -> details.text
              )
            )
          )
        }
      })

      EventSupport.addListener(expander, "text-expander-value", (event: CustomEvent) => {
        val details = event.detail.asInstanceOf[TextExpanderValueEventDetail]
        details.value = s"$$${details.item.getAttribute("data-value")}"
      })
    }

    override def destroy(): Unit =
      EventSupport.clearAllListeners()

    // Callback from the server
    def returnCompletions(jsonText: String): Unit = {
      pendingPromiseOpt.foreach { pendingPromise =>

        pendingPromiseOpt = None

        val array = js.JSON.parse(jsonText).asInstanceOf[js.Array[js.Dictionary[String]]]

        pendingPromise.success(
          new Completions {
            val matched: Boolean = array.nonEmpty
            val fragment: dom.html.Element =
              ul(
                `class` := "dropdown-menu"
              )(
                array.map { labelValue =>

                  val value = labelValue.get("v").getOrElse(throw new IllegalStateException) // value is mandatory
                  val label = labelValue.get("l").flatMap(_.trimAllToOpt).map(l => s""""$l" ($value)""").getOrElse(value)

                  li(
                    label,
                    role := "option",
                    data("value") := value
                  )
                }.toSeq: _*
              ).render
          }
        )
      }
    }
  }
}

// Facades
trait Completions extends js.Object {
  val matched: Boolean
  val fragment: dom.html.Element
}

trait TextExpanderChangeEventDetail extends CustomEvent {
  val key: String
  val text: String
  val provide: js.Function1[js.Promise[Completions], Unit]
}

trait TextExpanderValueEventDetail extends CustomEvent {
  var value: String
  val item: dom.html.Element
}

trait TextExpanderCommittedEventDetail extends CustomEvent {
  val input: dom.html.Input | dom.html.TextArea
}