package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.xforms.analysis.controls.{AttributeControl, ComponentControl, LHHAAnalysis, StaticLHHASupport}
import org.orbeon.oxf.xml.XMLConstants.XML_LANG_QNAME
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.XFormsNames.FOR_QNAME
import org.orbeon.xforms.xbl.Scope

import scala.annotation.tailrec


sealed trait LhhaPlacementType { val directTargetControl: StaticLHHASupport; val lhhaControlRef: LhhaControlRef }
object LhhaPlacementType {
  case class Local   (directTargetControl: StaticLHHASupport, lhhaControlRef: LhhaControlRef)                             extends LhhaPlacementType
  case class External(directTargetControl: StaticLHHASupport, lhhaControlRef: LhhaControlRef, repeatNesting: Option[Int]) extends LhhaPlacementType
}

sealed trait LhhaControlRef
object LhhaControlRef {
  case class Control   (targetControl: StaticLHHASupport) extends LhhaControlRef
  case class PrefixedId(targetPrefixedId: String)         extends LhhaControlRef
}

object PartAnalysisSupport {

  // Attach this LHHA to its target control if any
  def attachToControl(findControlAnalysis: String => Option[ElementAnalysis], lhhaAnalysis: LHHAAnalysis): Unit = {

    val forStaticIdOpt = lhhaAnalysis.element.attributeValueOpt(FOR_QNAME)

    def searchLHHAControlInScope(scope: Scope, forStaticId: String): Option[StaticLHHASupport] =
      findControlAnalysis(scope.prefixedIdForStaticId(forStaticId)) collect { case e: StaticLHHASupport => e }

    @tailrec
    def searchWithXblLabelFor(staticLhhaSupport: StaticLHHASupport): LhhaControlRef =
      staticLhhaSupport match {
        case cc: ComponentControl =>
          cc.commonBinding.labelFor match {
            case Some(nestedLabelForStaticId) =>
              searchLHHAControlInScope(cc.bindingOrThrow.innerScope, nestedLabelForStaticId) match {
                case Some(nestedLabelForTarget) => searchWithXblLabelFor(nestedLabelForTarget)
                case None                       => LhhaControlRef.PrefixedId(cc.bindingOrThrow.innerScope.fullPrefix + nestedLabelForStaticId)
              }
            case None =>
              LhhaControlRef.Control(cc)
          }
        case _ =>
          LhhaControlRef.Control(staticLhhaSupport)
      }

    def isStaticIdOfParent(staticId: String) =
      lhhaAnalysis.getParent match {
        case e: StaticLHHASupport if e.staticId == staticId => true
        case _ => false
      }

    val lhhaPlacementType =
      forStaticIdOpt match {
        case Some(forStaticId) if ! isStaticIdOfParent(forStaticId) => // also handle case of a nested with `for` for parent

          val directTargetControl =
            searchLHHAControlInScope(lhhaAnalysis.scope, forStaticId) getOrElse (
              throw new ValidationException(
                s"`for` attribute with value `$forStaticId` doesn't point to a control supporting label, help, hint or alert.",
                ElementAnalysis.createLocationData(lhhaAnalysis.element)
              )
            )

          // NOTE: We don't support a reference to an effective control within an XBL which is in a repeat nested within the XBL!
          val repeatNesting = directTargetControl.ancestorRepeats.size - lhhaAnalysis.ancestorRepeats.size
          LhhaPlacementType.External(directTargetControl, searchWithXblLabelFor(directTargetControl), repeatNesting > 0 option repeatNesting)
        case _ =>
          // Directly nested LHHA

          val directTargetControl =
            lhhaAnalysis.getParent match {
              case e: StaticLHHASupport => e
              case _ =>
                throw new ValidationException(
                  s"parent control must support label, help, hint or alert.",
                  ElementAnalysis.createLocationData(lhhaAnalysis.element)
                )
            }

          LhhaPlacementType.Local(directTargetControl, searchWithXblLabelFor(directTargetControl))
      }

    lhhaAnalysis.lhhaPlacementType = lhhaPlacementType

    // We attach the LHHA to one, and possibly two target controls:
    // 1. The control to which the LHHA is directly attached, via `for` or direct nesting
    lhhaPlacementType.directTargetControl.attachDirectLhha(lhhaAnalysis)

    // 2. The control to which the LHHA is attached indirectly via `xxbl:label-for`, provided it's not the same control
    //    we already attached the LHHA to.
    lhhaPlacementType.lhhaControlRef match {
      case LhhaControlRef.Control(targetControl) if targetControl ne lhhaPlacementType.directTargetControl =>
        targetControl.attachByLhha(lhhaAnalysis)
      case _ =>
    }
  }

  def extractXMLLang(
    getAttributeControl: (String, String) => AttributeControl,
    elementAnalysis    : ElementAnalysis,
    lang               : String
  ): LangRef =
    if (! lang.startsWith("#"))
      LangRef.Literal(lang)
    else {
      val staticId   = lang.substring(1)
      val prefixedId = XFormsId.getRelatedEffectiveId(elementAnalysis.prefixedId, staticId)
      LangRef.AVT(getAttributeControl(prefixedId, "xml:lang"))
    }

  // This only sets the `lang` value on elements that have directly an `xml:lang`.
  // Other elements will get their `lang` value lazily.
  def setLangOnElement(
    getAttributeControl: (String, String) => AttributeControl,
    elementAnalysis    : ElementAnalysis
  ): Unit =
    elementAnalysis.lang =
      elementAnalysis.element.attributeValueOpt(XML_LANG_QNAME) match {
        case Some(v) => extractXMLLang(getAttributeControl, elementAnalysis, v)
        case None    => LangRef.Undefined
      }
}
