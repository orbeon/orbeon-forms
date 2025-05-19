package org.orbeon.xbl

import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{AjaxClient, AjaxEvent, DocumentAPI, EventListenerSupport}
import org.scalajs.dom
import org.scalajs.dom.{CustomEvent, FocusEvent}
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
    private var textExpanderActive = false
    private var stoppedChangeEvent = false

    private object EventSupport extends EventListenerSupport

    override def init(): Unit = {

      val expander   = containerElem.querySelector("text-expander")
      val inputField = containerElem.querySelector("input, textarea")

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

      EventSupport.addListener(expander, "text-expander-activate"  , (_: CustomEvent) => { textExpanderActive = true  })
      EventSupport.addListener(expander, "text-expander-deactivate", (_: CustomEvent) => { textExpanderActive = false })

      // Prevent a `xxforms-blur` when users click on a completion
      EventSupport.addListener(inputField, "focusout",
        (event: FocusEvent) => {
          if (textExpanderActive) {
            event.stopPropagation()
          } else if (stoppedChangeEvent) {
            // If we stopped the change event earlier and the field value hasn't changed since then, the browser
            // dispatch a `change` event on focus out, so we need to dispatch one manually here.
            inputField.dispatchEvent(new dom.Event("change", new dom.EventInit {
              bubbles = true
              cancelable = true
            }))
            stoppedChangeEvent = false
          }
        },
        useCapture = true
      )

      // Prevent a `xxforms-value` when users click on a completion
      EventSupport.addListener(containerElem, "change",
        (event: dom.Event) => {
          if (textExpanderActive) {
            stoppedChangeEvent = true
            event.stopPropagation()
          }
        },
        useCapture = true
      )
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