/**
  * Copyright (C) 2018 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.processor

import io.circe.generic.auto._
import io.circe.syntax._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.URLRewriterUtils
import org.orbeon.oxf.util.URLRewriterUtils.{RESOURCES_VERSIONED_PROPERTY, RESOURCES_VERSION_NUMBER_PROPERTY, getApplicationResourceVersion}
import org.orbeon.oxf.xforms.XFormsProperties._
import org.orbeon.oxf.xforms.XFormsUtils.{escapeJavaScript, namespaceId}
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl
import org.orbeon.oxf.xforms.control.{Controls, XFormsComponentControl, XFormsControl, XFormsValueComponentControl}
import org.orbeon.oxf.xforms.event.XFormsEvents
import org.orbeon.oxf.xforms.{ServerError, ShareableScript, XFormsContainingDocument, XFormsUtils}
import org.orbeon.xforms.{EventNames, rpc}

import scala.collection.mutable.ListBuffer
import scala.collection.compat._

object ScriptBuilder {

  def quoteString(s: String) =
    s""""${escapeJavaScript(s)}""""

  def escapeJavaScriptInsideScript (js: String): String =
    // Method from https://stackoverflow.com/a/23983448/5295
    org.apache.commons.lang3.StringUtils.replace(js, "</script", "</scr\\ipt")

  def writeScripts(shareableScripts: Iterable[ShareableScript], write: String => Unit): Unit =
    for (shareableScript <- shareableScripts) {

      val paramsString =
        if (shareableScript.paramNames.nonEmpty)
          shareableScript.paramNames mkString (",", ",", "")
        else
          ""

      write(s"\nfunction ${shareableScript.clientName}(event$paramsString) {\n")
      write(shareableScript.body)
      write("}\n")
    }

  def gatherJavaScriptInitializations(startControl: XFormsControl): List[(String, Option[String])] = {

    val controlsToInitialize = ListBuffer[(String, Option[String])]()

    def addControlToInitialize(effectiveId: String, value: Option[String]) =
      controlsToInitialize += effectiveId -> value

    Controls.ControlsIterator(startControl, includeSelf = false, followVisible = true) foreach {
      case c: XFormsValueComponentControl =>
        if (c.isRelevant) {
          val abstractBinding = c.staticControl.abstractBinding
          if (abstractBinding.modeJavaScriptLifecycle)
            addControlToInitialize(
              c.getEffectiveId,
              if (abstractBinding.modeExternalValue)
                c.externalValueOpt
              else
                None
            )
        }
      case c: XFormsComponentControl =>
        if (c.isRelevant && c.staticControl.abstractBinding.modeJavaScriptLifecycle)
          addControlToInitialize(c.getEffectiveId, None)
      case c =>
        // Legacy JavaScript initialization
        // As of 2016-08-04: xxf:dialog, xf:select1[appearance = compact], xf:range
        if (c.hasJavaScriptInitialization && ! c.isStaticReadonly)
          addControlToInitialize(c.getEffectiveId, None)
    }

    controlsToInitialize.result
  }

  def findConfigurationProperties(
    containingDocument : XFormsContainingDocument,
    versionedResources : Boolean,
    heartbeatDelay     : Long
  ): Option[String] = {

    // Gather all static properties that need to be sent to the client
    val staticProperties = containingDocument.getStaticState.clientNonDefaultProperties

    val dynamicProperties = {

      def dynamicProperty(p: => Boolean, name: String, value: Any) =
        p option (name -> value)

      // Heartbeat delay is dynamic because it depends on session duration
      def heartbeatOpt =
        Some(SESSION_HEARTBEAT_DELAY_PROPERTY -> heartbeatDelay)

      // Help events are dynamic because they depend on whether the xforms-help event is used
      // TODO: Better way to enable/disable xforms-help event support, maybe static analysis of event handlers?
      def helpOpt = dynamicProperty(
        containingDocument.getStaticOps.hasHandlerForEvent(XFormsEvents.XFORMS_HELP, includeAllEvents = false),
        HELP_HANDLER_PROPERTY,
        true
      )

      // Whether resources are versioned
      def resourcesVersionedOpt = dynamicProperty(
        versionedResources != URLRewriterUtils.RESOURCES_VERSIONED_DEFAULT,
        RESOURCES_VERSIONED_PROPERTY,
        versionedResources
      )

      // Application version is not an XForms property but we want to expose it on the client
      def resourcesVersionOpt = dynamicProperty(
        versionedResources && (getApplicationResourceVersion ne null),
        RESOURCES_VERSION_NUMBER_PROPERTY,
        getApplicationResourceVersion
      )

      // Gather all dynamic properties that are defined
      List(heartbeatOpt, helpOpt, resourcesVersionedOpt, resourcesVersionOpt).flatten
    }

    val globalProperties = List(
      DELAY_BEFORE_AJAX_TIMEOUT_PROPERTY -> getAjaxTimeout,
      RETRY_DELAY_INCREMENT              -> getRetryDelayIncrement,
      RETRY_MAX_DELAY                    -> getRetryMaxDelay
    )

    // combine all static and dynamic properties
    val clientProperties = staticProperties ++ dynamicProperties ++ globalProperties

    clientProperties.nonEmpty option {

      val sb = new StringBuilder

      sb append "var opsXFormsProperties = {"

      for (((propertyName, propertyValue), index) <- clientProperties.toList.zipWithIndex) {

        if (index != 0)
          sb append ','

        sb append '"'
        sb append propertyName
        sb append "\":"

        propertyValue match {
          case s: String =>
            sb append '"'
            sb append s
            sb append '"'
          case _ =>
            sb append propertyValue.toString
        }
      }

      sb append "};"

      sb.toString
    }
  }

  def buildJavaScriptInitialData(
    containingDocument   : XFormsContainingDocument,
    rewriteResource      : String => String,
    controlsToInitialize : List[(String, Option[String])]
  ): String = {

    val currentTime = System.currentTimeMillis

    // NOTE: `calendarImage` is to handle the minimal date picker. Because the client must re-generate markup
    // when the control becomes relevant or changes type, it needs the image URL, and that URL must be
    // rewritten for use in portlets. This should ideally be handled by a template and/or the server should
    // provide the markup directly when needed.

    val jsonString =
      rpc.Initializations(
        uuid                   = containingDocument.getUUID,
        namespacedFormId       = XFormsUtils.getNamespacedFormId(containingDocument),
        repeatTree             = containingDocument.getStaticOps.getRepeatHierarchyString(containingDocument.getContainerNamespace),
        repeatIndexes          = XFormsRepeatControl.currentNamespacedIndexesString(containingDocument),
        xformsServerPath       = rewriteResource("/xforms-server"),
        xformsServerUploadPath = rewriteResource("/xforms-server/upload"),
        calendarImagePath      = rewriteResource("/ops/images/xforms/calendar.png"),
        controls  =
          for {
            (id, valueOpt) <- controlsToInitialize
          } yield
            rpc.Control(namespaceId(containingDocument, id), valueOpt),
        listeners =
          (
            for {
              handler  <- containingDocument.getStaticOps.keyboardHandlers
              observer <- handler.observersPrefixedIds
              keyText  <- handler.keyText
            } yield
              rpc.KeyListener(handler.eventNames filter EventNames.KeyboardEvents, observer, keyText, handler.keyModifiers)
            ).distinct,
        events    =
          for {
            delayedEvent <- containingDocument.delayedEvents
          } yield
            delayedEvent.toServerEvent(currentTime),
        userScripts =
          for (script <- containingDocument.getScriptsToRun.to(List) collect { case Right(s) => s })
            yield
              rpc.UserScript(
                functionName = script.script.shared.clientName,
                targetId     = namespaceId(containingDocument, script.targetEffectiveId),
                observerId   = namespaceId(containingDocument, script.observerEffectiveId),
                paramValues  = script.paramValues
              )
      ).asJson.noSpaces

    s"""(function(){ORBEON.xforms.InitSupport.initializeFormWithInitData(${quoteString(jsonString)})}).call(this);"""
  }

  def findOtherScriptInvocations(containingDocument: XFormsContainingDocument): Option[String] = {

    val errorsToShow      = containingDocument.getServerErrors
    val focusElementIdOpt = containingDocument.getControls.getFocusedControl map (_.getEffectiveId)
    val messagesToRun     = containingDocument.getMessagesToRun filter (_.level == "modal")

    val dialogsToOpen =
      for {
        dialogControl <- containingDocument.getControls.getCurrentControlTree.getDialogControls
        if dialogControl.isVisible
      } yield
        dialogControl

    val javascriptLoads =
      containingDocument.getScriptsToRun collect { case Left(l) => l }

    val mustRunAnyScripts =
      errorsToShow.nonEmpty       ||
      focusElementIdOpt.isDefined ||
      messagesToRun.nonEmpty      ||
      dialogsToOpen.nonEmpty      ||
      javascriptLoads.nonEmpty

    mustRunAnyScripts option {

      val sb = new StringBuilder

      if (mustRunAnyScripts) {

        sb append "\nfunction xformsPageLoadedServer() { "

        // NOTE: The order of script actions vs. `javascript:` loads should be preserved. It is not currently.

        // javascript: loads
        for (load <- javascriptLoads) {
          val body = escapeJavaScript(load.resource.substring("javascript:".size))
          sb append s"""(function(){$body})();"""
        }

        // TODO: `showMessages`, `showDialog`, `setFocus`, `showError` must be part of `Initializations`.

        // Initial modal xf:message to run if present
        if (messagesToRun.nonEmpty) {
          val quotedMessages = messagesToRun map (m => s""""${escapeJavaScript(m.message)}"""")
          quotedMessages.addString(sb, "ORBEON.xforms.action.Message.showMessages([", ",", "]);")
        }

        // Initial dialogs to open
        for (dialogControl <- dialogsToOpen) {
          val id       = namespaceId(containingDocument, dialogControl.getEffectiveId)
          val neighbor = (
            dialogControl.neighborControlId
            map (n => s""""${namespaceId(containingDocument, n)}"""")
            getOrElse "null"
          )

          sb append s"""ORBEON.xforms.Controls.showDialog("$id", $neighbor);"""
        }

        // Initial setfocus if present
        // Seems reasonable to do this after dialogs as focus might be within a dialog
        focusElementIdOpt foreach { id =>
          sb append s"""ORBEON.xforms.Controls.setFocus("${namespaceId(containingDocument, id)}");"""
        }

        // Initial errors
        if (errorsToShow.nonEmpty) {

          val title   = "Non-fatal error"
          val details = XFormsUtils.escapeJavaScript(ServerError.errorsAsHTMLElem(errorsToShow).toString)
          val formId  = XFormsUtils.getNamespacedFormId(containingDocument)

          sb append s"""ORBEON.xforms.server.AjaxServer.showError("$title", "$details", "$formId");"""
        }

        sb append " }"
      }

      sb.toString
    }
  }
}