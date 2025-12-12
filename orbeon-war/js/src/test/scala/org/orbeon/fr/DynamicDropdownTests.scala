package org.orbeon.fr

import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.web.DomSupport.*
import org.scalatest.funspec.FixtureAsyncFunSpecLike

import scala.scalajs.js


trait DynamicDropdownTests {
  this: FixtureAsyncFunSpecLike with ClientTestSupport =>

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
