package org.orbeon.xforms

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.orbeon.datatypes.BasicLocationData
import org.orbeon.dom.QName
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.MarkupUtils.MarkupStringOps
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.Constants.LhhacSeparator
import org.orbeon.xforms.XFormsUI.*
import org.orbeon.xforms.facade.{Controls, Utils, XBL}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import scalatags.JsDom
import scalatags.JsDom.all.*

import scala.concurrent.duration.{span as _, *}
import scala.scalajs.js
import scala.scalajs.js.Dynamic.global as g
import scala.scalajs.js.JSConverters.iterableOnceConvertible2JSRichIterableOnce
import scala.scalajs.js.{Dictionary, JSON, URIUtils, UndefOr, eval}
import scala.util.Try


object XFormsResponse {

  import Private.*
  import XFormsUI.logger

  def handleResponseDom(
    responseXML : dom.Document,
    formId      : String,
    ignoreErrors: Boolean
  ): Unit = {

    // Using `IO` as we have code which handles the processing of the response asynchronously, specifically on iOS when
    // the zoom level needs to be reset. It is unclear if this code is still useful. The JS code was using
    // `setTimeout()`, and not properly handling return values and errors. With `IO`, we can at least ensure some
    // sanity.
    implicit def runtime: IORuntime = IORuntime.global

    val actionsAndOrErrorsIos =
      responseXML.documentElement.childNodes.map {
        case actionElem: dom.Element if actionElem.localName == "action" => // only 1 possible `action` element
          handleActions(formId, actionElem)
            .map {
              case true =>
                // Display loading indicator when we go to another page.
                // Display it even if it was not displayed before as loading the page could take time.
                Page.loadingIndicator.showIfNotAlreadyVisible()
              case false =>
            }
        case errorsElem: dom.Element if errorsElem.localName == "errors" => // only 1 possible `errors` element (can be in addition to `action`)
          IO(handleErrorsElem(formId, ignoreErrors, errorsElem))
        case _: dom.Element => // should not happen
          IO.raiseError(throw new IllegalArgumentException)
        case _ =>
          IO.unit // ignore other nodes
      }

    actionsAndOrErrorsIos
      .toList
      .sequence
      .onError {
        case t: Throwable =>
          // Show dialog with error to the user, as they won't be able to continue using the UI anyway
          // Don't rethrow exception: we want the code that runs after the Ajax response is handled to run, so we have a
          // chance to recover from this error.
          IO(AjaxClient.logAndShowError(t, formId, ignoreErrors))
      }
      .unsafeToFuture()
  }

  // Returns an `IO` containing `true` if a submission or load causes navigation AND showing progress hasn't
  // been disabled
  private def handleActions(formId: String, actionElement: dom.Element): IO[Boolean] = {

    val controlValuesElements = actionElement.childrenWithLocalName("control-values").toList

    val responseDialogIdsToShowAsynchronously =
      (isIOS && getZoomLevel != 1.0)
        .flatList(findDialogsToShow(controlValuesElements).toList)

    def actionsIo: IO[Boolean] =
      IO.fromTry(
        Try {
          handleControlDetails(formId, controlValuesElements)
          handleOtherActions(formId, actionElement)
        }
      )

    val actionsMaybeWithDelayIo: IO[Boolean] =
      if (responseDialogIdsToShowAsynchronously.nonEmpty)
        for {
          _      <- IO(resetIOSZoom())
          _      <- IO.sleep(200.milliseconds)
          result <- actionsIo
        } yield
          result
      else
        actionsIo

    IO(handleDeleteRepeatElements(controlValuesElements))
      .flatMap(_ => actionsMaybeWithDelayIo)
  }

  // Returns `true` if a submission or load will cause navigation AND showing progress hasn't been disabled
  private def handleOtherActions(formId: String, actionElement: dom.Element): Boolean = {

    var newDynamicStateTriggersReplace = false

    actionElement.childrenT.foreach { childElem =>
      childElem.localName match {

        // Update repeat hierarchy
        case "repeat-hierarchy" =>
          InitSupport.processRepeatHierarchyUpdateForm(formId, childElem.textContent)

        // Change highlighted section in repeat
        case "repeat-indexes" =>

          val form = Page.getXFormsFormFromNamespacedIdOrThrow(formId)
          val repeatTreeParentToAllChildren = form.repeatTreeParentToAllChildren
          val repeatIndexes                 = form.repeatIndexes

          // Extract data from server response
          val newRepeatIndexes =
            js.Dictionary[Int](
              childElem
                .childrenT
                .collect {
                  case repeatIndexElem: dom.Element if repeatIndexElem.localName == "repeat-index" =>
                    val repeatId = repeatIndexElem.attValueOrThrow("id")
                    val newIndex = repeatIndexElem.attValueOrThrow("new-index").toInt
                    repeatId -> newIndex
                }
                .toList*
            )

          // For each repeat id that changes, see if all the children are also included in
          // `newRepeatIndexes`. If they are not, add an entry with the index unchanged.
          newRepeatIndexes.foreach { case (repeatId, _) =>
            repeatTreeParentToAllChildren.get(repeatId).getOrElse(js.Array()).foreach { child =>
              if (! newRepeatIndexes.contains(child))
                repeatIndexes.get(child) match {
                  case Some(index) => newRepeatIndexes(child) = index
                  case None        => newRepeatIndexes.remove(child)
                }
            }
          }

          def isEndElem(n: dom.Node): Boolean =
            n match {
              case e: dom.Element
                if e.classList.contains("xforms-repeat-delimiter") ||
                   e.classList.contains("xforms-repeat-begin-end") => true
              case _ => false
            }

          def getClassForRepeatId(repeatId: String): String = {
            var currentDepth = 1
            var currentRepeatId = repeatId
            var done = false
            while (! done) {
              form.repeatTreeChildToParent.get(currentRepeatId) match {
                case Some(id) =>
                  currentRepeatId = id
                  currentDepth    = if (currentDepth == 4) 1 else currentDepth + 1
                case None =>
                  done = true
              }
            }
            s"xforms-repeat-selected-item-$currentDepth"
          }

          // Unhighlight items at old indexes
          newRepeatIndexes.foreach { case (repeatId, _) =>
            repeatIndexes
              .get(repeatId)
              .filter(_ != 0)
              .foreach { oldIndex =>
                val oldItemDelimiter = Utils.findRepeatDelimiter(formId, repeatId, oldIndex)
                if (oldItemDelimiter != null) // https://github.com/orbeon/orbeon-forms/issues/3689
                  oldItemDelimiter.nextSiblings.takeWhile(! isEndElem(_)).foreach {
                    case elem: html.Element => elem.classList.remove(getClassForRepeatId(repeatId))
                    case _ =>
                  }
              }
          }

          // Store new indexes
          newRepeatIndexes.foreach { case (repeatId, newIndex) =>
            repeatIndexes(repeatId) = newIndex
          }

          // Highlight item at new index
          newRepeatIndexes.foreach { case (repeatId, newIndex) =>
            if (newIndex != 0) {
              val newItemDelimiter = Utils.findRepeatDelimiter(formId, repeatId, newIndex)
              if (newItemDelimiter != null) // https://github.com/orbeon/orbeon-forms/issues/3689
                newItemDelimiter.nextSiblings.takeWhile(! isEndElem(_)).foreach {
                  case elem: html.Element => elem.classList.add(getClassForRepeatId(repeatId))
                  case _ =>
                }
            }
          }

        case "poll" =>
          val delay = childElem.attValueOpt("delay").map(_.toDouble).getOrElse(0.0)
          AjaxClient.createDelayedPollEvent(delay, formId)

        // Submit form
        case "submission" =>
          handleSubmission(formId, childElem, () => newDynamicStateTriggersReplace = true)

        // Display modal message
        case "message" =>
          dom.window.alert(childElem.textContent)

        // Load another page
        case "load" =>
          val resource        = childElem.attValueOrThrow   ("resource")
          val showOpt         = childElem.attValueOpt       ("show")
          val targetOpt       = childElem.attValueOpt       ("target")
          val showProgressOpt = childElem.booleanAttValueOpt("show-progress")

          if (resource.startsWith("javascript:")) {
            val js = URIUtils.decodeURIComponent(resource.substring("javascript:".length))
            eval(js)
          } else if (showOpt.contains("replace")) {
            targetOpt match {
              case None =>
                // Display loading indicator unless the server tells us not to display it
                if (resource.head != '#' && ! showProgressOpt.contains(false))
                  newDynamicStateTriggersReplace = true
                try
                  dom.window.location.href = resource
                catch {
                  case _: Throwable =>
                    // NOP: This is to prevent the error "Unspecified error" in IE. This can
                    // happen when navigating away is cancelled by the user pressing cancel
                    // on a dialog displayed on unload.
                    // 2025-08-14: Does it happen in current browsers?
                }
              case Some(target) =>
                dom.window.open(resource, target, "noopener")
            }
          } else {
            dom.window.open(resource, "_blank", "noopener")
          }

        // Set focus to a control
        case "focus" =>
          Controls.setFocus(childElem.attValueOrThrow("control-id"))

        // Remove focus from a control
        case "blur" =>
          Controls.removeFocus(childElem.attValueOrThrow("control-id"))

        // Run JavaScript code
        case "script" =>
          handleScriptElem(formId, childElem)

        // Run JavaScript code
        case "callback" =>
          handleCallbackElem(formId, childElem)

        // Show help message for specified control
        case "help" =>
          Help.showHelp(dom.document.getElementByIdT(childElem.attValueOrThrow("control-id")))
        case _ =>
      }
    }
    newDynamicStateTriggersReplace
  }

  private def handleControlDetails(formId: String, controlValuesElements: Iterable[dom.Element]): Unit = {

    val recreatedInputs             = js.Dictionary.empty[html.Element]
    val controlsWithUpdatedItemsets = js.Dictionary.empty[Boolean]

    controlValuesElements.foreach { controlValuesElement =>
      controlValuesElement.childrenT.foreach { childElem =>
        childElem.localName match {
          case "control" =>
            handleControl(childElem, recreatedInputs, controlsWithUpdatedItemsets, formId)
          case "init" =>
            handleInit(childElem, controlsWithUpdatedItemsets)
          case "inner-html" =>
            handleInnerHtml(childElem)
          case "attribute" =>
            handleAttribute(childElem)
          case "text" =>
            handleText(childElem)
          case "repeat-iteration" =>
            handleRepeatIteration(childElem, formId)
          case "dialog" =>
            handleDialog(childElem, formId)
          case _ => // handled by `handleOtherActions()`
        }
      }
    }
  }

  private def findDialogsToShow(controlValuesElems: Iterable[dom.Element]): Iterator[String] =
    for {
      controlValuesElem <- controlValuesElems.iterator
      dialogElem        <- controlValuesElem.childrenWithLocalName("dialog")
      visibleValue      <- dialogElem.attValueOpt("visibility")
      if visibleValue == "visible"
      idValue           <- dialogElem.attValueOpt("id")
    } yield
      idValue

  private def handleScriptElem(formId: String, scriptElem: dom.Element): Unit = {

    val functionName  = scriptElem.attValueOrThrow("name")
    val targetId      = scriptElem.attValueOrThrow("target-id")
    val observerId    = scriptElem.attValueOrThrow("observer-id")
    val paramElements = scriptElem.childrenWithLocalName("param")

    val paramValues = paramElements map (_.textContent.asInstanceOf[js.Any])

    ServerAPI.callUserScript(formId, functionName, targetId, observerId, paramValues.toList*)
  }

  private def handleCallbackElem(formId: String, callbackElem: dom.Element): Unit =
    ServerAPI.callUserCallback(formId, callbackElem.attValueOrThrow("name"))

  private def handleDeleteRepeatElements(controlValuesElems: Iterable[dom.Element]): Unit =
    controlValuesElems.iterator foreach { controlValuesElem =>
      controlValuesElem.childrenWithLocalName("delete-repeat-elements").foreach { deleteElem =>

        // Extract data from server response
        val deleteId      = deleteElem.attValueOrThrow("id")
        val parentIndexes = deleteElem.attValueOrThrow("parent-indexes")
        val count         = deleteElem.attValueOrThrow("count").toInt

        // TODO: Server splits `deleteId`/`parentIndexes` and here we just put them back together!
        // TODO: `deleteId` is namespaced by the server; yet here we prepend `repeat-end`
        val repeatEnd =
          dom.document.getElementById("repeat-end-" + appendRepeatSuffix(deleteId, parentIndexes))

        // Find last element to delete
        var lastNodeToDelete: dom.Node = repeatEnd.previousSibling

        // Perform delete
        for (_ <- 0 until count) {
          var nestedRepeatLevel = 0
          var wasDelimiter = false
          while (! wasDelimiter) {
            lastNodeToDelete match {
              case lastElemToDelete: dom.Element =>

                val classList = lastElemToDelete.classList

                if (classList.contains("xforms-repeat-begin-end") && lastElemToDelete.id.startsWith("repeat-end-"))
                  nestedRepeatLevel += 1 // entering nested repeat
                else if (classList.contains("xforms-repeat-begin-end") && lastElemToDelete.id.startsWith("repeat-begin-"))
                  nestedRepeatLevel -=1 // exiting nested repeat
                else
                  wasDelimiter = nestedRepeatLevel == 0 && classList.contains("xforms-repeat-delimiter")

                // Since we are removing an element that can contain controls, remove the known server value
                lastElemToDelete.getElementsByClassName("xforms-control") foreach
                  (controlElem => ServerValueStore.remove(controlElem.id))

                // We also need to check this on the "root", as the `getElementsByClassName()` function only returns
                // sub-elements of the specified root and doesn't include the root in its search.
                if (lastElemToDelete.classList.contains("xforms-control"))
                  ServerValueStore.remove(lastElemToDelete.id)

              case _ => ()
            }
            val previous = lastNodeToDelete.previousSibling
            lastNodeToDelete.parentNode.removeChild(lastNodeToDelete)
            lastNodeToDelete = previous
          }
        }
      }
    }

  private def handleInit(elem: dom.Element, controlsWithUpdatedItemsets: js.Dictionary[Boolean]): Unit = {

    val controlId       = elem.attValueOrThrow("id")
    val documentElement = dom.document.getElementByIdT(controlId)
    val relevantOpt     = elem.booleanAttValueOpt("relevant")
    val readonlyOpt     = elem.booleanAttValueOpt("readonly")

    Option(XBL.instanceForControl(documentElement)) foreach { companionInstance =>

      val becomesRelevant    = relevantOpt.contains(true)
      val becomesNonRelevant = relevantOpt.contains(false)

      def callXFormsUpdateReadonlyIfNeeded(): Unit =
        if (readonlyOpt.isDefined && XFormsXbl.isObjectWithMethod(companionInstance, "xformsUpdateReadonly"))
          companionInstance.xformsUpdateReadonly(readonlyOpt.contains(true))

      if (becomesRelevant) {
        // NOTE: We don't need to call this right now, because  this is done via `instanceForControl`
        // the first time. `init()` is guaranteed to be called only once. Obviously this is a little
        // bit confusing.
        // if (_.isFunction(instance.init))
        //     instance.init();
        callXFormsUpdateReadonlyIfNeeded()
      } else if (becomesNonRelevant) {
        // We ignore `readonly` when we become non-relevant
        // Our component subclass's `destroy()` removes "xforms-xbl-object" data as well
        companionInstance.destroy()
      } else {
        // Stays relevant or non-relevant (but we should never be here if we are non-relevant)
        callXFormsUpdateReadonlyIfNeeded()
      }

      elem.childrenWithLocalName("value").foreach { childNode =>
        handleValue(childNode, controlId, recreatedInput = false, controlsWithUpdatedItemsets)
      }
    }
  }

  private def updateItemset(
    documentElement: html.Element,
    controlId      : String,
    itemsetTree    : js.Array[ItemsetItem],
    groupName      : Option[String]
  ): Unit =
    if (
      documentElement.classList.contains("xforms-select1-appearance-compact") ||
      documentElement.classList.contains("xforms-select-appearance-compact")  ||
      documentElement.classList.contains("xforms-select1-appearance-minimal")
    )
      updateSelectItemset(documentElement, itemsetTree)
    else
      updateFullAppearanceItemset(
        documentElement  = documentElement,
        into             = documentElement.querySelectorT("span.xforms-items"),
        afterOpt         = None,
        controlId        = controlId,
        itemsetTree      = itemsetTree,
        isSelect         = documentElement.classList.contains("xforms-select"),
        groupNameOpt     = groupName,
        clearChildrenOpt = Some(_.replaceChildren())
      )

  private def handleControl(
    elem                       : dom.Element,
    recreatedInputs            : js.Dictionary[html.Element],
    controlsWithUpdatedItemsets: js.Dictionary[Boolean],
    formId                     : String
  ): Unit = {

    val controlId           = elem.attValueOrThrow("id")
    val staticReadonlyOpt   = elem.attValueOpt("static")
    val relevantOpt         = elem.booleanAttValueOpt("relevant")
    val readonlyOpt         = elem.booleanAttValueOpt("readonly")
    val requiredOpt         = elem.booleanAttValueOpt("required")
    val classesOpt          = elem.attValueOpt("class")
    val newLevelOpt         = elem.attValueOpt("level")
    val progressStateOpt    = elem.attValueOpt("progress-state")
    val progressReceivedOpt = elem.attValueOpt("progress-received")
    val progressExpectedOpt = elem.attValueOpt("progress-expected")
    val newSchemaTypeOpt    = elem.attValueOpt("type")
    val newVisitedOpt       = elem.booleanAttValueOpt("visited")

    var documentElement = dom.document.getElementByIdT(controlId)

    // Done to fix #2935; can be removed when we have taken care of #2940
    if (documentElement == null && (controlId  == "fb-static-upload-empty" || controlId == "fb-static-upload-non-empty"))
      return

    if (documentElement == null) {
      documentElement = dom.document.getElementByIdT(s"group-begin-$controlId")
      if (documentElement == null) {
        logger.error(s"Can't find element or iteration with ID '$controlId'")
        // TODO: throw?
      }
    }

    val isLeafControl = documentElement.classList.contains("xforms-control")

    // TODO: 2024-05-27: Unsure if this should ever kick in or if the logic is up to date!
    var isStaticReadonly = documentElement.classList.contains("xforms-static")
    val newDocumentElement =
      maybeMigrateToStatic(
        documentElement,
        controlId,
        staticReadonlyOpt,
        isLeafControl
      )
    if (newDocumentElement ne documentElement) {
      documentElement = newDocumentElement
      isStaticReadonly = true
    }

    // We update the relevance and readonly before we update the value. If we don't, updating the value
    // can fail on IE in some cases. (The details about this issue have been lost.)

    // Handle becoming relevant
    if (relevantOpt.contains(true))
      Controls.setRelevant(documentElement, relevant = true)

    val recreatedInput = maybeUpdateSchemaType(documentElement, controlId, newSchemaTypeOpt, formId)
    if (recreatedInput)
      recreatedInputs(controlId) = documentElement

    // Handle required
    requiredOpt.foreach {
      case true  => documentElement.classList.add("xforms-required")
      case false => documentElement.classList.remove("xforms-required")
    }

    // Handle readonly
    if (! isStaticReadonly)
      readonlyOpt
        .foreach(Controls.setReadonly(documentElement, _))

    // Handle updates to custom classes
    classesOpt.foreach { classes =>
      classes.splitTo[List]().foreach { currentClass =>
        if (currentClass.startsWith("-"))
          documentElement.classList.remove(currentClass.substring(1))
        else {
          // '+' is optional
          documentElement.classList.add(
            if (currentClass.charAt(0) == '+')
              currentClass.substring(1)
            else
              currentClass
          )
        }
      }
    }

    // Update the `required-empty`/`required-full` even if the required has not changed or
    // is not specified as the value may have changed
    if (! isStaticReadonly)
      elem.attValueOpt("empty")
        .foreach(Controls.updateRequiredEmpty(documentElement, _))

    // Custom attributes on controls
    if (isLeafControl)
      updateCustomAttributes(documentElement, elem, requiredOpt, newVisitedOpt, newLevelOpt)

    updateControlAttributes(
      documentElement,
      elem,
      newLevelOpt,
      progressStateOpt,
      progressReceivedOpt,
      progressExpectedOpt,
      newVisitedOpt,
      controlId,
      recreatedInput,
      controlsWithUpdatedItemsets,
      relevantOpt
    )
  }

  private val IterationSuffix = "~iteration"

  // TODO: check uses of `dom.document.getElementById()`: should we not checked under the current `<form>` only?
  private def handleInnerHtml(elem: dom.Element): Unit = {

    val innerHTML    = elem.firstChildWithLocalNameOrThrow("value").textContent
    val initValue    = elem.firstChildWithLocalNameOpt("init").map(_.textContent)
    val destroyValue = elem.firstChildWithLocalNameOpt("destroy").map(_.textContent)
    val controlId    = elem.attValueOrThrow("id")

    val controlXFormsId = XFormsId.fromEffectiveId(controlId)

    val prefixedId = controlXFormsId.toPrefixedId

    destroyValue
      .foreach(InitSupport.destroyJavaScriptControlsFromSerialized)

    if (prefixedId.endsWith(IterationSuffix)) {
      // The HTML is the content of a repeat iteration

      val repeatPrefixedId = prefixedId.substring(0, prefixedId.length - IterationSuffix.length)

      val parentRepeatIndexes = controlXFormsId.iterations.init

      // NOTE: New iterations are added always at the end so we don't yet need the value of the current index.
      // This will be either the separator before the template, or the end element.
      val afterInsertionPoint =
        dom.document.getElementByIdT("repeat-end-" + appendRepeatSuffix(repeatPrefixedId, parentRepeatIndexes.mkString("-")))

      val tagName = afterInsertionPoint.tagName

      afterInsertionPoint.insertAdjacentHTML("beforebegin", s"""<$tagName class="xforms-repeat-delimiter"></$tagName>$innerHTML""")

    } else {

      dom.document.getElementByIdOpt(controlId) match {
        case Some(documentElement) =>
          documentElement.replaceChildren() // probably obsolete
          documentElement.innerHTML = innerHTML
        case None =>

          def insertBetweenDelimiters(prefix: String): Boolean =
            dom.document.getElementByIdOpt(prefix + "-begin-" + controlId) match {
              case Some(delimiterBegin) =>

                val EndMarkerId = prefix + "-end-" + controlId

                def nodeMatches(e: dom.Node): Boolean =
                  e match {
                    case e: html.Element => e.id != EndMarkerId
                    case _               => true
                  }

                while (nodeMatches(delimiterBegin.nextSibling))
                  delimiterBegin.parentNode.removeChild(delimiterBegin.nextSibling)

                delimiterBegin.insertAdjacentHTML("afterend", innerHTML)
                true
              case None =>
                false
            }

          insertBetweenDelimiters("group")  ||
          insertBetweenDelimiters("repeat") ||
          insertBetweenDelimiters("xforms-case")
        }
    }

    initValue
      .foreach(InitSupport.initializeJavaScriptControlsFromSerialized)

    // If the element that had the focus is not in the document anymore, it might have been replaced by
    // setting the innerHTML, so set focus it again
    if (
      ! dom.document.contains(Globals.currentFocusControlElement) &&
        dom.document.getElementByIdOpt(Globals.currentFocusControlId).isDefined
    ) Controls.setFocus(Globals.currentFocusControlId)
  }

  private def updateControlAttributes(
    documentElement            : html.Element,
    elem                       : dom.Element,
    newLevelOpt                : Option[String],
    progressStateOpt           : Option[String],
    progressReceivedOpt        : Option[String],
    progressExpectedOpt        : Option[String],
    newVisitedOpt              : Option[Boolean],
    controlId                  : String,
    recreatedInput             : Boolean,
    controlsWithUpdatedItemsets: js.Dictionary[Boolean],
    relevantOpt                : Option[Boolean]
  ): Unit = {

    // Store new label message in control attribute
    elem.attValueOpt("label") foreach { newLabel =>
      Controls.setLabelMessage(documentElement, newLabel)
    }

    // Store new hint message in control attribute
    // See also https://github.com/orbeon/orbeon-forms/issues/3561
    elem.attValueOpt("hint") match {
      case Some(newHint) =>
        Controls.setHintMessage(documentElement, newHint)
      case None =>
        elem.attValueOpt("title") foreach { newTitle =>
          Controls.setHintMessage(documentElement, newTitle)
        }
    }

    // Store new help message in control attribute
    elem.attValueOpt("help") foreach { newHelp =>
      Controls.setHelpMessage(documentElement, newHelp)
    }

    // Store new alert message in control attribute
    elem.attValueOpt("alert") foreach { newAlert =>
      Controls.setAlertMessage(documentElement, newAlert)
    }

    // Store validity, label, hint, help in element
    newLevelOpt
      .foreach(Controls.setConstraintLevel(documentElement, _))

    // Handle progress for upload controls
    // The attribute `progress-expected="…"` could be missing, even if we have a
    // progress-received="50591"` (see `expectedSize` which is an `Option` in `UploadProgress`),
    // in which case we don't have a "progress" to report to the progress bar.
    (progressStateOpt, progressExpectedOpt) match {
      case (Some(progressState), Some(progressExpected)) if progressState.nonAllBlank && progressExpected.nonAllBlank =>
        Page.getUploadControl(documentElement).progress(
          progressState,
          progressReceivedOpt.getOrElse(throw new IllegalArgumentException).toLong,
          progressExpected.toLong
        )
      case _ =>
    }

    // Handle visited flag
    newVisitedOpt
      .foreach(Controls.updateVisited(documentElement, _))

    // Nested elements
    elem.children.foreach { childNode =>
      childNode.localName match {
        case "itemset" => handleItemset(childNode, controlId, controlsWithUpdatedItemsets)
        case "case"    => handleSwitchCase(childNode)
        case _         =>
      }
    }

    // Must handle `value` after `itemset`
    handleValues(elem, controlId, recreatedInput, controlsWithUpdatedItemsets)

    // Handle becoming non-relevant after everything so that XBL companion class instances
    // are nulled and can be garbage-collected
    if (relevantOpt.contains(false))
      Controls.setRelevant(documentElement, relevant = false)
  }

  private def handleItemset(elem: dom.Element, controlId: String, controlsWithUpdatedItemsets : js.Dictionary[Boolean]): Unit = {

    val itemsetTree     = Option(JSON.parse(elem.textContent).asInstanceOf[js.Array[ItemsetItem]]).getOrElse(js.Array())
    val documentElement = dom.document.getElementByIdT(controlId)
    val groupNameOpt    = elem.attValueOpt("group")

    controlsWithUpdatedItemsets(controlId) = true

    updateItemset(documentElement, controlId, itemsetTree, groupNameOpt)

    // Call legacy global custom listener if any
    if (js.typeOf(g.xformsItemsetUpdatedListener) != "undefined")
      g.xformsItemsetUpdatedListener.asInstanceOf[js.Function2[String, js.Array[ItemsetItem], js.Any]]
        .apply(controlId, itemsetTree)
  }

  private def handleSwitchCase(elem: dom.Element): Unit = {
    val id      = elem.attValueOrThrow("id")
    val visible = elem.attValueOrThrow("visibility") == "visible"
    Controls.toggleCase(id, visible)
  }

  private def maybeMigrateToStatic(
    documentElement  : html.Element,
    controlId        : String,
    staticReadonlyOpt: Option[String],
    isLeafControl    : Boolean
  ): html.Element =
    if (! documentElement.classList.contains("xforms-static") && staticReadonlyOpt.exists(_.toBoolean)) {
      if (isLeafControl) {
        val parentElement = documentElement.parentElement
        val newDocumentElement = dom.document.createElementT("span")
        newDocumentElement.id = controlId

        newDocumentElement.classList = documentElement.classList
        newDocumentElement.classList.add("xforms-static")
        parentElement.replaceChild(newDocumentElement, documentElement)

        Controls.getControlLHHA(newDocumentElement, "alert")
          .foreach(parentElement.removeChild)

        Controls.getControlLHHA(newDocumentElement, "hint")
          .foreach(parentElement.removeChild)

          newDocumentElement
      } else {
        documentElement.classList.add("xforms-static")
        documentElement
      }
    } else {
      documentElement
    }

  private val AriaControlClasses = Set(
    "xforms-input",
    "xforms-textarea",
    "xforms-secret",
    "xforms-select1-appearance-compact", // .xforms-select1
    "xforms-select1-appearance-minimal"  // .xforms-select1
  )

  private def updateCustomAttributes(
    documentElement: html.Element,
    elem           : dom.Element,
    requiredOpt    : Option[Boolean],
    newVisitedOpt  : Option[Boolean],
    newLevelOpt    : Option[String],
  ): Unit = {
    if (AriaControlClasses.exists(documentElement.classList.contains)) {
      documentElement.querySelectorOpt("input, textarea, select").foreach { firstInput =>

        requiredOpt.foreach {
          case true  => firstInput.setAttribute("aria-required", true.toString)
          case false => firstInput.removeAttribute("aria-required")
        }

        val visited = newVisitedOpt.getOrElse(documentElement.classList.contains("xforms-visited"))
        val invalid = newLevelOpt.map(_ == "error").getOrElse(documentElement.classList.contains("xforms-invalid"))

        if (invalid && visited)
          firstInput.setAttribute("aria-invalid", true.toString)
        else
          firstInput.removeAttribute("aria-invalid")
      }
    }

    if (documentElement.classList.contains("xforms-upload")) {
      // Additional attributes for xf:upload
      // <xxf:control id="xforms-control-id"
      //    state="empty|file"
      //    accept=".txt"
      //    filename="filename.txt" mediatype="text/plain" size="23kb"/>

      val fileNameSpan  = documentElement.querySelector(".xforms-upload-filename")
      val mediatypeSpan = documentElement.querySelector(".xforms-upload-mediatype")
      val sizeSpan      = documentElement.querySelector(".xforms-upload-size")
      val uploadSelect  = documentElement.querySelector(".xforms-upload-select").asInstanceOf[html.Input]

      // Set values in DOM
      val upload = Page.getUploadControl(documentElement)

      val stateOpt     = elem.attValueOpt("state")
      val fileNameOpt  = elem.attValueOpt("filename")
      val mediatypeOpt = elem.attValueOpt("mediatype")
      val sizeOpt      = elem.attValueOpt("size")
      val acceptOpt    = elem.attValueOpt("accept")

      stateOpt.foreach(upload.setState)
      fileNameOpt .foreach(fileNameSpan .textContent = _)
      mediatypeOpt.foreach(mediatypeSpan.textContent = _)
      sizeOpt     .foreach(sizeSpan     .textContent = _)
      // NOTE: Server can send a space-separated value but `accept` expects a comma-separated value
      acceptOpt.foreach(a => uploadSelect.accept = a.splitTo[List]().mkString(","))

    } else if (documentElement.classList.contains("xforms-output") || documentElement.classList.contains("xforms-static")) {

      elem.attValueOpt("alt").foreach { alt =>
        if (documentElement.classList.contains("xforms-mediatype-image")) {
          val img = documentElement.querySelector(":scope > img").asInstanceOf[html.Image]
          img.alt = alt
        }
      }

      if (documentElement.classList.contains("xforms-output-appearance-xxforms-download")) {
        elem.attValueOpt("download").foreach { download =>
          val aElem = documentElement.querySelector(".xforms-output-output").asInstanceOf[html.Anchor]
          aElem.asInstanceOf[js.Dynamic].download = download
        }
      }
    } else if (documentElement.classList.contains("xforms-trigger") || documentElement.classList.contains("xforms-submit")) {
        // It isn't a control that can hold a value (e.g. trigger) and there is no point in trying to update it
        // NOP
    } else if (documentElement.classList.contains("xforms-input") || documentElement.classList.contains("xforms-secret")) {
        // Additional attributes for xf:input and xf:secret

      val inputSizeOpt         = elem.attValueOpt("size")
      val maxlengthOpt         = elem.attValueOpt("maxlength")
      val inputAutocompleteOpt = elem.attValueOpt("autocomplete")

      // NOTE: Below, we consider an empty value as an indication to remove the attribute. May or may not be
      // the best thing to do.
      // 2025-08-15: We used to remove the attribute to indicate no limit, but this is probably not correct: if you
      // remove the attribute, then set the property, then remove the attribute again, the property is not reset. It is
      // better to set the property to its documented (MDN) default.

      val input = documentElement.querySelector("input").asInstanceOf[html.Input]

      inputSizeOpt.foreach { size =>
        if (size.isEmpty)
          input.size = 20
        else
          input.size = size.toInt
      }

      maxlengthOpt.foreach { maxlength =>
        if (maxlength.isEmpty)
          input.maxLength = -1
        else
          input.maxLength = maxlength.toInt
      }

      inputAutocompleteOpt.foreach { inputAutocomplete =>
        if (inputAutocomplete.isEmpty)
          input.autocomplete = ""
        else
          input.autocomplete = inputAutocomplete
      }

    } else if (documentElement.classList.contains("xforms-textarea")) {
      // Additional attributes for xf:textarea

      val maxlengthOpt    = elem.attValueOpt("maxlength")
      val textareaColsOpt = elem.attValueOpt("cols")
      val textareaRowsOpt = elem.attValueOpt("rows")

      val textarea = documentElement.querySelector("textarea").asInstanceOf[html.TextArea]

      // NOTE: Below, we consider an empty value as an indication to remove the attribute. May or may not be
      // the best thing to do.
      maxlengthOpt.foreach { maxlength =>
        if (maxlength.isEmpty)
          textarea.maxLength = -1
        else
          textarea.maxLength = maxlength.toInt
      }

      textareaColsOpt.foreach { textareaCols =>
        if (textareaCols.isEmpty)
          textarea.cols = 20
        else
          textarea.cols = textareaCols.toInt
      }

      textareaRowsOpt.foreach { textareaRows =>
        if (textareaRows.isEmpty)
          textarea.rows = 2
        else
          textarea.rows = textareaRows.toInt
      }
    }
  }

  private val TypePrefix = "xforms-type-"

  private val TypeCssClassToInputType: Map[String, InputType] = Map(
    s"${TypePrefix}boolean" -> InputType.Boolean,
    s"${TypePrefix}string"  -> InputType.String
  )

  private val LhhaClasses = List(
    "xforms-label",
    "xforms-help",
    "xforms-hint",
    "xforms-alert"
  )

  private sealed trait InputType
  private object InputType {
    case object Boolean extends InputType
    case object String  extends InputType
  }

  private def maybeUpdateSchemaType(
    documentElement: html.Element,
    controlId      : String,
    newSchemaType  : Option[String],
    formId         : String,
  ): Boolean =
    newSchemaType match  {
        case Some(newSchemaType) =>

        val newSchemaTypeQName =
          newSchemaType.trimAllToOpt match {
            case None    => Names.XsString
            case Some(s) => QName.fromClarkName(s).getOrElse(throw new IllegalArgumentException(s"Invalid schema type: `$s`"))
          }

        lazy val newInputType =
          newSchemaTypeQName match {
            case Names.XsBoolean | Names.XfBoolean => InputType.Boolean
            case _                                 => InputType.String
          }

        lazy val existingInputType =
          TypeCssClassToInputType
            .collectFirst { case (className, inputType) if documentElement.classList.contains(className) => inputType }
            .getOrElse(InputType.String)

        val mustUpdateInputType =
          documentElement.classList.contains("xforms-input") && existingInputType != newInputType

        if (mustUpdateInputType)
          updateInputType(documentElement, controlId, newInputType, formId)

        // Remove existing CSS `xforms-type-*` classes
        documentElement
          .className
          .splitTo[List]()
          .filter(_.startsWith(TypePrefix))
          .foreach(documentElement.classList.remove)

        // Add new CSS `xforms-type-*` class
        newSchemaTypeQName match { case qName @ QName(localName, _) =>
          val isBuiltIn = Namespaces.isForBuiltinType(qName)
          val newClass = s"$TypePrefix${if (isBuiltIn) "" else "custom-"}$localName"
          documentElement.classList.add(newClass)
        }

        mustUpdateInputType
      case None =>
        false
    }

  private def updateInputType(
    documentElement: html.Element,
    controlId      : String,
    newInputType   : InputType,
    formId         : String
  ): Unit = {

    // Find the position of the last LHHA before the control "actual content"
    // A value of -1 means that the content came before any label
    val lastLhhaPosition =
      documentElement.children.segmentLength(e => LhhaClasses.exists(e.classList.contains)) - 1

    // Remove all elements that are not labels
    documentElement.children.filter(e => ! LhhaClasses.exists(e.classList.contains))
      .foreach(documentElement.removeChild)

    val inputLabelElement = Controls.getControlLHHA(documentElement, "label").asInstanceOf[html.Label]

    newInputType match {
      case InputType.Boolean =>
        updateBooleanInput(documentElement, controlId, lastLhhaPosition, inputLabelElement)
      case InputType.String =>
        updateStringInput(documentElement, controlId, formId, lastLhhaPosition, inputLabelElement)
    }
  }

  private def updateStringInput(
    documentElement  : html.Element,
    controlId        : String,
    formId           : String,
    lastLhhaPosition : Int,
    inputLabelElement: html.Label,
  ): Unit = {

    def insertIntoDocument(nodes: List[html.Element], lastLhhaPosition: Int): Unit = {
      val childElements = documentElement.children
      if (childElements.isEmpty)
        documentElement.append(nodes*)
      else if (lastLhhaPosition == -1)
        documentElement.prepend(nodes*)
      else
        childElements(lastLhhaPosition).after(nodes*)
    }

    def createNewInputElem(typeClassName: String): html.Input = {
      val newInputElementId = XFormsId.appendToEffectiveId(controlId, "$xforms-input-1")
      input(
        tpe := "text",
        cls := s"xforms-input-input $typeClassName",
        id  := newInputElementId,
        name := Page.deNamespaceIdIfNeeded(formId, newInputElementId) // in portlet mode, name is not prefixed
      ).render
    }

    val newStringInput = createNewInputElem("xforms-type-string")
    insertIntoDocument(List(newStringInput), lastLhhaPosition)
    inputLabelElement.htmlFor = newStringInput.id
  }

  private def updateBooleanInput(
    documentElement  : html.Element,
    controlId        : String,
    lastLabelPosition: Int,
    inputLabelElement: html.Label
  ): Unit = {
    updateFullAppearanceItemset(
      documentElement  = documentElement,
      into             = documentElement,
      afterOpt         = (lastLabelPosition >= 0).flatOption((documentElement.children(lastLabelPosition).asInstanceOf[html.Element]: js.UndefOr[html.Element]).toOption),
      controlId        = controlId,
      itemsetTree      = js.Array(
        new ItemsetItem {
          val attributes: UndefOr[Dictionary[String]]    = js.undefined // js.UndefOr[js.Dictionary[String]]
          val children  : UndefOr[js.Array[ItemsetItem]] = js.undefined
          val label     : UndefOr[String]                = js.undefined
          val value     : UndefOr[String]                = true.toString
          val help      : UndefOr[String]                = js.undefined
          val hint      : UndefOr[String]                = js.undefined
        }
      ),
      isSelect         = true,
      groupNameOpt     = None,
      clearChildrenOpt = None // items have already been removed
    )

    Option(inputLabelElement)
      .foreach(_.htmlFor = XFormsId.appendToEffectiveId(controlId, LhhacSeparator + "e0"))
  }

  // TODO:
  //  - Resolve nested `optgroup`s.
  //  - use direct serialization/deserialization instead of custom JSON.
  //    See `XFormsSelect1Control.outputAjaxDiffUseClientValue()`.

  private def updateSelectItemset(
    documentElement: html.Element,
    itemsetTree    : js.Array[ItemsetItem]
  ): Unit =
    documentElement.queryNestedElems[html.Select]("select", includeSelf = false).headOption match {
      case Some(select) =>

        val selectedValues =
          select.options.filter(_.selected).map(_.value)

        def generateItem(itemElement: ItemsetItem): Option[html.Element] = {

          val classOpt = itemElement.attributes.toOption.flatMap(_.get("class"))

          (itemElement.children.toOption, itemElement.value.toOption) match {
            case (None, Some(value)) =>
              Some(
                dom.document.createOptionElement.kestrel { option =>
                  option.value     = value
                  option.selected  = itemElement.value.exists(selectedValues.contains)
                  option.innerHTML = itemElement.label.getOrElse("")
                  classOpt.foreach(option.className = _)
                }
              )
            case (Some(children), None) =>
              Some(
                dom.document.createOptGroupElement.kestrel { optgroup =>
                  optgroup.label = itemElement.label.getOrElse("")
                  classOpt.foreach(optgroup.className = _)
                  optgroup.replaceChildren(children.toList.flatMap(generateItem)*)
                }
              )
            case (Some(_), Some(_)) | (None, None) =>
              // This should not happen
              logger.error(s"Invalid itemset item has children and a value")
              dom.console.log(itemElement)
              None
          }
        }

        select.replaceChildren(itemsetTree.toList.flatMap(generateItem)*)

      case _ =>
        // This should not happen but if it does we'd like to know about it without entirely stopping the
        // update process so we give the user a chance to recover the form.
        // TODO: Generalize. 2025-08-19: Not sure what that was meant to mean.
        logger.error(s"`<select>` element not found when attempting to update itemset")
    }

  // Concrete scenarios:
  //
  // - insert into the `span.xforms-items` element (replacing all existing content)
  // - insert into the control's container element at the beginning
  // - insert into the control's container element after a given element
  private def updateFullAppearanceItemset(
    documentElement : html.Element,
    into            : html.Element, // this must be the container of the item elements
    afterOpt        : Option[html.Element],
    controlId       : String,
    itemsetTree     : js.Array[ItemsetItem],
    isSelect        : Boolean,
    groupNameOpt    : Option[String],
    clearChildrenOpt: Option[html.Element => Unit]
  ): Unit = {

    // - https://github.com/orbeon/orbeon-forms/issues/5595
    // - https://github.com/orbeon/orbeon-forms/issues/5427
    val isReadonly = XFormsControls.isReadonly(documentElement)

    // Remember currently-checked values
    val checkedValues: Set[String] =
      into.children.flatMap { elem =>
        Option(elem.querySelector("input[type = checkbox], input[type = radio]").asInstanceOf[dom.html.Input])
          .filter(_.checked)
          .map(input => input.value)
      }.toSet

    // Clear existing items if needed
    clearChildrenOpt.foreach(_.apply(into))

    val domParser = new dom.DOMParser

    val itemsToInsertIt: Iterator[html.Span] =
      itemsetTree.iterator.zipWithIndex.map { case (itemElement, itemIndex) =>

        val classOpt = itemElement.attributes.toOption.flatMap(_.get("class"))

        val itemLabelNodesOpt =
          itemElement.label.toOption.map { label =>
            domParser
              .parseFromString(label, dom.MIMEType.`text/html`)
              .querySelector("html > body")
              .childNodes
          }

        val helpOpt: Option[JsDom.TypedTag[html.Span]] = itemElement.help.toOption.filter(_.nonAllBlank).map { helpString =>
          span(cls := "xforms-help")(helpString)
        }

        val hintOpt: Option[JsDom.TypedTag[html.Span]] = itemElement.hint.toOption.filter(_.nonAllBlank).map { hintString =>
          span(cls := "xforms-hint") {
            domParser
              .parseFromString(hintString, dom.MIMEType.`text/html`)
              .querySelector("html > body")
              .childNodes
              .toList
          }
        }

        val selectedClass =
          if (itemElement.value.exists(checkedValues)) "xforms-selected"
          else                                         "xforms-deselected"
        span(cls := (selectedClass :: classOpt.toList mkString " ")) {

          def createInput: JsDom.TypedTag[html.Input] =
            input(
              id       := XFormsId.appendToEffectiveId(controlId, Constants.LhhacSeparator + "e" + itemIndex),
              tpe      := (if (isSelect) "checkbox" else "radio"),
              name     := groupNameOpt.getOrElse(controlId),
              value    := itemElement.value
            )(
              itemElement.value.exists(checkedValues).option(checked),
              isReadonly                             .option(disabled)
            )

          itemLabelNodesOpt match {
            case None =>
              createInput
            case Some(itemLabelNodes) =>
              label(cls := (if (isSelect) "checkbox" else "radio"))(
                createInput
              )(
                span(hintOpt.isDefined.option(cls := "xforms-hint-region"))(itemLabelNodes.toList)
              )(
                helpOpt
              )(
                hintOpt
              )
          }
        }.render
      }

    val itemsToInsertAsList = itemsToInsertIt.toList

    afterOpt match {
      case None if into.firstElementChild eq null =>
        into.append(itemsToInsertAsList*)
      case None =>
        into.firstElementChild.prepend(itemsToInsertAsList*)
      case Some(after) =>
        after.after(itemsToInsertAsList*)
    }
  }

  private def handleValues(
    controlElem                 : dom.Element,
    controlId                   : String,
    recreatedInput              : Boolean,
    controlsWithUpdatedItemsets : js.Dictionary[Boolean]
  ): Unit =
    controlElem
      .childrenWithLocalName("value")
      .foreach(handleValue(_, controlId, recreatedInput, controlsWithUpdatedItemsets))

  private def handleDialog(
    controlElem : dom.Element,
    formId      : String
  ): Unit = {

    val id            = controlElem.id
    val visible       = controlElem.getAttribute("visibility") == "visible"
    val neighborIdOpt = controlElem.getAttribute("neighbor").trimAllToOpt

    if (visible)
      showDialog(id, neighborIdOpt, "handleDialog")
    else
      hideDialog(id, formId)
  }

  private def handleAttribute(controlElem : dom.Element): Unit = {

    val newAttributeValue = controlElem.textContent
    val forAttribute      = controlElem.getAttribute("for")
    val nameAttribute     = controlElem.getAttribute("name")
    val htmlElement       = dom.document.getElementById(forAttribute)

    if (htmlElement != null)
      htmlElement.setAttribute(nameAttribute, newAttributeValue) // use case: xh:html/@lang but HTML fragment produced
  }

  private def handleText(controlElem: dom.Element): Unit = {

    val newTextValue = controlElem.textContent
    val forAttribute = controlElem.getAttribute("for")
    val htmlElement = dom.document.getElementById(forAttribute)

    if (htmlElement != null && htmlElement.tagName.toLowerCase() == "title")
      dom.document.title = newTextValue
  }

  private def handleRepeatIteration(
    controlElem : dom.Element,
    formId      : String
  ): Unit = {

    val repeatId    = controlElem.getAttribute("id")
    val iteration   = controlElem.getAttribute("iteration")
    val relevantOpt = controlElem.attValueOpt("relevant")

    // Remove or add `xforms-disabled` on elements after this delimiter
    relevantOpt
      .map(_.toBoolean)
      .foreach(Controls.setRepeatIterationRelevance(formId, repeatId, iteration, _))
  }

  private def handleValue(
    elem                        : dom.Element,
    controlId                   : String,
    recreatedInput              : Boolean,
    controlsWithUpdatedItemsets : js.Dictionary[Boolean]
  ): Unit = {

    val newControlValue  = elem.textContent
    val documentElement  = dom.document.getElementByIdT(controlId)

    def containsAnyOf(e: dom.Element, tokens: List[String]) = {
      val classList = e.classList
      tokens.exists(classList.contains)
    }

    if (containsAnyOf(documentElement, HandleValueIgnoredControls)) {
      logger.error(s"Got value from server for element with class: ${documentElement.getAttribute("class")}")
    } else {

      val normalizedPreviousServerValueOpt = Option(ServerValueStore.get(controlId)).map(_.normalizeSerializedHtml)
      val normalizedNewControlValue        = newControlValue.normalizeSerializedHtml
      ServerValueStore.set(controlId, newControlValue)

      if (containsAnyOf(documentElement, HandleValueOutputOnlyControls))
        XFormsControls.setCurrentValue(documentElement, normalizedNewControlValue, force = false)
      else
        Controls.getCurrentValue(documentElement) foreach { currentValue =>

          val normalizedCurrentValue = currentValue.normalizeSerializedHtml

          val doUpdate =
            // If this was an input that was recreated because of a type change, we always set its value
            recreatedInput ||
            // If this is a control for which we recreated the itemset, we want to set its value
            controlsWithUpdatedItemsets.get(controlId).contains(true) ||
            (
              // Update only if the new value is different from the value already have in the HTML area
              normalizedCurrentValue != normalizedNewControlValue && (
                // Update only if the value in the control is the same now as it was when we sent it to the server,
                // so not to override a change done by the user since the control value was last sent to the server
                  normalizedPreviousServerValueOpt.isEmpty ||
                  normalizedPreviousServerValueOpt.contains(normalizedCurrentValue) ||
                  // For https://github.com/orbeon/orbeon-forms/issues/3130
                  //
                  // We would like to test for "becomes readonly", but test below is equivalent:
                  //
                  // - either the control was already readonly, so `currentValue != newControlValue` was `true`
                  //   as server wouldn't send a value otherwise
                  // - or it was readwrite and became readonly, in which case we test for this below
                  documentElement.classList.contains("xforms-readonly")
                )
              )

          if (doUpdate) {
            val promiseOrUndef = XFormsControls.setCurrentValue(documentElement, normalizedNewControlValue, force = true)

            // Store the server value as the client sees it, not as the server sees it. There can be a difference in the following cases:
            //
            // 1) For HTML editors, the HTML might change once we put it in the DOM.
            // 2) For select/select1, if the server sends an out-of-range value, the actual value of the field won"t be the out
            //    of range value but the empty string.
            // 3) For boolean inputs, the server might tell us the new value is "" when the field becomes non-relevant, which is
            //    equivalent to "false".
            //
            // It is important to store in the serverValue the actual value of the field, otherwise if the server later sends a new
            // value for the field, since the current value is different from the server value, we will incorrectly think that the
            // user modified the field, and won"t update the field with the value provided by the AjaxServer.

            // `setCurrentValue()` may return a jQuery `Promise` and if it does we update the server value only once it is resolved.
            // For details see https://github.com/orbeon/orbeon-forms/issues/2670.

            maybeFutureToScalaFuture(promiseOrUndef) foreach { _ =>
              ServerValueStore.set(
                controlId,
                Controls.getCurrentValue(documentElement)
              )
            }
          }
        }
    }
  }

  private def handleErrorsElem(formId: String, ignoreErrors: Boolean, errorsElem: dom.Element): Unit = {

    val serverErrors =
      errorsElem.childNodes collect { case n: dom.Element => n } map { errorElem =>
        // <xxf:error exception="org.orbeon.saxon.trans.XPathException" file="gaga.xhtml" line="24" col="12">
        //     Invalid date "foo" (Year is less than four digits)
        // </xxf:error>
        ServerError(
          message  = errorElem.textContent,
          location = errorElem.attValueOpt("file") map { file =>
            BasicLocationData(
              file = file,
              line = errorElem.attValueOpt("line").map(_.toInt).getOrElse(-1),
              col  = errorElem.attValueOpt("col" ).map(_.toInt).getOrElse(-1)
            )
          },
          classOpt = errorElem.attValueOpt("exception")
        )
      }

    AjaxClient.showError(
      titleString   = "Non-fatal error",
      detailsString = ServerError.errorsAsHtmlString(serverErrors),
      formId        = formId,
      ignoreErrors  = ignoreErrors
    )
  }

  private def handleSubmission(
    formId           : String,
    submissionElement: dom.Element,
    notifyReplace    : js.Function0[Unit]
  ): Unit = {

    val urlType         = submissionElement.attValueOrThrow("url-type")
    val submissionId    = submissionElement.attValueOrThrow("submission-id")

    val showProgressOpt = submissionElement.booleanAttValueOpt("show-progress")
    val targetOpt       = submissionElement.attValueOpt("target")

    val form  = Page.getXFormsFormFromNamespacedIdOrThrow(formId)

    // When the target is an iframe, we add a `?t=id` to work around a Chrome bug happening  when doing a POST to the
    // same page that was just loaded, gut that the POST returns a PDF. See:
    //
    // https://code.google.com/p/chromium/issues/detail?id=330687
    // https://github.com/orbeon/orbeon-forms/issues/1480
    //
    def updatedPath(path: String) =
      if (path.contains("xforms-server-submit")) {

        val isTargetAnIframe =
          targetOpt.flatMap(target => dom.document.getElementByIdOpt(target)).exists(_.tagName.equalsIgnoreCase("iframe"))

        if (isTargetAnIframe) {
          val url = new dom.URL(path, dom.document.location.href)
          url.searchParams.delete("t")
          url.searchParams.append("t", java.util.UUID.randomUUID().toString)
          url.href
        } else {
          path
        }
      } else {
        path
      }

    val newTargetOpt =
      targetOpt flatMap {
        case target if ! js.isUndefined(dom.window.asInstanceOf[js.Dynamic].selectDynamic(target)) =>
          // Pointing to a frame, so this won't open a new window
          Some(target)
        case target =>
          // See if we're able to open a new window
          val targetButNotBlank =
            if (target == "_blank")
              Math.random().toString.substring(2) // use target name that we can reuse, in case opening the window works
            else
              target
          // Don't use "noopener" as we do need to use that window to test on it!
          val newWindow = dom.window.open("about:blank", targetButNotBlank)
          if (! js.isUndefined(newWindow) && (newWindow ne null)) // unclear if can be `undefined` or `null` or both!
            Some(targetButNotBlank)
          else
            None
      }

    val effectiveAction =
      updatedPath(
        (
          if (urlType == "action")
            form.xformsServerSubmitActionPath
          else
            form.xformsServerSubmitResourcePath
        ).getOrElse(dom.window.location.toString)
      )

    // Notify the caller (to handle the loading indicator)
    if (! showProgressOpt.contains(false))
      notifyReplace()

    // Remove any pre-existing `<form>` so we don't keep accumulating elements
    dom.document.body
      .querySelectorAll("form[id ^= 'xforms-form-submit-']")
      .toJSArray
      .foreach(_.remove())

    // Create and submit a new `<form>` element created on the fly
    // https://github.com/orbeon/orbeon-forms/issues/6682
    dom.document.body
      .appendChildT(
        JsDom.all.form(
          id      := "xforms-form-submit-" + java.util.UUID.randomUUID().toString,
          method  := "post",
          target  := newTargetOpt.getOrElse(""),
          enctype := "multipart/form-data",
          action  := effectiveAction
        )(
          input(`type` := "hidden", name := Constants.UuidFieldName,         value := form.uuid),
          input(`type` := "hidden", name := Constants.SubmissionIdFieldName, value := submissionId)
        ).render
      )
      .submit()
  } // end handleSubmission

  trait ItemsetItem extends js.Object {
    val attributes: js.UndefOr[js.Dictionary[String]]
    val children  : js.UndefOr[js.Array[ItemsetItem]]
    val label     : js.UndefOr[String]
    val value     : js.UndefOr[String]
    val help      : js.UndefOr[String]
    val hint      : js.UndefOr[String]
  }

  private object Private {

    val HandleValueIgnoredControls    = List("xforms-trigger", "xforms-submit", "xforms-upload")
    val HandleValueOutputOnlyControls = List("xforms-output", "xforms-static", "xforms-label", "xforms-hint", "xforms-help", "xforms-alert")

    def appendRepeatSuffix(id: String, suffix: String): String =
      if (suffix.isEmpty)
        id
      else
        id + Constants.RepeatSeparator + suffix
  }
}
