package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.analysis.controls.{ComponentControl, LHHAAnalysis, StaticLHHASupport}
import org.orbeon.xforms.xbl.Scope

import scala.annotation.tailrec


object PartAnalysisSupport {

  // Attach this LHHA to its target control if any
  def attachToControl(findControlAnalysis: String => Option[ElementAnalysis], lhhaAnalysis: LHHAAnalysis): Unit = {

    val (targetControl, effectiveTargetControlOrPrefixedIdOpt) = {

      def searchLHHAControlInScope(scope: Scope, forStaticId: String): Option[StaticLHHASupport] =
        findControlAnalysis(scope.prefixedIdForStaticId(forStaticId)) collect { case e: StaticLHHASupport => e}

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
}
