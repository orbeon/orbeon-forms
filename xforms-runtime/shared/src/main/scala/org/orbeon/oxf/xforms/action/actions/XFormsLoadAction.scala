/**
  * Copyright (C) 2010 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  * 2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.action.actions

import org.orbeon.dom.Element
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.UrlRewriteMode
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.xforms.{Load, UrlType, XFormsCrossPlatformSupport, XFormsNames}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xml.XMLConstants


class XFormsLoadAction extends XFormsAction {

  import XFormsLoadAction._

  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    val interpreter = actionContext.interpreter
    val actionElem  = actionContext.analysis.element

    val show =
      Option(interpreter.resolveAVT(actionContext.analysis, "show")) map
        LegacyShow.withNameLowercaseOnly                 getOrElse
        LegacyShow.Replace

    // NOTE: Will also be none if the XPath context item is missing (which is not great).
    val targetOpt =
      actionElem.attributeValueOpt(XFormsNames.TARGET_QNAME)         orElse
      actionElem.attributeValueOpt(XFormsNames.XXFORMS_TARGET_QNAME) flatMap
      (v => Option(interpreter.resolveAVTProvideValue(actionContext.analysis, v)))

    val urlType =
      Option(interpreter.resolveAVT(actionContext.analysis, XMLConstants.FORMATTING_URL_TYPE_QNAME)) map
      UrlType.withNameLowercaseOnly getOrElse
      UrlType.Render

    val urlNorewrite   = resolveUrlNorewrite(actionElem)
    val isShowProgress = interpreter.resolveAVT(actionContext.analysis, XFormsNames.XXFORMS_SHOW_PROGRESS_QNAME) != "false"

    // XForms 1.1 had "If both are present, the action has no effect.", but XForms 2.0 no longer requires this.

    val bindingContext = interpreter.actionXPathContext.getCurrentBindingContext

    bindingContext.newBind option bindingContext.getSingleItemOrNull match {
      case Some(null) =>
      // NOP if no URI obtained (2019-03-12 asked WG for confirmation)
      case Some(item) =>
        resolveStoreLoadValue(
          containingDocument = actionContext.containingDocument,
          currentElem        = Some(actionElem),
          doReplace          = show == LegacyShow.Replace,
          value              = DataModel.getValue(item).encodeHRRI(processSpace = true),
          target             = targetOpt,
          urlType            = urlType,
          urlNorewrite       = urlNorewrite,
          isShowProgress     = isShowProgress,
          mustHonorDeferredUpdateFlags           = interpreter.mustHonorDeferredUpdateFlags(actionContext.analysis)
        )
      case None =>
        actionElem.attributeValueOpt(XFormsNames.RESOURCE_QNAME) match {
          case Some(resourceAttValue) =>

            Option(interpreter.resolveAVTProvideValue(actionContext.analysis, resourceAttValue)) match {
              case Some(resolvedResource) =>
                resolveStoreLoadValue(
                  containingDocument = actionContext.containingDocument,
                  currentElem        = Some(actionElem),
                  doReplace          = show == LegacyShow.Replace,
                  value              = resolvedResource.encodeHRRI(processSpace = true),
                  target             = targetOpt,
                  urlType            = urlType,
                  urlNorewrite       = urlNorewrite,
                  isShowProgress     = isShowProgress,
                  mustHonorDeferredUpdateFlags           = interpreter.mustHonorDeferredUpdateFlags(actionContext.analysis)
                )
              case None =>
                if (interpreter.indentedLogger.debugEnabled)
                  interpreter.indentedLogger.logDebug(
                    "xf:load",
                    "resource AVT returned an empty sequence, ignoring action",
                    "resource",
                    resourceAttValue
                  )
            }

          case None =>
            // "Either the single node binding attributes, pointing to a URI in the instance
            // data, or the linking attributes are required."
            throw new OXFException("Missing 'resource' attribute or single-item binding on the `xf:load` element.")
        }
    }
  }
}

object XFormsLoadAction {

  import enumeratum._

  sealed trait LegacyShow extends EnumEntry
  object LegacyShow extends Enum[LegacyShow] {

    val values = findValues

    case object New     extends LegacyShow
    case object Replace extends LegacyShow
  }

  def resolveStoreLoadValue(
    containingDocument           : XFormsContainingDocument,
    currentElem                  : Option[Element],
    doReplace                    : Boolean,
    value                        : String,
    target                       : Option[String],
    urlType                      : UrlType,
    urlNorewrite                 : Boolean,
    isShowProgress               : Boolean,
    mustHonorDeferredUpdateFlags : Boolean
  ): Unit = {

    if (mustHonorDeferredUpdateFlags)
      containingDocument.synchronizeAndRefresh()

    val externalURL =
      if (value.startsWith("#") || urlNorewrite) {
        // Keep value unchanged if it's just a fragment or if we are explicitly disabling rewriting
        // TODO: Not clear what happens in portlet mode: does norewrite make any sense?
        value
      } else {
        // URL must be resolved
        if (urlType == UrlType.Resource) {
          // Load as resource URL
          XFormsCrossPlatformSupport.resolveResourceURL(
            containingDocument,
            currentElem.orNull,
            value,
            UrlRewriteMode.AbsolutePathOrRelative
          )
        } else {
          // Load as render URL

          // Cases for `show="replace"` and `render` URLs:
          //
          // 1. Servlet in non-embedded mode
          //     1. Upon initialization
          //         - URL is rewritten to absolute URL
          //         - `XFormsToXHTML` calls `getResponse.sendRedirect()`
          //             - just use the absolute `location` URL without any further processing
          //     2. Upon Ajax request
          //         - URL is rewritten to absolute URL
          //         - `xxf:load` sent to client in Ajax response
          //         - client does `window.location.href = ...` or `window.open()`
          // 2. Servlet in embedded mode (with client proxy portlet or embedding API)
          //     1. Upon initialization
          //         - URL is first rewritten to a absolute path without context (resolving of `resolveXMLBase`)
          //         - URL is not WSRP-encoded
          //         - `XFormsToXHTML` calls `getResponse.sendRedirect()`
          //         - `ServletExternalContext.getResponse.sendRedirect()`
          //             - rewrite the path to an absolute path including context
          //             - add `orbeon-embeddable=true`
          //         - embedding client performs HTTP redirect
          //     2. Upon Ajax request
          //         - URL is WSRP-encoded
          //         - `xxf:load` sent to client in Ajax response
          //         - client does `window.location.href = ...` or `window.open()`
          // 3. Portlet
          //     1. Upon initialization
          //         - not handled (https://github.com/orbeon/orbeon-forms/issues/2617)
          //     2. Upon Ajax
          //         - perform a "two-pass load"
          //         - URL is first rewritten to a absolute path without context (resolving of `resolveXMLBase`)
          //         - URL is not WSRP-encoded
          //         - `XFormsServer` adds a server event with `xxforms-load` dispatched to `#document`
          //         - client performs form submission (`action`) which includes server events
          //         - server dispatches incoming `xxforms-load` to `XXFormsRootControl`
          //         - `XXFormsRootControl` performs `getResponse.sendRedirect()`
          //         - `OrbeonPortlet` creates `Redirect()` object
          //         - `BufferedPortlet.bufferedProcessAction()` sets new render parameters
          //         - portlet then renders with new path set by the redirection
          //
          // Questions/suggestions:
          //
          // - why do we handle the Ajax case differently in embedded vs. portlet modes?
          // - it's unclear which parts of the rewriting must take place here vs. in `sendRedirect()`
          // - make clearer what's stored with `addLoadToRun()` (`location` is not clear enough)
          //
          val skipRewrite =
            if (! containingDocument.isPortletContainer) {
              if (! containingDocument.isEmbeddedFromHeaders)
                false
              else
                containingDocument.initializing
            } else {
              // Portlet container
              // NOTE: As of 2016-03-17, the initialization case will fail and the portlet will throw an exception.
              true
            }
          XFormsCrossPlatformSupport.resolveRenderURL(
            containingDocument,
            currentElem.orNull,
            value,
            skipRewrite
          )
        }
      }

    // Force no progress indication if this is a JavaScript URL
    val effectiveIsShowProgress =
      if (externalURL.trim.startsWith("javascript:"))
        false
      else
        isShowProgress

    containingDocument.addLoadToRun(Load(externalURL, target, urlType, doReplace, effectiveIsShowProgress))
  }

  /**
   * Resolve f:url-norewrite attributes on this element, taking into account ancestor f:url-norewrite attributes for
   * the resolution.
   *
   * @param element element used to start resolution
   * @return true if rewriting is turned off, false otherwise
   */
  def resolveUrlNorewrite(element: Element): Boolean = {
    var currentElement = element
    do {
      val urlNorewriteAttribute = currentElement.attributeValue(XMLConstants.FORMATTING_URL_NOREWRITE_QNAME)
      // Return the first ancestor value found
      if (urlNorewriteAttribute != null)
        return "true" == urlNorewriteAttribute
      currentElement = currentElement.getParent
    } while (currentElement ne null)
    // Default is to rewrite
    false
  }
}
