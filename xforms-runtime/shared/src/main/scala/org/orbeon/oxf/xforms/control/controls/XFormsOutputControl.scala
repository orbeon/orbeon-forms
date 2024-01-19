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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.dom.{Element, QName}
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.externalcontext.UrlRewriteMode
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{PathUtils, URLRewriterUtils}
import org.orbeon.oxf.xforms.action.actions.XFormsLoadAction
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, OutputControl}
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.xforms.submission.{SubmissionHeaders, SubmissionUtils}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.xforms.Constants.DUMMY_IMAGE_URI
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.orbeon.xforms.XFormsNames._
import org.xml.sax.helpers.AttributesImpl

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

/**
 * Represents an xf:output control.
 */
class XFormsOutputControl(
  container : XBLContainer,
  parent    : XFormsControl,
  element   : Element,
  id        : String
) extends XFormsSingleNodeControl(container, parent, element, id)
  with XFormsValueControl
  with ReadonlySingleNodeFocusableTrait
  with VisitableTrait
  with FileMetadata {

  override type Control <: OutputControl

  def supportedFileMetadata: Seq[String] = Seq("mediatype", "filename") // could add "state"?

  // Optional format and mediatype
  private def format: Option[String] = staticControlOpt flatMap (_.format)

  // Value attribute
  private val valueAttributeOpt = element.attributeValueOpt(VALUE_QNAME)
  // TODO: resolve statically
  private val urlNorewrite = XFormsLoadAction.resolveUrlNorewrite(element)

  override def markDirtyImpl(): Unit ={
    super.markDirtyImpl()
    markFileMetadataDirty()
  }

  override def computeValue(collector: ErrorEventCollector): String =
    staticControlOpt flatMap (_.staticValue) getOrElse {

      val bc = bindingContext
      val value =
        valueAttributeOpt match {
          case Some(valueAttribute) =>
            // Value from the `value` attribute
            evaluateAsString(valueAttribute, bc.nodeset.asScala, bc.position, collector, "computing value")
          case None =>
            // Value from the binding
            bc.singleItemOpt map DataModel.getValue // using `singleItemOpt` directly so we can handle the case of a missing binding
      }

      val result = value getOrElse ""

      // This is ugly, but `evaluateFileMetadata` require that the value is set. If not, there will be an infinite loop.
      // We need to find a better solution.
      setValue(result)
      evaluateFileMetadata(isRelevant, collector)

      result
    }

  override def evaluateExternalValue(collector: ErrorEventCollector): Unit = {
    assert(isRelevant)

    val internalValue = getValue(collector)
    assert(internalValue ne null)

    val updatedValue =
      if (staticControlOpt exists (c => c.isDownloadAppearance || c.isVideoMediatype)) {
        proxyValueIfNeeded(internalValue, "", filename(collector), fileMediatype(collector) orElse mediatype, collector)
      } else if (staticControlOpt exists (_.isImageMediatype)) {
        // Use dummy image as default value so that client always has something to load
        proxyValueIfNeeded(internalValue, DUMMY_IMAGE_URI, filename(collector), fileMediatype(collector) orElse mediatype, collector)
      } else if (staticControlOpt exists (_.isHtmlMediatype)) {
        internalValue
      } else {
        // Other mediatypes
        valueAttributeOpt match {
          case Some(_) =>
            // There is a @value attribute, don't use format
            internalValue
          case None =>
            // There is a single-node binding, so the format may be used
            getValueUseFormat(format, collector) getOrElse internalValue
        }
      }

    setExternalValue(updatedValue)
  }

  // Keep public for unit tests
  def evaluatedHeaders(collector: ErrorEventCollector): Map[String, List[String]] = {

    // TODO: pass BindingContext directly
    getContextStack.setBinding(bindingContext)
    val headersToForward = SubmissionUtils.clientHeadersToForward(containingDocument.getRequestHeaders, forwardClientHeaders = true)

    EventCollector.withFailFastCollector(
      "evaluating headers",
      this,
      collector,
      Map.empty[String, List[String]]
    ) { failFastCollector =>
      SubmissionHeaders.evaluateHeaders(
        getEffectiveId,
        staticControl,
        headersToForward,
        this,
        failFastCollector
      )(getContextStack)
    }
  }

  private def proxyValueIfNeeded(
    internalValue: String,
    defaultValue : String,
    filename     : Option[String],
    mediatype    : Option[String],
    collector    : ErrorEventCollector
  ): String =
    try {
      // If the value is a file: we make sure it is signed otherwise we return the default value

      def verifiedValueOrDefault(initial: String, value: => String, default: => String) =
        if (PathUtils.getProtocol(initial) == "file" && ! XFormsUploadControl.verifyMAC(initial))
          default
        else
          value

      internalValue.trimAllToOpt match {
        case Some(trimmedInternalValue) =>
          getBuiltinTypeName match {
            case null | "anyURI" =>
              val (maybeResolved, maybeProxied) =
                if (! urlNorewrite) {
                  // Resolve xml:base and try to obtain a path which is an absolute path without the context

                  // NOTE: We also proxy data: URLs, even though we could send them to the client directly in many
                  // cases. One drawback is that they are are limited to 32 KB with IE8 (although IE8 support will
                  // go away soon hopefully, and IE 8 doesn't support the image annotation component which was the
                  // driver for data: URL support here), and in general make more sense for relatively short
                  // values. So for now we keep the proxying for data: URLs.

                  val resolvedURI =
                    URLRewriterUtils.rewriteResourceURL(
                      XFormsCrossPlatformSupport.externalContext.getRequest,
                      containingDocument.resolveXMLBase(element, trimmedInternalValue).toString,
                      URLRewriterUtils.getPathMatchers,
                      UrlRewriteMode.AbsolutePathNoContext
                    )

                  (
                    resolvedURI,
                    XFormsCrossPlatformSupport.proxyURI(
                      urlString        = resolvedURI,
                      filename         = filename,
                      contentType      = mediatype,
                      lastModified     = XFormsCrossPlatformSupport.getLastModifiedIfFast(resolvedURI),
                      customHeaders    = evaluatedHeaders(collector),
                      getHeader        = containingDocument.headersGetter
                    )
                  )
                } else {
                  // Otherwise we leave the value as is
                  (
                    trimmedInternalValue,
                    trimmedInternalValue
                  )
                }

              verifiedValueOrDefault(
                maybeResolved,
                maybeProxied,
                defaultValue
              )
            case "base64Binary" =>
              // NOTE: "-1" for `lastModified` will cause `XFormsAssetServer` to set `Last-Modified` and `Expires` properly to "now"
              XFormsCrossPlatformSupport.proxyBase64Binary(
                trimmedInternalValue,
                filename,
                mediatype,
                evaluatedHeaders(collector),
                containingDocument.headersGetter
              )
            case _ =>
              defaultValue
          }
        case None =>
          defaultValue
      }
    } catch {
      case NonFatal(t) =>
        // We don't want to fail if there is an issue proxying, for example if the resource is not found.
        // Ideally, this would indicate some condition on the control (not found? out of range?).
        warn(
          "exception while proxying value",
          Seq(
            "value"     -> internalValue,
            "throwable" -> OrbeonFormatter.format(t)
          )
        )

        defaultValue
    }

  protected override def getRelevantEscapedExternalValue(collector: ErrorEventCollector): String =
    if (staticControlOpt exists (c => c.isDownloadAppearance || c.isImageMediatype || c.isVideoMediatype)) {
      val externalValue = getExternalValue(collector)
      if (externalValue.nonAllBlank) {
        // External value is not blank, rewrite as absolute path. Two cases:
        // - URL is proxied:        /xforms-server/dynamic/27bf...  => [/context]/xforms-server/dynamic/27bf...
        // - URL is default value:  /ops/images/xforms/foo.gif      => [/context][/version]/ops/images/xforms/foo.gif
        XFormsCrossPlatformSupport.resolveResourceURL(containingDocument, element, externalValue, UrlRewriteMode.AbsolutePath)
      } else
        // Empty value, return as is
        externalValue
    } else if (staticControlOpt exists (_.isHtmlMediatype))
      // Rewrite the HTML value with resolved @href and @src attributes
      XFormsControl.getEscapedHTMLValue(getLocationData, getExternalValue(collector))
    else
      // Return external value as is
      getExternalValue(collector)

  override def getNonRelevantEscapedExternalValue: String =
    if (mediatype exists (_.startsWith("image/")))
      // Return rewritten URL of dummy image URL
      XFormsCrossPlatformSupport.resolveResourceURL(containingDocument, element, DUMMY_IMAGE_URI, UrlRewriteMode.AbsolutePath)
    else
      super.getNonRelevantEscapedExternalValue

  // If we have both @ref and @value, read the type
  // XForms doesn't specify this as of XForms 2.0, but we already read the other MIPs so it makes sense.
  override def valueType: QName = super.valueType

  // Often, `<xf:output>` just output text on the page and are not focusable. By this we mean that:
  // (1) We reject a focus event coming from the browser, and
  // (2) An `<xf:setfocus>` action on a container won't consider the `<xf:output>` as a candidate.
  //
  // However, there are at least 2 exceptions:
  // (a) If the `<xf:output>` has a label, we consider it as a field, so we should be able to tab through it
  //     as we do for other fields, for accessibility, so it should be included in (2) above.
  //     See https://github.com/orbeon/orbeon-forms/issues/3583
  // (b) If the `<xf:output>` contains HTML that is itself focusable, i.e. that users can tab to, as if we don't
  //     and users tab to that content, the server will refuse the tab and set back the focus on another another
  //     control. Right now, we "conservatively" allow any control that contains HTML to have the focus, but to be
  //     more correct, we should restrict this to focusable HTML content.
  //     See https://github.com/orbeon/orbeon-forms/issues/4773
  override def isDirectlyFocusableMaybeWithToggle: Boolean =
    (staticControl.hasLHHA(LHHA.Label) || staticControl.isHtmlMediatype) &&
    super.isDirectlyFocusableMaybeWithToggle

  override def addAjaxExtensionAttributes(
    attributesImpl    : AttributesImpl,
    previousControlOpt: Option[XFormsControl],
    collector         : ErrorEventCollector
  ): Boolean = {
    var added = super.addAjaxExtensionAttributes(attributesImpl, previousControlOpt, collector)
    added |= addFileMetadataAttributes(attributesImpl, previousControlOpt.asInstanceOf[Option[FileMetadata]], collector)
    added
  }

  override def compareExternalUseExternalValue(
    previousExternalValue: Option[String],
    previousControl      : Option[XFormsControl],
    collector            : ErrorEventCollector
  ): Boolean =
    previousControl match {
      case Some(other: XFormsOutputControl) =>
        compareFileMetadata(other, collector) &&
        super.compareExternalUseExternalValue(previousExternalValue, previousControl, collector)
      case _ => false
    }

  override def findAriaByControlEffectiveIdWithNs: Option[String] =
    if (appearances(XXFORMS_TEXT_APPEARANCE_QNAME) ||
        (staticControlOpt exists (c => c.isDownloadAppearance || c.isImageMediatype || c.isVideoMediatype || c.isHtmlMediatype)))
      None
    else
      super.findAriaByControlEffectiveIdWithNs

  override def getBackCopy(collector: ErrorEventCollector): AnyRef = {
    val cloned = super.getBackCopy(collector).asInstanceOf[XFormsOutputControl]
    updateFileMetadataCopy(cloned, collector)
    cloned
  }
}

object XFormsOutputControl {

  def getExternalValueOrDefault(
    control       : XFormsOutputControl,
    mediatypeValue: String,
    collector     : ErrorEventCollector
  ): String =
    if ((control ne null) && control.isRelevant)
      // Ask control
      control.getExternalValue(collector)
    else if ((mediatypeValue ne null) && mediatypeValue.startsWith("image/"))
      // Dummy image
      DUMMY_IMAGE_URI
    else if ((mediatypeValue ne null) && mediatypeValue.startsWith("video/"))
      ""
    else
      // Default for other mediatypes
      null
}