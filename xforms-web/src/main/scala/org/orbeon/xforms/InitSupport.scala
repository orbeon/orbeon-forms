/**
  * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.xforms

import io.circe.generic.auto._
import io.circe.parser.decode
import org.log4s.Logger
import org.orbeon.facades.{Bowser, Mousetrap}
import org.orbeon.liferay._
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.MarkupUtils.MarkupStringOps
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{ContentTypes, LoggerFactory, PathUtils}
import org.orbeon.web.DomEventNames
import org.orbeon.wsrp.WSRPSupport
import org.orbeon.xforms
import org.orbeon.xforms.EventNames.{KeyModifiersPropertyName, KeyTextPropertyName}
import org.orbeon.xforms.StateHandling.StateResult
import org.orbeon.xforms.facade._
import org.orbeon.xforms.rpc.Initializations
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import java.io.StringWriter
import scala.collection.{mutable => m}
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.Dictionary
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}


@JSExportTopLevel("OrbeonInitSupport")
object InitSupport {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xforms.InitSupport")

  import Private._

  def pageContainsFormsMarkup(): Unit =
    pageContainsFormsMarkupPromise.success(())

  def mapNamespacePromise(namespace: String): Future[Option[xforms.Form]] = {
    val promise = Promise[Option[xforms.Form]]()
    formNamespacesToPromise += namespace -> promise
    promise.future
  }

  private def completeNamespacePromise(namespace: String, formOpt: Option[xforms.Form]): Unit =
    formNamespacesToPromise.get(namespace).foreach(_.success(formOpt))

  def removeNamespacePromise(namespace: String): Unit =
    formNamespacesToPromise -= namespace

  private var initTimestampBeforeMs: Long = -1

  def getResponseTransform(contextAndNamespaceOpt: Option[(String, String)])(content: String, contentType: String): String = {

    // See also `ServletEmbeddingContextWithResponse.decodeURL()`
    def decodeURL(context: String, namespace: String)(encoded: String): String = {

      def createResourceURL(resourceId: String) = {

        val (path, query) = PathUtils.splitQueryDecodeParams(resourceId)

        val basePath = context.dropTrailingSlash + '/' + path.dropStartingSlash

        if (path.endsWith(".css"))
          PathUtils.recombineQuery(basePath, query ::: (Constants.EmbeddingNamespaceParameter -> namespace) :: Nil)
        else
          PathUtils.recombineQuery(basePath, query)
      }

      def path(navigationParameters: Map[String, Array[String]]) =
        navigationParameters.getOrElse(WSRPSupport.PathParameterName, Array()).headOption.getOrElse(throw new IllegalStateException)

      def createActionOrRenderURL(portletMode: Option[String], windowState: Option[String], navigationParameters: Map[String, Array[String]]) =
        context.dropTrailingSlash + '/' + path(navigationParameters).dropStartingSlash

      val decodedUrl =
        WSRPSupport.decodeURL(encoded, createResourceURL, createActionOrRenderURL, createActionOrRenderURL)

      // TODO: Check this logic, which is done in `APISupport` and which we reproduce here, but without being 100%
      //  certain of why it's necessary.
      if (contentType == ContentTypes.XmlContentType) decodedUrl.escapeXmlMinimal else decodedUrl
    }

    contextAndNamespaceOpt match {
      case Some((context, namespace)) =>
        val writer = new StringWriter
        WSRPSupport.decodeWSRPContent(content, namespace, decodeURL(context, namespace), writer)
        writer.toString
      case None =>
        content
    }
  }

  // Called by form-specific dynamic initialization
  @JSExport
  def initializeFormWithInitData(
    initializeFormWithInitData : String,
    contextPathOrUndef         : js.UndefOr[String],
    namespaceOrUndef           : js.UndefOr[String])
  : Unit = {

    initTimestampBeforeMs = System.currentTimeMillis()

    val contextAndNamespaceOpt = namespaceOrUndef.toOption.map(contextPathOrUndef.getOrElse("") -> _)

    val updatedInitializeFormWithInitData =
      getResponseTransform(contextAndNamespaceOpt)(initializeFormWithInitData, ContentTypes.JsonContentType)

    val initializations =
      decode[rpc.Initializations](updatedInitializeFormWithInitData) match {
        case Left(e)  => throw e
        case Right(i) => i
      }

    logger.debug(s"initialization data is ready for form `${initializations.namespacedFormId}`/`${initializations.uuid}`")

    AjaxClient.initialize(initializations.configuration)

    pageContainsFormsMarkupF foreach { _ =>

      namespaceOrUndef.toOption.foreach { namespace =>
        val formOpt = initializeForm(initializations, contextAndNamespaceOpt)
        completeNamespacePromise(namespace, formOpt)
      }

      initializeReactWhenSessionAboutToExpire(initializations.configuration)

      if (Page.countInitializedForms == Support.allFormElems.size) {
        logger.info(s"all forms are loaded; total time for client-side web app initialization: ${System.currentTimeMillis() - initTimestampBeforeMs} ms")
        scheduleOrbeonLoadedEventIfNeeded(initializations.configuration)
      }
    }
  }

  def liferayF: Future[Unit] = {
    logger.debug("checking for Liferay object")
    dom.window.Liferay.toOption match {
      case None          => Future.unit
      case Some(liferay) => liferay.allPortletsReadyF
    }
  }

  def setupGlobalClassesIfNeeded(): Unit = {

    val body = dom.document.body

    // For embedding as we don't have control over the generation of the `<body>` element
    // Remove once we no longer depend on YUI widgets at all anymore.
    body.classList.add(Constants.YuiSkinSamClass)

    // TODO: With embedding, consider placing those on the root element of the embedded code. Watch for dialogs behavior.
    if (Bowser.ios.contains(true))
      body.classList.add(Constants.XFormsIosClass)

    if (Bowser.mobile.contains(true))
      body.classList.add(Constants.XFormsMobileClass)
  }

  @JSExport
  def initializeJavaScriptControlsFromSerialized(initData: String): Unit =
    decode[List[rpc.Control]](initData) match {
      case Left(e)  =>
        logger.error(s"error decoding serialized controls for `initializeJavaScriptControlsFromSerialized`: ${e.getMessage}")
      case Right(controls) =>
        initializeJavaScriptControls(controls)
    }

  @JSExport
  def destroyJavaScriptControlsFromSerialized(initData: String): Unit =
    decode[List[rpc.Control]](initData) match {
      case Left(e)  =>
        logger.error(s"error decoding serialized controls for `destroyJavaScriptControlsFromSerialized`: ${e.getMessage}")
      case Right(controls) =>
        destroyJavaScriptControls(controls)
    }

  @JSExport
  def processRepeatHierarchyUpdateForm(formId: String, repeatTreeString: String): Unit = {

    val (repeatTreeChildToParent, repeatTreeParentToAllChildren) =
      processRepeatHierarchy(repeatTreeString)

    val form = Page.getXFormsFormFromNamespacedIdOrThrow(formId)

    form.repeatTreeChildToParent       = repeatTreeChildToParent
    form.repeatTreeParentToAllChildren = repeatTreeParentToAllChildren
  }

  private object Private {

    val pageContainsFormsMarkupPromise = Promise[Unit]()

    private var topLevelListenerRegistered               = false
    private var reactWhenSessionAboutToExpireInitialized = false
    private var orbeonLoadedEventScheduled               = false

    var formNamespacesToPromise : Map[String, Promise[Option[xforms.Form]]] = Map.empty

    def initializeForm(initializations: Initializations, contextAndNamespaceOpt: Option[(String, String)]): Option[Form] = {

      logger.debug(s"initializing form `${initializations.namespacedFormId}`/`${initializations.uuid}`")
      val formId      = initializations.namespacedFormId
      // Form is an `Option` as it might already have been removed by the time we receive the dynamic JavaScript
      // (Should we do an error instead of a cast?)
      val formElemOpt = Option(dom.document.getElementById(formId).asInstanceOf[html.Form])
      formElemOpt.flatMap { formElem =>

        // Q: Do this later?
        $(formElem).removeClass(Constants.InitiallyHiddenClass)

        val uuid =
          StateHandling.initializeState(formId, initializations.uuid, initializations.configuration.revisitHandling) match {
            case StateResult.Uuid(uuid) =>
              uuid
            case StateResult.Restore(uuid) =>
              AjaxClient.fireEvent(
                AjaxEvent(
                  eventName = EventNames.XXFormsAllEventsRequired,
                  form      = formElem
                )
              )
              uuid
            case StateResult.Reload =>
              dom.window.location.reload(flag = true)
              return None
          }

        val (repeatTreeChildToParent, repeatTreeParentToAllChildren) =
          processRepeatHierarchy(initializations.repeatTree)

        // NOTE on paths: We switched back and forth between trusting the client or the server. Starting 2010-08-27
        // the server provides the info. Starting 2011-10-05 we revert to using the server values instead of client
        // detection, as that works in portals. The concern with using the server values was proxying. But should
        // proxying be able to change the path itself? If so, wouldn't other things break anyway? So for now
        // server values it is.

        val newForm =
          new Form(
            uuid                           = uuid,
            elem                           = formElem,
            ns                             = formId.substring(0, formId.indexOf(Constants.FormClass)), // namespaceOpt.getOrElse("")
            contextAndNamespaceOpt         = contextAndNamespaceOpt,
            xformsServerPath               = initializations.xformsServerPath,
            xformsServerSubmitActionPath   = initializations.xformsServerSubmitActionPath,
            xformsServerSubmitResourcePath = initializations.xformsServerSubmitResourcePath,
            xformsServerUploadPath         = initializations.xformsServerUploadPath,
            repeatTreeChildToParent        = repeatTreeChildToParent,
            repeatTreeParentToAllChildren  = repeatTreeParentToAllChildren,
            repeatIndexes                  = processRepeatIndexes(initializations.repeatIndexes),
            xblInstances                   = js.Array(),
            configuration                  = initializations.configuration
          )

        Page.registerForm(
          formId,
          newForm
        )

        initializeJavaScriptControls(initializations.controls)
        initializeKeyListeners(initializations.listeners, formElem)
        dispatchInitialServerEvents(initializations.pollEvent, formId)

        initializeGlobalEventListenersIfNeeded()

        // Putting this here due to possible Scala.js bug reporting a "applyDynamic does not support passing a vararg parameter"
        // 2020-11-26: Using Scala.js 1.0 way of detecting the global variable.

        // TODO: move to shared place
        def namespaceBuildXFormsPageLoadedServer(namespaceOpt: Option[String]): String =
          s"xformsPageLoadedServer${namespaceOpt.getOrElse("")}"

        val xformsPageLoadedServerName = namespaceBuildXFormsPageLoadedServer(contextAndNamespaceOpt.map(_._2))

        val hasOtherScripts =
          js.typeOf(js.special.fileLevelThis.asInstanceOf[js.Dynamic].selectDynamic(xformsPageLoadedServerName)) != "undefined"

        // Run user scripts
        initializations.userScripts foreach { case rpc.UserScript(functionName, targetId, observerId, paramsValues) =>
          ServerAPI.callUserScript(formId, functionName, targetId, observerId, paramsValues map (_.asInstanceOf[js.Any]): _*)
        }

        // Run other code sent by server
        if (initializations.messagesToRun.nonEmpty)
          MessageDialog.showMessages(initializations.messagesToRun.toJSArray)

        initializations.dialogsToShow foreach { case rpc.Dialog(id, neighborId) =>
          XFormsUI.showDialogForInit(id, neighborId)
        }

        // Do this after dialogs as focus might be within a dialog
        initializations.focusElementId foreach { focusElementId =>
          Controls.setFocus(focusElementId)
        }

        initializations.errorsToShow foreach { case rpc.Error(title, details, formId) =>
          AjaxClient.showError(title, details, formId, ignoreErrors = false)
        }

        if (hasOtherScripts)
          js.special.fileLevelThis.asInstanceOf[js.Dynamic].applyDynamic(xformsPageLoadedServerName)()

        Some(newForm)
      }
    }

    // The heartbeat is per servlet session and we only need one. But see https://github.com/orbeon/orbeon-forms/issues/2014.
    def initializeReactWhenSessionAboutToExpire(configuration: rpc.ConfigurationProperties): Unit =
      if (! reactWhenSessionAboutToExpireInitialized) {
        // Say session is 60 minutes and percentage is 80%: heartbeat must come after 48 minutes and we check every 4.8 minutes
        val reactAfterMillis = (configuration.maxInactiveIntervalMillis * (configuration.sessionExpirationTriggerPercentage.toDouble / 100.0)).toLong

        Session.initialize(configuration, reactAfterMillis)

        val checkEveryMillis = reactAfterMillis / 10
        if (checkEveryMillis > 0) {
          logger.debug(s"checking if getting close to session expiration every $checkEveryMillis ms")
          js.timers.setInterval(checkEveryMillis)(Session.updateWithLocalNewestEventTime())
        }
        reactWhenSessionAboutToExpireInitialized = true
      }

    def scheduleOrbeonLoadedEventIfNeeded(configuration: rpc.ConfigurationProperties): Unit =
      if (! orbeonLoadedEventScheduled) {
        // See https://doc.orbeon.com/xforms/core/client-side-javascript-api#custom-events
        // See https://github.com/orbeon/orbeon-forms/issues/3729
        // 2019-01-10: There was an old comment about how the call to `this.subscribers.length` in the `fire()`
        // method could hang with IE. That is likely no longer a relevant comment but it might still be
        // better to fire the event asynchronously, although we could maybe use a `0` delay.
        js.timers.setTimeout(configuration.internalShortDelay) {
          logger.debug("dispatching `orbeonLoadedEvent`")
          Events.orbeonLoadedEvent.fire()
        }
        orbeonLoadedEventScheduled = true
      }

    def pageContainsFormsMarkupF: Future[Unit] =
      pageContainsFormsMarkupPromise.future

    private def parseRepeatIndexes(repeatIndexesString: String): List[(String, String)] =
      for {
        repeatIndexes <- repeatIndexesString.splitTo[List](",")
        repeatInfos   = repeatIndexes.splitTo[List]() // must be of the form "a b"
      } yield
        repeatInfos.head -> repeatInfos.last

    private def parseRepeatTree(repeatTreeString: String): List[(String, String)] =
      for {
       repeatTree  <- repeatTreeString.splitTo[List](",")
       repeatInfos = repeatTree.splitTo[List]() // must be of the form "a b"
       if repeatInfos.size > 1
     } yield
       repeatInfos.head -> repeatInfos.last

    private def createParentToChildrenMap(childToParentMap: Map[String, String]): collection.Map[String, js.Array[String]] = {

      val parentToChildren = m.Map[String, js.Array[String]]()

      childToParentMap foreach { case (child, parent) =>
        Iterator.iterateOpt(parent)(childToParentMap.get) foreach { p =>
          parentToChildren.getOrElseUpdate(p, js.Array[String]()).push(child)
        }
      }

      parentToChildren
    }

    def processRepeatHierarchy(repeatTreeString: String): (Dictionary[String], Dictionary[js.Array[String]]) = {

      val childToParent    = parseRepeatTree(repeatTreeString)
      val childToParentMap = childToParent.toMap

      (childToParentMap.toJSDictionary, createParentToChildrenMap(childToParentMap).toJSDictionary)
    }

    private def processRepeatIndexes(repeatIndexesString: String): Dictionary[String] =
      parseRepeatIndexes(repeatIndexesString).toMap.toJSDictionary

    private def initializeGlobalEventListenersIfNeeded(): Unit =
      if (! topLevelListenerRegistered) {
        // We are using jQuery for `change` because the Select2 component, used for the dropdowns with search, dispatches that jQuery
        // event, and if just using the DOM API our code handling `change` in `xforms.js` isn't being notified, and the value isn't
        // updated on the server
        GlobalEventListenerSupport.addJQueryListener(dom.document, DomEventNames.Change, Events.change)

        // We are not using jQuery for `focusin` and `focusout` as jQuery registers its own listeners on `focus` and `blur`, maybe
        // for compatibility with older browsers that didn't support `focusin` and `focusout`, and since they are different events,
        // we're then unable stopping the propagation of those events
        GlobalEventListenerSupport.addJsListener(dom.document, DomEventNames.FocusIn,   Events.focus)
        GlobalEventListenerSupport.addJsListener(dom.document, DomEventNames.FocusOut,  Events.blur)
        GlobalEventListenerSupport.addJsListener(dom.document, DomEventNames.KeyPress,  Events.keypress)
        GlobalEventListenerSupport.addJsListener(dom.document, DomEventNames.KeyDown,   Events.keydown)
        GlobalEventListenerSupport.addJsListener(dom.document, DomEventNames.Input,     Events.input)
        GlobalEventListenerSupport.addJsListener(dom.document, DomEventNames.MouseOver, Events.mouseover)
        GlobalEventListenerSupport.addJsListener(dom.document, DomEventNames.MouseOut,  Events.mouseout)
        GlobalEventListenerSupport.addJsListener(dom.document, DomEventNames.Click,     Events.click)

        // Catch logout link clicks to inform other pages on the same session that it is going to be invalidated
        $(".fr-logout-link").get.foreach { logoutAnchor =>
          GlobalEventListenerSupport.addJsListener(logoutAnchor, DomEventNames.Click, (_: dom.raw.Event) => {
            Session.logout()
          })
        }

        // We could do this on `pageshow` or `pagehide`
        // https://github.com/orbeon/orbeon-forms/issues/4552
        GlobalEventListenerSupport.addListener(dom.window,
          DomEventNames.PageHide,
          (ev: dom.raw.PageTransitionEvent) => {
            if (ev.persisted)
              Page.loadingIndicator().hideIfAlreadyVisible()
          }
        )

        AjaxFieldChangeTracker.initialize()
        topLevelListenerRegistered = true
      }

    def initializeJavaScriptControls(controls: List[rpc.Control]): Unit =
      controls foreach { case rpc.Control(id, valueOpt) =>
        Option(dom.document.getElementById(id).asInstanceOf[html.Element]) foreach { control =>
          val classList = control.classList
          if (XFormsXbl.isComponent(control)) {
            // Custom XBL component initialization
            for {
              _     <- Option(XBL.instanceForControl(control))
              value <- valueOpt
            } locally {
              Controls.setCurrentValue(control, value, force = false)
            }
          } else if (classList.contains("xforms-select1-appearance-compact") || classList.contains("xforms-select-appearance-compact")) {
            // Legacy JavaScript initialization
            Init._compactSelect(control)
          }
        }
      }

    def destroyJavaScriptControls(controls: List[rpc.Control]): Unit =
      controls foreach { case rpc.Control(id, _) =>
        Option(dom.document.getElementById(id).asInstanceOf[html.Element]) foreach { control =>
          if (XFormsXbl.isComponent(control)) {
            for {
              instance <- Option(XBL.instanceForControl(control))
            } locally {
              instance.destroy()
            }
          }
        }
      }

    private val KeysForWhichToPreventDefault = Set("up", "down", "left", "right")

    private def initializeKeyListeners(listeners: List[rpc.KeyListener], formElem: html.Form): Unit =
      listeners foreach { case rpc.KeyListener(eventNames, observer, keyText, modifiers) =>

        // NOTE: 2019-01-07: We don't handle dialogs yet.
        //if (dom.document.getElementById(observer).classList.contains("xforms-dialog"))

        // TODO: destruction
        val mousetrap =
          if (observer == Constants.DocumentId)
            Mousetrap // TODO: should observe on embedding element?
          else
            Mousetrap(dom.document.getElementById(observer).asInstanceOf[html.Element])

        val modifierStrings =
          modifiers.toList map (_.entryName)

        val modifierString =
          modifierStrings mkString " "

        val callback: js.Function = (e: dom.KeyboardEvent, combo: String) => {

          val properties =
            Map(KeyTextPropertyName -> (keyText: js.Any)) ++
              (modifiers map (_ => KeyModifiersPropertyName -> (modifierString: js.Any)))

          AjaxClient.fireEvent(
            AjaxEvent(
              eventName  = e.`type`,
              targetId   = observer,
              properties = properties,
              form       = Some(formElem)
            )
          )

          if (modifiers.nonEmpty || KeysForWhichToPreventDefault(keyText))
            e.preventDefault()
        }

        val keys = modifierStrings ::: List(keyText.toLowerCase) mkString "+"

        // It is unlikely that supporting multiple event names is very useful, but you can imagine
        // in theory supporting both `keydown` and `keyup` for example.
        eventNames foreach { eventName =>
          mousetrap.bind(keys, callback, eventName)
        }
      }

    private def dispatchInitialServerEvents(events: Option[rpc.PollEvent], formId: String): Unit =
      events foreach { case rpc.PollEvent(delay) =>
        AjaxClient.createDelayedPollEvent(
          delay  = delay.toDouble,
          formId = formId
        )
      }
  }
}
