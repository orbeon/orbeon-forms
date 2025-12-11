package org.orbeon.fr

import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.web.DomSupport.*

import scala.async.Async.*
import scala.scalajs.js


class FormRunnerApiClientTest extends SharedDocker {

  describe("Form Runner API client tests") {
    it("must find form controls by name, set values, get values, and activate") { _ =>
      withFormReady(app = "tests", form = "control-names") { case FormRunnerWindow(_, formRunnerApi) =>
        async {

          val form = formRunnerApi.getForm(js.undefined)

          assert(form.findControlsByName("first-name").head.classList.contains("xforms-control"))

          assert(form.findControlsByName("last-name").head.classList.contains("xforms-control"))
          assert(form.findControlsByName("i-dont-exist").isEmpty)

          assert(form.findControlsByName("comments").length == 1)
          assert(form.findControlsByName("comments").forall(_.classList.contains("xforms-textarea")))

          await(form.setControlValue("comments", "Hello world!").map(_.toFuture).get)

          // Use same value to make sure that we get a resolving `Promise` in this case as well
          await(form.setControlValue("comments", "Hello world!").map(_.toFuture).get)
          await(form.setControlValue("comments", "Hello world!").map(_.toFuture).get)

          assert(form.getControlValue("comments").contains("Hello world!"))

          await(form.activateControl("add-comment").map(_.toFuture).get)

          // Only one "Add Comment" button
          assert(form.activateControl("add-comment", 1).isEmpty)

          assert(form.findControlsByName("comments").length == 2)
          assert(form.findControlsByName("comments").forall(_.classList.contains("xforms-textarea")))

          await(form.setControlValue("comments", "Hello world, again!", 1).map(_.toFuture).get)
          assert(form.getControlValue("comments", 1).contains("Hello world, again!"))

          assert(form.findControlsByName("comments").map(_.id).sameElements(List("message-section≡grid-3-grid≡comments-control⊙1", "message-section≡grid-3-grid≡comments-control⊙2")))

          // Single selection control with `Int`
          assert(form.getControlValue("topic").contains("0"))
          await(form.setControlValue("topic", 3).map(_.toFuture).get)
          assert(form.getControlValue("topic").contains("3"))
        }
      }
    }

    it("must pass the strict Wizard focus rules") { _ =>
      withFormReady(app = "tests", form = "wizard") { case FormRunnerWindow(_, formRunnerApi) =>
        async {

          val form = formRunnerApi.getForm(js.undefined)

          // Initial test of control visibility
          assert(form.findControlsByName("control-1").nonEmpty)
          assert(form.findControlsByName("control-2").isEmpty)

          // This must work because the field is visible
          await(formRunnerApi.wizard.focus("control-1").toFuture)

          // NOTE: `document.activeElement` seems to remain at the `body` element, so we cannot test on that. This might
          // be a JSDOM-specific issue.

          // This must fail because the field is not visible and not reachable
          await(formRunnerApi.wizard.focus("control-2").toFuture)

          // `control-2` is not visible
          assert(form.findControlsByName("control-2").isEmpty)

          // After filling `control-1`, `control-2` will be reachable
          form.setControlValue("control-1", "value-1")
          await(formRunnerApi.wizard.focus("control-2").toFuture)

          // `control-2` is visible
          assert(form.findControlsByName("control-2").nonEmpty)
        }
      }
    }
  }

  describe("The Dynamic Dropdown With Search control") {

    val ControlPrefix = "dynamic-dropdown-with-search"

    // The idea here is that the associated form uses a name for the form controls based on the specific control
    // settings. This way, we can try multiple combinations of settings and check that the controls behave as they
    // should. In doubt, take the form.xhtml and load it into Form Builder.
    sealed trait OpenClosed
    case object Open   extends OpenClosed
    case object Closed extends OpenClosed

    case class ControlSetup(
      openClosed              : OpenClosed,
      servicePerformsSearch   : Boolean,
      autoSelectUniqueChoice  : Boolean,
      storeLabel              : Boolean,
      serviceReturnsSingleItem: Boolean,
      readonly                : Boolean,
      hasInitialValueInData   : Boolean,
    ) {
      def controlName: String = {
        val parts =
          ControlPrefix                                                                                ::
          (if (openClosed == Open)     "open"        else "closed")                                    ::
          (if (servicePerformsSearch)  "service-yes" else "service-no")                                ::
          (if (autoSelectUniqueChoice) "auto-yes"    else "auto-no")                                   ::
          (if (storeLabel)             "label-yes"   else "label-no")                                  ::
          (autoSelectUniqueChoice list (if (serviceReturnsSingleItem) "service-1" else "service-all")) :::
          (hasInitialValueInData  list "value-yes")                                                    :::
          (if (readonly)               "readonly"    else "readwrite")                                 ::
          Nil

        parts.mkString("-")
      }
    }

    val controlSetups = List(
      ControlSetup(openClosed = Closed, servicePerformsSearch = false, autoSelectUniqueChoice = false, storeLabel = false, serviceReturnsSingleItem = false, readonly = false, hasInitialValueInData = false),
      ControlSetup(openClosed = Closed, servicePerformsSearch = false, autoSelectUniqueChoice = false, storeLabel = true,  serviceReturnsSingleItem = false, readonly = true,  hasInitialValueInData = false),
      ControlSetup(openClosed = Closed, servicePerformsSearch = false, autoSelectUniqueChoice = false, storeLabel = true,  serviceReturnsSingleItem = false, readonly = false, hasInitialValueInData = false),
      ControlSetup(openClosed = Closed, servicePerformsSearch = false, autoSelectUniqueChoice = true,  storeLabel = false, serviceReturnsSingleItem = false, readonly = true,  hasInitialValueInData = false),
      ControlSetup(openClosed = Closed, servicePerformsSearch = false, autoSelectUniqueChoice = true,  storeLabel = false, serviceReturnsSingleItem = false, readonly = false, hasInitialValueInData = false),
      ControlSetup(openClosed = Closed, servicePerformsSearch = false, autoSelectUniqueChoice = true,  storeLabel = false, serviceReturnsSingleItem = true,  readonly = true,  hasInitialValueInData = false),
      ControlSetup(openClosed = Closed, servicePerformsSearch = false, autoSelectUniqueChoice = true,  storeLabel = false, serviceReturnsSingleItem = true,  readonly = false, hasInitialValueInData = false),
      ControlSetup(openClosed = Closed, servicePerformsSearch = false, autoSelectUniqueChoice = true,  storeLabel = true,  serviceReturnsSingleItem = false, readonly = true,  hasInitialValueInData = false),
      ControlSetup(openClosed = Closed, servicePerformsSearch = false, autoSelectUniqueChoice = true,  storeLabel = true,  serviceReturnsSingleItem = false, readonly = false, hasInitialValueInData = false),
      ControlSetup(openClosed = Closed, servicePerformsSearch = false, autoSelectUniqueChoice = true,  storeLabel = true,  serviceReturnsSingleItem = true,  readonly = true,  hasInitialValueInData = false),
      ControlSetup(openClosed = Closed, servicePerformsSearch = false, autoSelectUniqueChoice = true,  storeLabel = true,  serviceReturnsSingleItem = true,  readonly = false, hasInitialValueInData = false),
      ControlSetup(openClosed = Closed, servicePerformsSearch = false, autoSelectUniqueChoice = true,  storeLabel = false, serviceReturnsSingleItem = false, readonly = false, hasInitialValueInData = true),
      ControlSetup(openClosed = Closed, servicePerformsSearch = false, autoSelectUniqueChoice = true,  storeLabel = false, serviceReturnsSingleItem = false, readonly = true,  hasInitialValueInData = true),
    )

    describe("Initial labels") {
      controlSetups.foreach { controlSetup =>
        val controlName = controlSetup.controlName

        val mustHaveAfghanistanDataLabel =
          ! controlSetup.hasInitialValueInData && controlSetup.autoSelectUniqueChoice && controlSetup.serviceReturnsSingleItem && ! controlSetup.readonly

        val mustHaveSwitzerlandDataLabel =
          controlSetup.hasInitialValueInData && controlSetup.autoSelectUniqueChoice && ! controlSetup.serviceReturnsSingleItem && ! controlSetup.storeLabel

        val labelToFind =
          if (mustHaveAfghanistanDataLabel)
            Some("Afghanistan")
          else if (mustHaveSwitzerlandDataLabel)
            Some("Switzerland")
          else
            None

        val labelAttMustHoldValue =
          labelToFind.isDefined && controlSetup.storeLabel && ! controlSetup.readonly

        it(s"for `$controlName`: must find initial label `$labelToFind`") { _ =>
          withFormReady(app = "tests", form = "databound-select1") { case FormRunnerWindow(_, formRunnerApi) =>

            val form = formRunnerApi.getForm(js.undefined)
            val controls = form.findControlsByName(controlName)
            assert(controls.nonEmpty)

            // Check the initial label value as set into the `data-label` attribute
            assert {
              labelToFind match {
                case Some(label) => controls.head.querySelectorT("[data-label]").dataset("label") == label
                case None        => controls.head.querySelectorT("[data-label]").dataset("label").isEmpty
              }
            }

            // Check the initial label value as set into the `label` attribute on the server, as reflected by the
            // `*-labelatt` output controls
            assert {
              labelToFind match {
                case Some(label) if labelAttMustHoldValue => form.getControlValue(s"$controlName-labelatt").contains(label)
                case _                                    => true
              }
            }
          }
        }
      }
    }
  }
}