package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xforms.XFormsElementValue
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis.isHTML
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, ElementAnalysisTreeBuilder, PartAnalysisImpl}
import org.orbeon.oxf.xml.dom.Extensions.DomElemOps
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames.{APPEARANCE_QNAME, XFORMS_FULL_APPEARANCE_QNAME, XFORMS_MINIMAL_APPEARANCE_QNAME, XFORMS_NAMESPACE_URI, XXFORMS_DOWNLOAD_APPEARANCE_QNAME, XXFORMS_LEFT_APPEARANCE_QNAME, XXFORMS_PLACEHOLDER_APPEARANCE_QNAME}
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping

import scala.annotation.tailrec

object OutputControlBuilder {

  def apply(
    part            : PartAnalysisImpl,
    index           : Int,
    element         : Element,
    parent          : Option[ElementAnalysis],
    preceding       : Option[ElementAnalysis],
    scope           : Scope
  ): OutputControl = {

    // TODO: This All this could be passed at construction
    val staticId  : String = element.idOrNull
    val prefixedId: String = scope.prefixedIdForStaticId(staticId) // NOTE: we could also pass the prefixed id during construction
    val containerScope: Scope = part.containingScope(prefixedId)
    val namespaceMapping: NamespaceMapping = part.metadata.getNamespaceMapping(prefixedId).orNull

    // TODO: Duplication in trait
    val appearances: Set[QName]     = ElementAnalysis.attQNameSet(element, APPEARANCE_QNAME, namespaceMapping)

    // Control-specific
    val isImageMediatype    : Boolean = element.attributeValueOpt("mediatype") exists (_.startsWith("image/"))
    val isHtmlMediatype     : Boolean = element.attributeValueOpt("mediatype") contains "text/html"
    val isDownloadAppearance: Boolean = appearances.contains(XXFORMS_DOWNLOAD_APPEARANCE_QNAME)

    val staticValue: Option[String] =
      (! isImageMediatype && ! isDownloadAppearance && ElementAnalysisTreeBuilder.hasStaticValue(element)) option
        XFormsElementValue.getStaticChildElementValue(containerScope.fullPrefix, element, acceptHTML = true, null)

    new OutputControl(
      part,
      index,
      element,
      parent,
      preceding,
      scope,
      isImageMediatype,
      isHtmlMediatype,
      isDownloadAppearance,
      staticValue
    )
  }
}

object LHHAAnalysisBuilder {

  def apply(
    part            : PartAnalysisImpl,
    index           : Int,
    element         : Element,
    parent          : Option[ElementAnalysis],
    preceding       : Option[ElementAnalysis],
    scope           : Scope
  ): LHHAAnalysis = {

    // TODO: This All this could be passed at construction
    val staticId  : String = element.idOrNull
    val prefixedId: String = scope.prefixedIdForStaticId(staticId) // NOTE: we could also pass the prefixed id during construction
    val containerScope: Scope = part.containingScope(prefixedId)
    val namespaceMapping: NamespaceMapping = part.metadata.getNamespaceMapping(prefixedId).orNull

    // TODO: Duplication in trait
    val appearances: Set[QName]     = ElementAnalysis.attQNameSet(element, APPEARANCE_QNAME, namespaceMapping)

    // TODO: make use of static value
    //
    // - output static value in HTML markup
    // - if has static value, don't attempt to compare values upon diff, and never send new related information to client
    // - 2017-10-17: Now using this in `XFormsLHHAControl`.
    //
    // TODO: figure out whether to allow HTML or not (could default to true?)
    //
    val staticValue: Option[String] =
      ElementAnalysisTreeBuilder.hasStaticValue(element) option
        XFormsElementValue.getStaticChildElementValue(containerScope.fullPrefix, element, acceptHTML = true, null)

    val lhhaType: LHHA =
      LHHA.withNameOption(element.getName) getOrElse
        LHHA.Label // FIXME: Because `SelectionControlTrait` calls this for `value`!

    val hasLocalMinimalAppearance: Boolean =
      appearances(XFORMS_MINIMAL_APPEARANCE_QNAME) || appearances(XXFORMS_PLACEHOLDER_APPEARANCE_QNAME)
    val hasLocalFullAppearance   : Boolean = appearances(XFORMS_FULL_APPEARANCE_QNAME)
    val hasLocalLeftAppearance   : Boolean = appearances(XXFORMS_LEFT_APPEARANCE_QNAME)

    // Placeholder is only supported for label or hint. This in fact only makes sense for a limited set
    // of controls, namely text fields or text areas at this point.
    val isPlaceholder: Boolean =
      lhhaType match {
        case LHHA.Label | LHHA.Hint =>
          hasLocalMinimalAppearance || (
            ! hasLocalFullAppearance &&
              part.staticState.staticStringProperty(
                if (lhhaType == LHHA.Hint) HintAppearanceProperty else LabelAppearanceProperty
              )
            .tokenizeToSet.contains(XFORMS_MINIMAL_APPEARANCE_QNAME.localName)
          )
        case _ => false
      }

    new LHHAAnalysis(
      part,
      index,
      element,
      parent,
      preceding,
      scope,
      staticValue,
      isPlaceholder,
      containsHTML(element)
    )
  }

  // Attach this LHHA to its target control if any
  def attachToControl(part: PartAnalysisImpl, lhhaAnalysis: LHHAAnalysis): Unit = {

    val (targetControl, effectiveTargetControlOrPrefixedIdOpt) = {

      def searchLHHAControlInScope(scope: Scope, forStaticId: String): Option[StaticLHHASupport] =
        part.findControlAnalysis(scope.prefixedIdForStaticId(forStaticId)) collect { case e: StaticLHHASupport => e}

      @tailrec
      def searchXblLabelFor(e: StaticLHHASupport): Option[StaticLHHASupport Either String] =
        e match {
          case xbl: ComponentControl =>
            xbl.commonBinding.labelFor match {
              case Some(nestedLabelForStaticId) =>
                searchLHHAControlInScope(xbl.bindingOrThrow.innerScope, nestedLabelForStaticId) match {
                  case Some(nestedLabelForTarget) => searchXblLabelFor(nestedLabelForTarget) // recurse
                  case None                       => Some(Right(xbl.bindingOrThrow.innerScope.fullPrefix + nestedLabelForStaticId)) // assuming id of an HTML element
                }
              case None =>
                Some(Left(xbl))
            }
          case _ =>
            Some(Left(e))
        }

      def initialElemFromForOpt =
        lhhaAnalysis.forStaticIdOpt map  { forStaticId =>
          searchLHHAControlInScope(lhhaAnalysis.scope, forStaticId) getOrElse (
            throw new ValidationException(
              s"`for` attribute with value `$forStaticId` doesn't point to a control supporting label, help, hint or alert.",
              ElementAnalysis.createLocationData(lhhaAnalysis.element)
            )
          )
        }

      val initialElem = initialElemFromForOpt getOrElse {
        lhhaAnalysis.getParent match {
          case e: StaticLHHASupport => e
          case _ =>
            throw new ValidationException(
              s"parent control must support label, help, hint or alert.",
              ElementAnalysis.createLocationData(lhhaAnalysis.element)
            )
        }
      }

      (initialElem, searchXblLabelFor(initialElem))
    }

    // NOTE: We don't support a reference to an effective control within an XBL which is in a repeat nested within the XBL!
    val repeatNesting = targetControl.ancestorRepeats.size - lhhaAnalysis.ancestorRepeats.size

    lhhaAnalysis._isForRepeat                           = ! lhhaAnalysis.isLocal && repeatNesting > 0
    lhhaAnalysis._forRepeatNesting                      = if (lhhaAnalysis._isForRepeat && repeatNesting > 0) repeatNesting else 0
    lhhaAnalysis._directTargetControlOpt                = Some(targetControl)
    lhhaAnalysis._effectiveTargetControlOrPrefixedIdOpt = effectiveTargetControlOrPrefixedIdOpt

    // We attach the LHHA to one, and possibly two target controls
    targetControl.attachLHHA(lhhaAnalysis)
    effectiveTargetControlOrPrefixedIdOpt foreach {
      _.left.toOption filter (_ ne targetControl) foreach (_.attachLHHABy(lhhaAnalysis))
    }
  }

  private def containsHTML(lhhaElement: Element) = {

    val lhhaElem =
      new DocumentWrapper(
          lhhaElement.getDocument,
          null,
          XPath.GlobalConfiguration
        ).wrap(lhhaElement)

    val XFOutput = URIQualifiedName(XFORMS_NAMESPACE_URI, "output")

    val descendantOtherElems = lhhaElem descendant * filter (_.uriQualifiedName != XFOutput)
    val descendantOutputs    = lhhaElem descendant XFOutput

    isHTML(lhhaElement) || descendantOtherElems.nonEmpty || (descendantOutputs exists {
      _.attValueOpt("mediatype") contains "text/html"
    })
  }
}