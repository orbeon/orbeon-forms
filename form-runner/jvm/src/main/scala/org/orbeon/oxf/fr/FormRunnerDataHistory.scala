package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.SimpleDataMigration.{FormDiff, diffSimilarXmlData}
import org.orbeon.oxf.util.{DateUtils, DateUtilsUsingSaxon}
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.analysis.model.StaticBind
import org.orbeon.oxf.xforms.function.xxforms.XXFormsResourceSupport
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.{Constants, XFormsId}


trait FormRunnerDataHistory {

  //@XPathFunction
  def compareRfc1123AndIsoDates(rfc1123: String, iso: String): Boolean =
    DateUtilsUsingSaxon.tryParseISODateOrDateTime(iso, DateUtilsUsingSaxon.TimeZone.UTC).contains(DateUtils.parseRFC1123(rfc1123))

  //@XPathFunction
  def diffMessage(
    d1: om.NodeInfo,
    d2: om.NodeInfo
  ): om.NodeInfo = {

    val xfcd = inScopeContainingDocument

    val diffs =
      diffSimilarXmlData(
        d1,
        d2,
        isElementReadonly = _ => false)(
        formOps = new ContainingDocumentOps(
          xfcd,
          XFormsId.effectiveIdToAbsoluteId(Names.FormModel)
        )
      )(
        mapBind = (b: StaticBind) => {
          b.nameOpt
        }
      )

    val resourcesRootElem = {

      val currentLang = frc.currentLang

      xfcd.resolveObjectByIdInScope(Constants.DocumentId, Names.FormResources) collect {
        case i: XFormsInstance =>
          XXFormsResourceSupport.findResourceElementForLang(i.rootElement, currentLang) getOrElse
            (throw new IllegalArgumentException(s"missing resources element for lang `$currentLang`"))
      } getOrElse
        (throw new IllegalArgumentException(s"missing resources instance"))
    }

    def getLabelOrNull(name: String) =
      (resourcesRootElem / name / "label").headOption.map(_.stringValue).orNull

    import org.orbeon.scaxon.NodeConversions.elemToNodeInfo

    <_>
      {
        diffs map {
          case FormDiff.ValueChanged    (Some(name))        => <c l={getLabelOrNull(name)} t="value-changed"/>
          case FormDiff.IterationAdded  (Some(name), count) => <c l={getLabelOrNull(name)} t="iteration-added"   count={count.toString}/>
          case FormDiff.IterationRemoved(Some(name), count) => <c l={getLabelOrNull(name)} t="iteration-removed" count={count.toString}/>
          case FormDiff.ElementAdded    (Some(name))        => <c l={getLabelOrNull(name)} t="other-changed"/>
          case FormDiff.ElementRemoved  (Some(name))        => <c l={getLabelOrNull(name)} t="other-changed"/>
          case _ => <c/>
        }
      }
    </_>
  }
}
