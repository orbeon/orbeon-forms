package org.orbeon.oxf.fr.process

import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunnerPersistence.*
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsId
import org.orbeon.xml.NamespaceMapping


object FormRunnerActionApi {

  def controlSetvalue(
    controlName     : String,
    valueExpr       : String,
    sectionNameOpt  : Option[String],
    atOpt           : Option[String],
    namespaceMapping: NamespaceMapping = ProcessInterpreter.StandardNamespaceMapping,
  )(implicit
    xfc             : XFormsFunction.Context,
    logger          : IndentedLogger
  ): Unit = {

    val updatedValueExpr =
      FormRunnerRename.replaceVarReferencesWithFunctionCallsForAction(
      xpathString      = valueExpr,
      namespaceMapping = namespaceMapping,
      library          = process.SimpleProcess.xpathFunctionLibrary,
      avt              = false,
      libraryNameOpt   = None,     // TODO
      norewrite        = Set.empty // TODO: `$fr-form-model-vars`
    )

    val value =
      process.SimpleProcess.evaluateString(
        expr            = updatedValueExpr,
        item            = process.SimpleProcess.xpathContext,
        mapping         = namespaceMapping,
        functionContext = xfc
      )
    
    val modelAbsoluteId: String = XFormsId.effectiveIdToAbsoluteId(Names.FormModel)

    val selectedItems =
      atOpt match {
        case Some(at) =>

          val allItems =
            FormRunner.resolveTargetRelativeToActionSourceFromControlsFromBindOpt(
              xfc.container,
              xfc.modelOpt,
              xfc.sourceEffectiveId,
              modelAbsoluteId,
              controlName
            ).toList.flatMap(_.toList) // https://github.com/orbeon/orbeon-forms/issues/6016

          at match {
            case "start" => allItems.headOption.toList
            case "end"   => allItems.lastOption.toList
            case "all"   => allItems
            case i       => allItems.lift(i.toInt - 1).toList
          }

        case None =>
          FormRunner.resolveTargetRelativeToActionSourceOpt(
            actionSourceAbsoluteId  = modelAbsoluteId,
            targetControlName       = controlName,
            followIndexes           = true, // TODO
            libraryOrSectionNameOpt = sectionNameOpt.map(_.trimAllToEmpty).map(Right.apply),
          ).toList.flatMap(_.toList)
      }

    selectedItems
      .collect { case n: om.NodeInfo => List(n) }
      .foreach { ref =>
        XFormsAPI.setvalue(ref, value)
        XFormsAPI.delete(ref /@ TmpFileAttributeName) // https://github.com/orbeon/orbeon-forms/issues/5768
      }
  }
}
