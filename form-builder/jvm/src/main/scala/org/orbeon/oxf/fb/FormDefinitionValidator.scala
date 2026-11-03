/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.fb

import java.text.MessageFormat
import enumeratum.EnumEntry.Lowercase
import enumeratum.*
import org.orbeon.dom.QName
import org.orbeon.oxf.fb.XMLNames.*
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.XMLNames.*
import org.orbeon.oxf.fr.permission.*
import org.orbeon.oxf.fr.{FormRunner, FormRunnerDocContext, Names, NodeInfoCell}
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, XPath}
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocumentOpt
import org.orbeon.oxf.xforms.analysis.model.DependencyAnalyzer
import org.orbeon.oxf.xforms.analysis.model.DependencyAnalyzer.BindNode
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.oxf.xml.{SaxonUtils, ShareableXPathStaticContext}
import org.orbeon.saxon.expr.{Expression, Literal, StringLiteral}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om.{NodeInfo, StructuredQName}
import org.orbeon.saxon.value.BooleanValue
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsNames
import org.orbeon.xforms.XFormsNames.*
import org.orbeon.xml.NamespaceMapping

import scala.collection.immutable
import scala.util.{Failure, Success, Try}


sealed trait ValidationSeverity extends EnumEntry with Lowercase

object ValidationSeverity extends Enum[ValidationSeverity] {

  val values: immutable.IndexedSeq[ValidationSeverity] = findValues

  case object Error   extends ValidationSeverity
  case object Warning extends ValidationSeverity
  case object Info    extends ValidationSeverity
}

sealed trait DataCompatibilityStatus extends EnumEntry with Lowercase

object DataCompatibilityStatus extends Enum[DataCompatibilityStatus] {

  val values: immutable.IndexedSeq[DataCompatibilityStatus] = findValues

  case object Compatible        extends DataCompatibilityStatus
  case object MigrationRequired extends DataCompatibilityStatus
  case object Incompatible      extends DataCompatibilityStatus
}

sealed trait ValidationIssue {
  def severity   : ValidationSeverity
  def blocking   : Boolean
  def code       : String
  def controlName: Option[String]
}

object ValidationIssue {

  case class CircularDependency(
    mipName     : String,
    controlNames: List[String]
  ) extends ValidationIssue {
    val severity   : ValidationSeverity = ValidationSeverity.Error
    val blocking   : Boolean            = true
    val code       : String             = s"circular-dependency-$mipName"
    val controlName: Option[String]     = controlNames.headOption
  }

  case class InvalidXPathExpression(
    controlName: Option[String],
    location   : String,
    expression : String
  ) extends ValidationIssue {
    val severity: ValidationSeverity = ValidationSeverity.Error
    val blocking: Boolean            = false
    val code    : String             = "invalid-xpath"
  }

  case class UnresolvedVariableReference(
    controlName : Option[String],
    location    : String,
    variableName: String
  ) extends ValidationIssue {
    val severity: ValidationSeverity = ValidationSeverity.Error
    val blocking: Boolean            = false
    val code    : String             = "unresolved-variable"
  }

  case class UnresolvedControlReference(
    controlName          : Option[String],
    location             : String,
    referencedControlName: String
  ) extends ValidationIssue {
    val severity: ValidationSeverity = ValidationSeverity.Error
    val blocking: Boolean            = false
    val code    : String             = "unresolved-control"
  }

  case class TokenWithoutPermissions(
    controlName      : Option[String],
    location         : String,
    mode             : String,
    missingOperations: Set[Operation]
  ) extends ValidationIssue {
    val severity: ValidationSeverity = ValidationSeverity.Warning
    val blocking: Boolean            = false
    val code    : String             = "token-without-permissions"
  }

  case class ContainerRepeatStatusChanged(
    containerName: String,
    wasRepeated  : Boolean,
    isRepeated   : Boolean
  ) extends ValidationIssue {
    val severity   : ValidationSeverity = ValidationSeverity.Warning
    val blocking   : Boolean            = false
    val code       : String             = "container-repeat-changed"
    val controlName: Option[String]     = Some(containerName)
  }

  case class ControlRepeatAncestryChanged(
    controlNameValue: String,
    fromRepeats     : List[String],
    toRepeats       : List[String]
  ) extends ValidationIssue {
    val severity   : ValidationSeverity = ValidationSeverity.Warning
    val blocking   : Boolean            = false
    val code       : String             = "control-repeat-ancestry-changed"
    val controlName: Option[String]     = Some(controlNameValue)
  }

  case class IncompatibleDatatypeChange(
    controlNameValue: String,
    fromDatatype    : String,
    toDatatype      : String
  ) extends ValidationIssue {
    val severity   : ValidationSeverity = ValidationSeverity.Warning
    val blocking   : Boolean            = false
    val code       : String             = "incompatible-datatype-change"
    val controlName: Option[String]     = Some(controlNameValue)
  }

  case class DataMigrationRequired(
    addedNames  : List[String],
    removedNames: List[String],
    movedNames  : List[String]
  ) extends ValidationIssue {
    val severity   : ValidationSeverity = ValidationSeverity.Warning
    val blocking   : Boolean            = false
    val code       : String             = "data-migration-required"
    val controlName: Option[String]     = None
  }

  case class StructuralValidationError(
    message         : String,
    code            : String         = "structural-error",
    controlName     : Option[String] = None
  ) extends ValidationIssue {
    val severity: ValidationSeverity = ValidationSeverity.Error
    val blocking: Boolean            = true
  }
}

object FormDefinitionValidator {

  implicit private val Logger: IndentedLogger =
    new IndentedLogger(LoggerFactory.createLogger(FormDefinitionValidator.getClass))

  val ValidationSeverity     : org.orbeon.oxf.fb.ValidationSeverity.type      = org.orbeon.oxf.fb.ValidationSeverity
  val DataCompatibilityStatus: org.orbeon.oxf.fb.DataCompatibilityStatus.type = org.orbeon.oxf.fb.DataCompatibilityStatus
  val ValidationIssue        : org.orbeon.oxf.fb.ValidationIssue.type         = org.orbeon.oxf.fb.ValidationIssue
  import ValidationIssue.*

  private val ParamElemTest      : Test = "param"       || (FR -> "param")
  private val NameElemTest       : Test = "name"        || (FR -> "name")
  private val ControlNameElemTest: Test = "controlName" || (FR -> "controlName")
  private val ExprElemTest       : Test = "expr"        || (FR -> "expr")
  private val TokenElemTest      : Test = "token"       || (FR -> "token")
  private val TypeElemTest       : Test = "type"        || (FR -> "type")

  private val FRControlStringValueName = new StructuredQName(FRPrefix, FR, "control-string-value")
  private val FRControlTypedValueName  = new StructuredQName(FRPrefix, FR, "control-typed-value")
  private val FRFormRunnerLinkName     = new StructuredQName(FRPrefix, FR, "form-runner-link")

  private val StandardVariableNames: Set[String] = Set(
    "form-resources",
    "fr-resources",
    "fr-mode",
    "fr-lang",
    "fr-search-value",
    "fr-search-page"
  )

  private val LegacyActionExpressionVarNames: Set[String] = Set(
    "control-value",
    "response-items",
    "item-label",
    "item-value",
    "item-hint"
  )

  private val BindFormulaAttTest: Test =
    FormulaTest                       ||
    XFormsNames.CONSTRAINT_QNAME      ||
    XFormsNames.XXFORMS_DEFAULT_QNAME ||
    QName("default")                  ||
    XFormsNames.RELEVANT_QNAME        ||
    XFormsNames.CALCULATE_QNAME       ||
    XFormsNames.READONLY_QNAME

  private val NestedBindFormulaElemTest: Test =
    FBConstraintTest ||
    XFConstraintTest ||
    FBRequiredTest   ||
    XFRequiredTest

  private val ServiceSubmissionTest: Test =
    FBSubmissionTest || (XF -> "submission")

  private val ModelVarTest: Test =
    (XF -> "var") || (XF -> "variable") || (XXF -> "var") || (XXF -> "variable")

  private val ResourcePointerElemTest: Test =
    XFLabelTest                 ||
    XFHintTest                  ||
    XFHelpTest                  ||
    XFAlertTest                 ||
    FRTextQName                 ||
    FRShortLabelQName           ||
    FRIterationLabelQName       ||
    FRAddIterationLabelQName    ||
    FRRemoveIterationLabelQName ||
    XFItemsetTest

  private val FormResourceRefRegex = """\$form-resources/([A-Za-z0-9_\-.]+)/""".r

  // Safe context wrapper over a form definition root element
  private class SafeFormDocContext(val formDefinitionRootElem: NodeInfo) extends FormRunnerDocContext {
    lazy val viewContainerElemOpt: Option[NodeInfo] = bodyElemOpt
    lazy val formInstanceElemOpt : Option[NodeInfo] =
      modelElemOpt.flatMap(frc.instanceElemFromModelElem(_, Names.FormInstance))
  }

  def validateFormDefinition(formDefinitionRootElem: NodeInfo): List[ValidationIssue] = {
    val ctx = new SafeFormDocContext(formDefinitionRootElem.rootElement)

    checkCircularDependencies(ctx) ++
      checkFormulasAndReferences(ctx) ++
      checkResourcePointerControlNames(ctx)
  }

  // Check whether setting `mipName` (`"calculate"` or `"default"`) on `originalControlNameOpt` (possibly renamed to `controlName`)
  // to `expressionOpt` introduces a circular dependency involving this control.
  def hasCircularDependencyForControl(
    formDefinitionRootElem: NodeInfo,
    controlName           : String,
    mipName               : String,
    expressionOpt         : Option[String],
    originalControlNameOpt: Option[String] = None
  ): Boolean = {
    val ctx           = new SafeFormDocContext(formDefinitionRootElem.rootElement)
    val effectiveName = controlName.trimAllToOpt.orElse(originalControlNameOpt.flatMap(_.trimAllToOpt)).getOrElse(controlName)
    val origName      = originalControlNameOpt.flatMap(_.trimAllToOpt).getOrElse(effectiveName)
    findCircularDependencyForMip(
      ctx         = ctx,
      mipName     = mipName,
      overrideOpt = Some((origName, effectiveName, expressionOpt))
    ).exists(_.contains(effectiveName))
  }

  def checkCircularDependencies(formDefinitionRootElem: NodeInfo): List[CircularDependency] =
    checkCircularDependencies(new SafeFormDocContext(formDefinitionRootElem.rootElement))

  private def checkCircularDependencies(ctx: SafeFormDocContext): List[CircularDependency] =
    List("calculate", "default").flatMap { mipName =>
      findCircularDependencyForMip(ctx, mipName, overrideOpt = None).map(CircularDependency(mipName, _))
    }

  private def findCircularDependencyForMip(
    ctx        : SafeFormDocContext,
    mipName    : String,
    overrideOpt: Option[(String, String, Option[String])]
  ): Option[List[String]] = {

    val allBinds = ctx.topLevelBindElemOpt.toList descendant (XFBindTest || FBBindTest)

    def rawMipValueOpt(bindElem: NodeInfo): Option[String] =
      mipName match {
        case "calculate" =>
          bindElem.attValueOpt(XFormsNames.CALCULATE_QNAME)
            .orElse(bindElem.attValueOpt(QName("calculate", FBNamespace)))
        case "default" =>
          bindElem.attValueOpt(XFormsNames.XXFORMS_DEFAULT_QNAME)
            .orElse(bindElem.attValueOpt(QName("default", FBNamespace)))
            .orElse(bindElem.attValueOpt("default"))
        case _ =>
          None
      }

    val fnLibrary = currentFunctionLibrary

    val bindNodes: List[BindNode[String]] =
      allBinds.flatMap { bindElem =>
        val rawNameOpt =
          bindElem.attValueOpt("name")
            .flatMap(_.trimAllToOpt)
            .orElse(frc.controlNameFromIdOpt(bindElem.id))

        val (effectiveNameOpt, exprStringOpt) =
          overrideOpt match {
            case Some((origName, newName, overrideExprOpt)) if rawNameOpt.contains(origName) =>
              (Some(newName), overrideExprOpt.flatMap(_.trimAllToOpt))
            case _ =>
              (rawNameOpt, rawMipValueOpt(bindElem).flatMap(_.trimAllToOpt))
          }

        for {
          bindName   <- effectiveNameOpt
          exprString <- exprStringOpt
        } yield {
          val refs =
            Try(
              compileExpression(
                xpathString      = exprString,
                namespaceMapping = NamespaceMapping(bindElem.namespaceMappings.toMap),
                functionLibrary  = fnLibrary,
                avt              = false
              )
            ).toOption.toList
              .flatMap(expr => SaxonUtils.iterateExternalVariableReferences(expr))
              .map { refName =>
                overrideOpt match {
                  case Some((origName, newName, _)) if refName == origName => newName
                  case _                                                   => refName
                }
              }
              .toSet

          BindNode(bindName, Some(bindName), refs)
        }
      }.toList

    DependencyAnalyzer.sortTopologically(bindNodes).left.toOption
  }

  private def checkFormulasAndReferences(ctx: SafeFormDocContext): List[ValidationIssue] = {

    val fnLibrary = currentFunctionLibrary

    val validControlNames: Set[String] = {
      val namesFromBodyIds =
        ctx.viewContainerElemOpt.toList
          .descendantOrSelf(*)
          .flatMap(_.idOpt)
          .flatMap(frc.controlNameFromIdOpt)
          .filterNot(_.endsWith(frc.DefaultIterationSuffix))

      val namesFromBinds =
        ctx.topLevelBindElemOpt.toList
          .descendant(XFBindTest || FBBindTest)
          .flatMap { bind =>
            bind.attValueOpt("name").flatMap(_.trimAllToOpt)
              .orElse(frc.controlNameFromIdOpt(bind.id))
          }
          .filterNot(_.endsWith(frc.DefaultIterationSuffix))

      (namesFromBodyIds ++ namesFromBinds).toSet
    }

    val allBindNames: Set[String] =
      ctx.topLevelBindElemOpt.toList
        .descendant(XFBindTest || FBBindTest)
        .flatMap { bind =>
          bind.attValueOpt("name").flatMap(_.trimAllToOpt)
            .orElse(frc.controlNameFromIdOpt(bind.id))
        }
        .toSet

    val modelVarNames: Set[String] =
      (ctx.modelElemOpt.toList / ModelVarTest).flatMap(_.attValueOpt("name")).flatMap(_.trimAllToOpt).toSet

    val validVariableNames: Set[String] =
      validControlNames ++ allBindNames ++ StandardVariableNames ++ modelVarNames

    val permissions: Permissions =
      Try(PermissionsXML.parse(ctx.metadataRootElemOpt.flatMap(_.child("permissions").headOption))).toOption match {
        case Some(defined: Permissions.Defined) => Permissions.normalizePermissions(defined)
        case Some(undefined)                    => undefined
        case None                               => Permissions.Undefined
      }

    def checkTokenPermission(
      controlNameOpt: Option[String],
      location      : String,
      mode          : String
    ): Option[TokenWithoutPermissions] = {
      val requiredOps: Set[Operation] =
        mode match {
          case "edit"         => Set(Operation.Read, Operation.Update)
          case "view" | "pdf" => Set(Operation.Read)
          case _              => Set.empty
        }

      if (requiredOps.isEmpty) {
        None
      } else {
        val grantedTokenOps = PermissionsAuthorization.possiblyAllowedTokenOperations(permissions, Some(requiredOps))
        val missingOps      = requiredOps.filterNot(op => Operations.allows(grantedTokenOps, op))
        missingOps.nonEmpty.option(
          TokenWithoutPermissions(
            controlName       = controlNameOpt,
            location          = location,
            mode              = mode,
            missingOperations = missingOps
          )
        )
      }
    }

    def localAncestorVars(elemOrAtt: NodeInfo): Set[String] = {
      val startElem = if (elemOrAtt.isAttribute) elemOrAtt.parentUnsafe else elemOrAtt
      startElem
        .ancestorOrSelf(*)
        .flatMap(ancestor => (ancestor precedingSibling ModelVarTest) ++ (ancestor / ModelVarTest))
        .flatMap(_.attValueOpt("name"))
        .flatMap(_.trimAllToOpt)
        .toSet
    }

    def validateExpression(
      elemOrAtt     : NodeInfo,
      avt           : Boolean,
      controlNameOpt: Option[String],
      location      : String,
      extraValidVars: Set[String] = Set.empty
    ): List[ValidationIssue] =
      elemOrAtt.stringValue.trimAllToOpt match {
        case None =>
          Nil
        case Some(xpathString) if avt && ! xpathString.contains("{") && ! xpathString.contains("}") =>
          Nil
        case Some(xpathString) =>
          Try(
            compileExpression(
              xpathString      = xpathString,
              namespaceMapping = NamespaceMapping(elemOrAtt.namespaceMappings.toMap),
              functionLibrary  = fnLibrary,
              avt              = avt
            )
          ) match {
            case Failure(_) =>
              List(InvalidXPathExpression(controlNameOpt, location, xpathString))
            case Success(expr) =>
              val inScopeVars = validVariableNames ++ extraValidVars ++ localAncestorVars(elemOrAtt)

              val unresolvedVars =
                if (xpathString.contains("$"))
                  DependencyAnalyzer
                    .findMissingVariableReferences(expr, inScopeVars)
                    .toList
                    .distinct
                    .map(UnresolvedVariableReference(controlNameOpt, location, _))
                else
                  Nil

              val unresolvedFnControls =
                (SaxonUtils.iterateFunctions(expr, FRControlStringValueName) ++
                  SaxonUtils.iterateFunctions(expr, FRControlTypedValueName))
                  .toList
                  .flatMap { fn =>
                    val args = fn.getArguments
                    if (args.length == 1) {
                      args(0) match {
                        case s: StringLiteral => List(s.getStringValue)
                        case _                => Nil
                      }
                    } else if (args.length == 2) {
                      (args(0), args(1)) match {
                        case (s: StringLiteral, l: Literal) if l.getValue == BooleanValue.FALSE =>
                          List(s.getStringValue)
                        case (s: StringLiteral, _) if args(1).toString == "false()" =>
                          List(s.getStringValue)
                        case _ =>
                          Nil
                      }
                    } else if (args.length == 3) {
                      // 3rd argument is section template container name in the main form
                      args(2) match {
                        case s: StringLiteral => List(s.getStringValue)
                        case _                => Nil
                      }
                    } else {
                      Nil
                    }
                  }
                  .distinct
                  .filterNot(validControlNames)
                  .map(UnresolvedControlReference(controlNameOpt, location, _))

              val tokenIssues =
                SaxonUtils
                  .iterateFunctions(expr, FRFormRunnerLinkName)
                  .toList
                  .flatMap { fn =>
                    val args = fn.getArguments
                    val hasToken =
                      args.length >= 2 && (
                        args(1) match {
                          case l: Literal if l.getValue == BooleanValue.TRUE => true
                          case _                                             => false
                        }
                      )
                    if (hasToken) {
                      val modes =
                        SaxonUtils
                          .iterateExpressionTree(args(0))
                          .collect { case s: StringLiteral => s.getStringValue }
                          .filter(Set("edit", "view", "pdf"))
                          .toList
                          .distinct
                      modes.flatMap(checkTokenPermission(controlNameOpt, location, _))
                    } else {
                      Nil
                    }
                  }

              unresolvedVars ++ unresolvedFnControls ++ tokenIssues
          }
      }

    def checkControlName(
      controlNameOpt       : Option[String],
      location             : String,
      referencedControlName: String
    ): List[ValidationIssue] =
      referencedControlName.trimAllToOpt match {
        case Some(cn) if ! validControlNames(cn) =>
          List(UnresolvedControlReference(controlNameOpt, location, cn))
        case _ =>
          Nil
      }

    val issues = scala.collection.mutable.ListBuffer[ValidationIssue]()

    // 1. Binds
    for {
      topLevelBind <- ctx.topLevelBindElemOpt.toList
      bindElem     <- topLevelBind descendantOrSelf (XFBindTest || FBBindTest)
    } locally {
      val bindControlNameOpt =
        frc.controlNameFromIdOpt(bindElem.id)
          .orElse(bindElem.attValueOpt("name").flatMap(_.trimAllToOpt))
          .map(_.stripSuffix(frc.DefaultIterationSuffix))

      for (att <- bindElem /@ BindFormulaAttTest)
        issues ++= validateExpression(att, avt = false, bindControlNameOpt, att.localname)

      for {
        nestedElem <- bindElem / NestedBindFormulaElemTest
        valueAtt   <- nestedElem /@ ValueTest
      } locally {
        issues ++= validateExpression(valueAtt, avt = false, bindControlNameOpt, nestedElem.localname)
      }
    }

    // 2. Template parameters (`*:param` in view and model, including `fr-form-resources` and `fr-form-metadata`)
    val allParamRoots = ctx.viewContainerElemOpt.toList ++ ctx.modelElemOpt.toList
    for (param <- allParamRoots descendant ParamElemTest) {
      val enclosingControlNameOpt =
        param.ancestor(*).flatMap(e => e.idOpt.flatMap(frc.controlNameFromIdOpt)).headOption
          .orElse(param.ancestor(*).find(_.parentOption.exists(_.localname == "resource")).map(_.localname))
          .orElse(param.ancestor("template").headOption.flatMap(_.attValueOpt("name")).flatMap(_.trimAllToOpt))

      val paramName = (param / NameElemTest).stringValue.trimAllToOpt.getOrElse("param")
      val location  = s"param:$paramName"

      for (controlNameElem <- param / ControlNameElemTest)
        issues ++= checkControlName(enclosingControlNameOpt, location, controlNameElem.stringValue)

      for (exprElem <- param / ExprElemTest)
        issues ++= validateExpression(exprElem, avt = false, enclosingControlNameOpt, location)

      val paramType =
        param.attValueOpt("type")
          .orElse((param / TypeElemTest).headOption.map(_.stringValue))
          .map(_.splitTo[List]("(").head)
          .getOrElse("")

      val hasToken = (param / TokenElemTest).exists(_.stringValue.trimAllToEmpty == "true")
      if (hasToken) {
        val modeOpt =
          paramType match {
            case "LinkToEditPageParam" | "link-to-edit-page" => Some("edit")
            case "LinkToViewPageParam" | "link-to-view-page" => Some("view")
            case "LinkToPdfParam"      | "link-to-pdf"       => Some("pdf")
            case _                                           => None
          }
        for {
          mode  <- modeOpt
          issue <- checkTokenPermission(enclosingControlNameOpt, location, mode)
        } locally {
          issues += issue
        }
      }
    }

    // 3. Email and confirmation templates in metadata
    val emailTemplates =
      (ctx.metadataRootElemOpt.toList / "email" / "templates" / "template") ++
        (ctx.metadataRootElemOpt.toList / "confirmation" / "templates" / "template")

    for (template <- emailTemplates) {
      val templateName = template.attValueOpt("name").flatMap(_.trimAllToOpt).getOrElse("default")
      val location     = s"email-template:$templateName"

      for (header <- template / "headers" / "header") {
        header.attValueOpt("type") match {
          case Some("control-value") | None if header.attValueOpt("section-template").isEmpty =>
            issues ++= checkControlName(None, location, header.stringValue)
          case Some("expression") =>
            issues ++= validateExpression(header, avt = false, None, location)
          case _ =>
        }
      }

      for {
        controlElem <- (template / "attach" / "control") ++ (template / "exclude-from-all-control-values" / "control")
        if controlElem.attValueOpt("section-template").isEmpty
      } locally {
        issues ++= checkControlName(None, location, controlElem.stringValue)
      }

      for (enableIfTrue <- template / "enable-if-true")
        issues ++= validateExpression(enableIfTrue, avt = false, None, location)
    }

    // 4. New actions & listeners
    for {
      listener    <- FormBuilder.findNewListeners(ctx.modelElemOpt)
      controlsAtt <- listener /@ ControlsTest
      controlName <- controlsAtt.stringValue.splitTo[List]()
    } locally {
      issues ++= checkControlName(None, "listener", controlName)
    }

    for (action <- FormBuilder.findNewActions(ctx.modelElemOpt)) {
      val actionName = action.attValueOpt("name").orElse(action.idOpt.map(_.stripSuffix("-binding"))).getOrElse("action")
      val location   = s"action:$actionName"

      for (att <- action descendant * att (ControlTest || RepeatTest || LeftTest || RightTest))
        issues ++= checkControlName(None, location, att.stringValue)

      for (att <- action descendant * att (ValueTest || RefTest || ItemsTest || LabelTest || HintTest || ConditionTest))
        issues ++= validateExpression(att, avt = false, None, location)

      for (att <- action descendant * att (QName("location") || QName("message")))
        issues ++= validateExpression(att, avt = true, None, location)
    }

    // 5. Legacy actions
    for (legacyAction <- FormBuilder.findLegacyActions(ctx.modelElemOpt)) {
      val actionName = legacyAction.idOpt.map(_.stripSuffix("-binding")).getOrElse("action")
      val location   = s"action:$actionName"

      for {
        observerAtt <- legacyAction child XFActionTest att "observer"
        observerId  <- observerAtt.stringValue.trimAllToOpt
        if observerId.endsWith("-control")
        controlName <- frc.controlNameFromIdOpt(observerId)
      } locally {
        issues ++= checkControlName(None, location, controlName)
      }

      for {
        varElem  <- legacyAction descendant XFORMS_VAR_QNAME
        if varElem.attValue("name") == "control-name"
        valueAtt <- varElem /@ VALUE_QNAME
        rawVal   = valueAtt.stringValue.trimAllToEmpty
        if rawVal.startsWith("'") && rawVal.endsWith("'") && rawVal.length >= 2
      } locally {
        issues ++= checkControlName(None, location, rawVal.substring(1, rawVal.length - 1))
      }

      for {
        varElem  <- legacyAction descendant XFORMS_VAR_QNAME
        if LegacyActionExpressionVarNames(varElem.attValue("name"))
        valueAtt <- varElem /@ VALUE_QNAME
      } locally {
        issues ++= validateExpression(valueAtt, avt = false, None, location)
      }
    }

    // 6. Service submissions
    for {
      modelElem   <- ctx.modelElemOpt.toList
      submission  <- modelElem descendant ServiceSubmissionTest
      resourceAtt <- submission /@ "resource"
    } locally {
      val serviceName = submission.idOpt.map(_.stripSuffix("-submission")).getOrElse("service")
      val paramNames  = (submission / "param" / "name").flatMap(_.stringValue.trimAllToOpt).toSet
      issues ++= validateExpression(resourceAtt, avt = true, None, s"service:$serviceName", extraValidVars = paramNames)
    }

    // 7. Body controls & containers
    for (viewElem <- ctx.viewContainerElemOpt.toList) {
      for ((test, filter, avtAtts, atts) <- BodyValidationMappings) {
        for (elem <- viewElem descendant test filter filter) {
          val enclosingControlNameOpt =
            elem.ancestorOrSelf(*).flatMap(e => e.idOpt.flatMap(frc.controlNameFromIdOpt)).headOption

          for {
            avtAtt <- avtAtts
            att    <- elem /@ avtAtt
          } locally {
            issues ++= validateExpression(att, avt = true, enclosingControlNameOpt, avtAtt)
          }

          for {
            attName <- atts
            att     <- elem /@ attName
          } locally {
            issues ++= validateExpression(att, avt = false, enclosingControlNameOpt, attName)
          }
        }
      }

      // Itemset filter formulas (`xf:itemset/fr:filter/fr:expr`)
      for {
        itemsetElem <- viewElem descendant XFItemsetTest
        exprElem    <- itemsetElem / (FR -> "filter") / FRExprTest
      } locally {
        val enclosingControlNameOpt =
          itemsetElem.ancestor(*).flatMap(e => e.idOpt.flatMap(frc.controlNameFromIdOpt)).headOption
        issues ++= validateExpression(exprElem, avt = false, enclosingControlNameOpt, "filter")
      }
    }

    issues.toList
  }

  private val BodyValidationMappings: List[(Test, NodeInfo => Boolean, List[String], List[String])] = List(
    (FRContainerTest                                                      , FormRunner.isRepeat, List("min", "max", "freeze", "remove-constraint", "clear-constraint"), Nil),
    (FRNumberTest || FRCurrencyTest || FRPercentageTest                   , _ => true,           List("prefix", "suffix")                                             , Nil),
    ((FRRangeQName: Test) || xfQName("range")                             , _ => true,           List("start", "end", "step", "prefix", "suffix")                     , Nil),
    (FRRatingQName                                                        , _ => true,           List("max")                                                          , Nil),
    (FRScaleQName                                                         , _ => true,           List("start", "end", "start-label", "end-label")                     , Nil),
    (FRHyperlinkQName                                                     , _ => true,           List("href")                                                         , Nil),
    (FRQrCodeQName || FRBarcodeQName                                      , _ => true,           List("content")                                                      , Nil),
    (FRDataboundSelect1QName || FRDataboundSelect1SearchQName             , _ => true,           List("resource")                                                     , Nil),
    (XFItemsetTest || XFItemTest || XFChoicesTest                         , _ => true,           Nil                                                                  , List("ref", "value", "label", "hint")),
    (XFLabelTest || XFHelpTest || XFHintTest || XFAlertTest || XFValueTest, _ => true,           Nil                                                                  , List("ref", "value")),
  )

  private def currentFunctionLibrary: FunctionLibrary =
    inScopeContainingDocumentOpt
      .map(_.partAnalysis.functionLibrary)
      .getOrElse(FormRunner.componentsFunctionLibrary)

  private def compileExpression(
    xpathString     : String,
    namespaceMapping: NamespaceMapping,
    functionLibrary : FunctionLibrary,
    avt             : Boolean
  ): Expression =
    XPath.compileExpressionMinimal(
      staticContext = new ShareableXPathStaticContext(
        XPath.GlobalConfiguration,
        namespaceMapping,
        functionLibrary
      ),
      xpathString   = xpathString,
      avt           = avt
    )

  // --- Data Compatibility Comparison (#3985) ---

  private case class BindDescriptor(
    name          : String,
    parentNameOpt : Option[String],
    repeatAncestry: List[String],
    isContainer   : Boolean,
    isRepeated    : Boolean,
    datatype      : String,
    persist       : Boolean
  )

  def compareFormDefinitions(
    publishedFormRootElem: NodeInfo,
    currentFormRootElem  : NodeInfo
  ): (DataCompatibilityStatus, List[ValidationIssue]) = {

    val pubCtx = new SafeFormDocContext(publishedFormRootElem.rootElement)
    val curCtx = new SafeFormDocContext(currentFormRootElem.rootElement)

    val pubDescriptors = extractBindDescriptors(pubCtx).filter(_.persist)
    val curDescriptors = extractBindDescriptors(curCtx).filter(_.persist)

    val pubByName = pubDescriptors.map(d => d.name -> d).toMap
    val curByName = curDescriptors.map(d => d.name -> d).toMap

    val incompatibleIssues = scala.collection.mutable.ListBuffer[ValidationIssue]()

    for {
      cur <- curDescriptors
      pub <- pubByName.get(cur.name)
    } locally {
      if (pub.isContainer || cur.isContainer) {
        if (pub.isRepeated != cur.isRepeated)
          incompatibleIssues += ContainerRepeatStatusChanged(cur.name, pub.isRepeated, cur.isRepeated)
      } else {
        if (pub.repeatAncestry != cur.repeatAncestry)
          incompatibleIssues += ControlRepeatAncestryChanged(cur.name, pub.repeatAncestry, cur.repeatAncestry)
        if (! isDatatypeChangeCompatible(pub.datatype, cur.datatype))
          incompatibleIssues += IncompatibleDatatypeChange(cur.name, pub.datatype, cur.datatype)
      }
    }

    val incompatibleContainerNames =
      incompatibleIssues.collect { case ContainerRepeatStatusChanged(name, _, _) => name }.toSet

    val addedNames =
      curDescriptors
        .filterNot(d => pubByName.contains(d.name))
        .map(_.name)

    val removedNames =
      pubDescriptors
        .filterNot(d => curByName.contains(d.name))
        .map(_.name)

    val movedNames =
      curDescriptors.flatMap { cur =>
        pubByName.get(cur.name).collect {
          case pub
            if pub.repeatAncestry == cur.repeatAncestry &&
               pub.parentNameOpt != cur.parentNameOpt   &&
               ! incompatibleContainerNames(cur.name) =>
            cur.name
        }
      }

    val migrationIssueOpt =
      (addedNames.nonEmpty || removedNames.nonEmpty || movedNames.nonEmpty).option(
        DataMigrationRequired(addedNames, removedNames, movedNames)
      )

    val allIssues = incompatibleIssues.toList ++ migrationIssueOpt.toList

    val status =
      if (incompatibleIssues.nonEmpty)
        DataCompatibilityStatus.Incompatible
      else if (migrationIssueOpt.isDefined)
        DataCompatibilityStatus.MigrationRequired
      else
        DataCompatibilityStatus.Compatible

    (status, allIssues)
  }

  private def extractBindDescriptors(ctx: SafeFormDocContext): List[BindDescriptor] = {

    val templateIterationNames: Set[String] =
      (
        for {
          modelElem        <- ctx.modelElemOpt.toList
          instance         <- modelElem / XFORMS_INSTANCE_QNAME
          if FormRunner.isTemplateId(instance.id)
          instanceRootElem <- instance.firstChildOpt(*)
        } yield
          instanceRootElem.localname
      ).toSet

    val containerElemsInBody: List[NodeInfo] =
      ctx.viewContainerElemOpt.toList.descendant(FRContainerTest).toList

    val containerNamesInBody: Set[String] =
      containerElemsInBody
        .flatMap(e => e.idOpt.flatMap(frc.controlNameFromIdOpt))
        .toSet

    val repeatedContainerNamesInBody: Set[String] =
      containerElemsInBody
        .filter(FormRunner.isRepeat)
        .flatMap(e => e.idOpt.flatMap(frc.controlNameFromIdOpt))
        .toSet

    def bindNameOpt(bind: NodeInfo): Option[String] =
      bind.attValueOpt("name").flatMap(_.trimAllToOpt)
        .orElse(frc.controlNameFromIdOpt(bind.id))

    def isIterationBind(bindName: String): Boolean =
      templateIterationNames(bindName) || bindName.endsWith(frc.DefaultIterationSuffix)

    def normalizeDatatype(rawOpt: Option[String]): String =
      rawOpt
        .flatMap(_.trimAllToOpt)
        .map { raw =>
          val idx = raw.indexOf(':')
          if (idx >= 0) raw.substring(idx + 1) else raw
        }
        .getOrElse("string")

    def isPersist(bind: NodeInfo): Boolean =
      ! (
        bind.attValueOpt(FRPersistQName).contains(FormRunner.FalseExpr) ||
        bind.attValueOpt(QName("persist", FBNamespace)).contains(FormRunner.FalseExpr)
      )

    def walkBinds(
      binds         : List[NodeInfo],
      parentNameOpt : Option[String],
      repeatAncestry: List[String]
    ): List[BindDescriptor] =
      binds.flatMap { bind =>
        bindNameOpt(bind) match {
          case None =>
            Nil
          case Some(bindName) =>
            val childBinds = (bind / (XFBindTest || FBBindTest)).toList
            if (isIterationBind(bindName)) {
              walkBinds(
                binds          = childBinds,
                parentNameOpt  = parentNameOpt,
                repeatAncestry = bindName :: repeatAncestry
              )
            } else {
              val isRepeated =
                childBinds.exists(cb => bindNameOpt(cb).exists(isIterationBind)) ||
                  repeatedContainerNamesInBody(bindName)
              val isContainer =
                childBinds.nonEmpty || containerNamesInBody(bindName)
              val descriptor =
                BindDescriptor(
                  name           = bindName,
                  parentNameOpt  = parentNameOpt,
                  repeatAncestry = repeatAncestry,
                  isContainer    = isContainer,
                  isRepeated     = isRepeated,
                  datatype       = normalizeDatatype(bind.attValueOpt("type")),
                  persist        = isPersist(bind)
                )
              descriptor :: walkBinds(
                binds          = childBinds,
                parentNameOpt  = Some(bindName),
                repeatAncestry = repeatAncestry
              )
            }
        }
      }

    val topLevelChildBinds =
      ctx.topLevelBindElemOpt.toList.flatMap(b => (b / (XFBindTest || FBBindTest)).toList)

    walkBinds(topLevelChildBinds, parentNameOpt = None, repeatAncestry = Nil)
  }

  private val WideningNumericTypes: Set[String] = Set("decimal", "double", "float")

  private def isDatatypeChangeCompatible(fromDatatype: String, toDatatype: String): Boolean =
    fromDatatype == toDatatype ||
      toDatatype == "string"   ||
      (fromDatatype == "integer" && WideningNumericTypes(toDatatype))

  // --- Structural Validation for Edit Source (#816) ---

  def validateFormStructure(formDefinitionRootElem: NodeInfo): List[StructuralValidationError] = {

    val errors  = scala.collection.mutable.ListBuffer[StructuralValidationError]()
    val root    = formDefinitionRootElem.rootElement
    val ctx     = new SafeFormDocContext(root)

    // 1. Root <xh:html>, <xh:head>, <xh:body>
    if (root.localname != "html" || root.namespaceURI != XH)
      errors += StructuralValidationError(
        message = "Root element must be <xh:html> in namespace \"http://www.w3.org/1999/xhtml\".",
        code    = "missing-html-root"
      )

    val headElemOpt = (root / (XH -> "head")).headOption
    if (headElemOpt.isEmpty)
      errors += StructuralValidationError(
        message = "Missing <xh:head> element under <xh:html>.",
        code    = "missing-head"
      )

    val xhtmlBodyElemOpt = (root / (XH -> "body")).headOption
    if (xhtmlBodyElemOpt.isEmpty)
      errors += StructuralValidationError(
        message = "Missing <xh:body> element under <xh:html>.",
        code    = "missing-body"
      )

    // 2. <xf:model id="fr-form-model"> and required instances & binds
    ctx.modelElemOpt match {
      case None =>
        errors += StructuralValidationError(
          message = "Missing <xf:model id=\"fr-form-model\"> element in <xh:head>.",
          code    = "missing-model"
        )
      case Some(_) =>
        ctx.formInstanceElemOpt match {
          case None =>
            errors += StructuralValidationError(
              message = "Missing <xf:instance id=\"fr-form-instance\"> in model.",
              code    = "missing-form-instance"
            )
          case Some(formInstance) =>
            if ((formInstance / *).headOption.isEmpty)
              errors += StructuralValidationError(
                message = "<xf:instance id=\"fr-form-instance\"> must contain a root element.",
                code    = "empty-form-instance"
              )
        }

        if (ctx.metadataInstanceElemOpt.isEmpty)
          errors += StructuralValidationError(
            message = "Missing <xf:instance id=\"fr-form-metadata\"> in model.",
            code    = "missing-metadata-instance"
          )

        ctx.resourcesInstanceElemOpt match {
          case None =>
            errors += StructuralValidationError(
              message = "Missing <xf:instance id=\"fr-form-resources\"> in model.",
              code    = "missing-resources-instance"
            )
          case Some(resInstance) =>
            val resourceElems = (resInstance / "resources" / "resource").toList
            if (resourceElems.isEmpty) {
              errors += StructuralValidationError(
                message = "<xf:instance id=\"fr-form-resources\"> must contain at least one <resource xml:lang=\"...\"> element.",
                code    = "missing-resource-elements"
              )
            } else if (resourceElems.size > 1) {
              val firstLangHolderNames = (resourceElems.head / *).map(_.localname).toSet
              for {
                resElem <- resourceElems.tail
                lang    = resElem.attValueOpt(XMLLangQName).getOrElse("")
                names   = (resElem / *).map(_.localname).toSet
                missing = firstLangHolderNames -- names
                extra   = names -- firstLangHolderNames
                diff    <- (missing ++ extra).toList.sorted
              } locally {
                errors += StructuralValidationError(
                  message     = s"Resource holder <$diff> is inconsistent across languages (in language \"$lang\").",
                  code        = "inconsistent-resources",
                  controlName = Some(diff)
                )
              }
            }
        }

        if (ctx.topLevelBindElemOpt.isEmpty)
          errors += StructuralValidationError(
            message = "Missing <xf:bind id=\"fr-form-binds\"> in model.",
            code    = "missing-form-binds"
          )
    }

    // 3. <fr:view> and <fr:body>
    if (ctx.viewContainerElemOpt.isEmpty)
      errors += StructuralValidationError(
        message = "Missing <fr:body> element inside <xh:body>/<fr:view>.",
        code    = "missing-fr-body"
      )

    // 4. Check for unprefixed (no-namespace) elements inside <xh:body> (outside any <xf:instance>)
    for {
      xhtmlBody <- xhtmlBodyElemOpt.toList
      elem      <- xhtmlBody descendant *
      if elem.namespaceURI.isEmpty && elem.ancestor(XFORMS_INSTANCE_QNAME).isEmpty
    } locally {
      errors += StructuralValidationError(
        message = s"Element <${elem.localname}> inside <xh:body> has no XML namespace.",
        code    = "element-without-namespace"
      )
    }

    // 5. Check grids and cells (<fr:c> / <xh:td>)
    for {
      viewBody <- ctx.viewContainerElemOpt.toList
      gridElem <- viewBody descendant FRGridTest
    } locally {
      val gridNameOpt = gridElem.idOpt.flatMap(frc.controlNameFromIdOpt)
      val maxColumns  = NodeInfoCell.NodeInfoCellOps.maxGridWidth(gridElem)

      for (cell <- gridElem / (FR -> "c")) {
        val childControls = (cell / *).toList
        if (childControls.size > 1)
          errors += StructuralValidationError(
            message     = s"Grid cell in ${gridNameOpt.map(n => s"\"$n\"").getOrElse("grid")} contains ${childControls.size} controls (at most 1 is allowed).",
            code        = "multiple-controls-in-cell",
            controlName = gridNameOpt
          )

        val xOpt = cell.attValueOpt("x").flatMap(_.toIntOption)
        val yOpt = cell.attValueOpt("y").flatMap(_.toIntOption)
        val wOpt = cell.attValueOpt("w").flatMap(_.toIntOption).orElse(Some(1))

        (xOpt, yOpt, wOpt) match {
          case (Some(x), Some(y), Some(w)) if x >= 1 && y >= 1 && w >= 1 && (x + w - 1) <= maxColumns =>
          case _ =>
            errors += StructuralValidationError(
              message     = s"Grid cell in ${gridNameOpt.map(n => s"\"$n\"").getOrElse("grid")} has invalid coordinates (x=\"${cell.attValue("x")}\", y=\"${cell.attValue("y")}\", w=\"${cell.attValue("w")}\").",
              code        = "invalid-cell-coordinates",
              controlName = gridNameOpt
            )
        }
      }

      for {
        tr   <- gridElem / (XH -> "tr")
        td   <- tr / (XH -> "td")
        if (td / *).size > 1
      } locally {
        errors += StructuralValidationError(
          message     = s"Grid cell in ${gridNameOpt.map(n => s"\"$n\"").getOrElse("grid")} contains multiple controls (at most 1 is allowed).",
          code        = "multiple-controls-in-cell",
          controlName = gridNameOpt
        )
      }
    }

    // 6. Check that every @bind in <fr:body> resolves to an <xf:bind> in <fr-form-binds>, and every repeated container has a template instance
    val allBindsById: Map[String, NodeInfo] =
      ctx.topLevelBindElemOpt.toList
        .descendantOrSelf(XFBindTest || FBBindTest)
        .flatMap(b => b.idOpt.map(_ -> b))
        .toMap

    val templateInstanceIds: Set[String] =
      (ctx.modelElemOpt.toList / XFORMS_INSTANCE_QNAME)
        .flatMap(_.idOpt)
        .filter(FormRunner.isTemplateId)
        .toSet

    for {
      viewBody <- ctx.viewContainerElemOpt.toList
      elem     <- viewBody descendant *
    } locally {
      val controlNameOpt = elem.idOpt.flatMap(frc.controlNameFromIdOpt)

      if (! FormRunner.isSectionTemplateContent(elem))
        for (bindId <- elem.attValueOpt("bind").flatMap(_.trimAllToOpt)) {
          if (! allBindsById.contains(bindId))
            errors += StructuralValidationError(
              message     = s"Control ${controlNameOpt.map(n => s"\"$n\"").getOrElse(elem.localname)} references non-existent bind \"$bindId\".",
              code        = "missing-bind-reference",
              controlName = controlNameOpt
            )
        }

      if (FormRunner.IsContainer(elem) && FormRunner.isRepeat(elem)) {
        for (containerName <- controlNameOpt) {
          val expectedTemplateId = frc.templateId(containerName)
          if (! templateInstanceIds.contains(expectedTemplateId))
            errors += StructuralValidationError(
              message     = s"Repeated container \"$containerName\" is missing template instance \"$expectedTemplateId\".",
              code        = "missing-repeat-template",
              controlName = Some(containerName)
            )
        }
      }
    }

    // 7. Check that every <xf:bind> under <fr-form-binds> has a corresponding data holder in <fr-form-instance> or its repeat template instance
    val templateIterationRootByName: Map[String, NodeInfo] =
      (
        for {
          modelElem        <- ctx.modelElemOpt.toList
          instance         <- modelElem / XFORMS_INSTANCE_QNAME
          if FormRunner.isTemplateId(instance.id)
          instanceRootElem <- instance.firstChildOpt(*)
        } yield
          instanceRootElem.localname -> instanceRootElem
      ).toMap

    def checkBindDataHolders(binds: List[NodeInfo], parentHolders: List[NodeInfo]): Unit =
      for (bind <- binds) {
        val bindRefOpt =
          bind.attValueOpt("ref")
            .orElse(bind.attValueOpt("nodeset"))
            .flatMap(_.trimAllToOpt)
            .orElse(bind.attValueOpt("name").flatMap(_.trimAllToOpt))
            .orElse(frc.controlNameFromIdOpt(bind.id))

        for (bindRef <- bindRefOpt if SaxonUtils.isValidNCName(bindRef)) {
          val childBinds = (bind / (XFBindTest || FBBindTest)).toList
          templateIterationRootByName.get(bindRef) match {
            case Some(templateRoot) =>
              checkBindDataHolders(childBinds, List(templateRoot))
            case None =>
              val matchingHolders = parentHolders.flatMap(p => (p / bindRef).toList)
              if (matchingHolders.isEmpty && parentHolders.nonEmpty) {
                errors += StructuralValidationError(
                  message     = s"Bind \"${bind.idOpt.getOrElse(bindRef)}\" is missing data holder element <$bindRef> in instance data.",
                  code        = "missing-data-holder",
                  controlName = Some(bindRef)
                )
              } else {
                checkBindDataHolders(childBinds, matchingHolders)
              }
          }
        }
      }

    for {
      topLevelBind <- ctx.topLevelBindElemOpt
      dataRoot     <- ctx.formInstanceElemOpt.flatMap(_.firstChildOpt(*))
    } locally {
      checkBindDataHolders((topLevelBind / (XFBindTest || FBBindTest)).toList, List(dataRoot))
    }

    // 8. Check that controls referencing $form-resources/<name> have a <name> matching the control name
    //    and a <name> holder in <fr-form-resources>
    errors ++= checkResourcePointerControlNames(ctx)

    val resourceHolderNamesFirstLang: Set[String] =
      ctx.resourcesInstanceElemOpt.toList
        .flatMap(res => (res / "resources" / "resource").headOption.toList)
        .flatMap(r => (r / *).map(_.localname))
        .toSet

    if (resourceHolderNamesFirstLang.nonEmpty) {
      for {
        viewBody <- ctx.viewContainerElemOpt.toList
        elem     <- viewBody descendant *
        lhha     <- elem / ResourcePointerElemTest
        refValue <- lhha.attValueOpt("ref")
        m        <- FormResourceRefRegex.findFirstMatchIn(refValue)
        resName  = m.group(1)
        if ! resourceHolderNamesFirstLang.contains(resName)
      } locally {
        val controlNameOpt = elem.idOpt.flatMap(frc.controlNameFromIdOpt)
        errors += StructuralValidationError(
          message     = s"Control \"${controlNameOpt.getOrElse(resName)}\" references $$form-resources/$resName, which is missing from <fr-form-resources>.",
          code        = "missing-resource-holder",
          controlName = controlNameOpt.orElse(Some(resName))
        )
      }
    }

    errors.toList.distinct
  }

  private def checkResourcePointerControlNames(ctx: SafeFormDocContext): List[StructuralValidationError] =
    for {
      viewBody    <- ctx.viewContainerElemOpt.toList
      elem        <- viewBody descendant *
      controlName <- elem.idOpt.flatMap(frc.controlNameFromIdOpt).toList
      lhha        <- elem / ResourcePointerElemTest
      refValue    <- lhha.attValueOpt("ref")
      m           <- FormResourceRefRegex.findFirstMatchIn(refValue)
      resName     = m.group(1)
      if resName != controlName
    } yield
      StructuralValidationError(
        message     = s"Control \"$controlName\" has <${lhha.name}> referencing resource \"$resName\" instead of \"$controlName\" (ref=\"$refValue\").",
        code        = "mismatched-resource-name",
        controlName = Some(controlName)
      )

  // --- Formatting & XML Serialization for XForms UI ---

  def formatIssueMessage(
    issue           : ValidationIssue,
    resourcesElemOpt: Option[NodeInfo] = None
  ): String = {

    def resourcePatternOpt(key: String): Option[String] =
      for {
        res  <- resourcesElemOpt
        elem <- (res / "dialog-check-form" / "issue" / key).headOption
        text <- elem.stringValue.trimAllToOpt
      } yield
        text

    def formatWith(key: String, defaultPattern: String, args: AnyRef*): String = {
      val pattern = resourcePatternOpt(key).getOrElse(defaultPattern)
      Try(MessageFormat.format(pattern, args*)).getOrElse(defaultPattern)
    }

    issue match {
      case CircularDependency(mipName, controlNames) =>
        val defaultPattern =
          if (mipName == "default")
            "Circular dependency in Initial Value formulas involving controls: {0}"
          else
            "Circular dependency in Calculated Value formulas involving controls: {0}"
        formatWith(s"circular-dependency-$mipName", defaultPattern, controlNames.mkString(", "))

      case InvalidXPathExpression(controlNameOpt, location, expression) =>
        formatWith(
          "invalid-xpath",
          "Invalid XPath expression in {0} ({1}): {2}",
          controlNameOpt.getOrElse("-"),
          location,
          expression
        )

      case UnresolvedVariableReference(controlNameOpt, location, variableName) =>
        formatWith(
          "unresolved-variable",
          "Reference to unknown variable \"${2}\" in {0} ({1})",
          controlNameOpt.getOrElse("-"),
          location,
          variableName
        )

      case UnresolvedControlReference(controlNameOpt, location, referencedControlName) =>
        formatWith(
          "unresolved-control",
          "Reference to unknown control \"{2}\" in {0} ({1})",
          controlNameOpt.getOrElse("-"),
          location,
          referencedControlName
        )

      case TokenWithoutPermissions(controlNameOpt, location, mode, missingOperations) =>
        formatWith(
          "token-without-permissions",
          "Link with token for \"{2}\" mode in {0} ({1}) requires \"Require token\" permission for: {3}",
          controlNameOpt.getOrElse("-"),
          location,
          mode,
          missingOperations.map(_.entryName).toList.sorted.mkString(", ")
        )

      case ContainerRepeatStatusChanged(containerName, wasRepeated, isRepeated) =>
        formatWith(
          "container-repeat-changed",
          "Container \"{0}\" changed repeat setting ({1} to {2})",
          containerName,
          if (wasRepeated) "repeated" else "non-repeated",
          if (isRepeated)  "repeated" else "non-repeated"
        )

      case ControlRepeatAncestryChanged(controlNameValue, fromRepeats, toRepeats) =>
        formatWith(
          "control-repeat-ancestry-changed",
          "Control \"{0}\" moved across repeat boundaries ({1} to {2})",
          controlNameValue,
          if (fromRepeats.isEmpty) "non-repeated" else fromRepeats.reverse.mkString("/"),
          if (toRepeats.isEmpty)   "non-repeated" else toRepeats.reverse.mkString("/")
        )

      case IncompatibleDatatypeChange(controlNameValue, fromDatatype, toDatatype) =>
        formatWith(
          "incompatible-datatype-change",
          "Control \"{0}\" changed data type from \"{1}\" to \"{2}\"",
          controlNameValue,
          fromDatatype,
          toDatatype
        )

      case DataMigrationRequired(addedNames, removedNames, movedNames) =>
        val details =
          List(
            addedNames.nonEmpty.option(s"+${addedNames.mkString(", ")}"),
            removedNames.nonEmpty.option(s"-${removedNames.mkString(", ")}"),
            movedNames.nonEmpty.option(s"moved: ${movedNames.mkString(", ")}")
          ).flatten.mkString("; ")
        formatWith(
          "data-migration-required",
          "Data structure changes compared to published version ({0})",
          details
        )

      case StructuralValidationError(message, _, _) =>
        message
    }
  }

  def validateFormAsXml(
    formDefinitionRootElem        : NodeInfo,
    publishedFormDefinitionRootOpt: Option[NodeInfo] = None,
    resourcesElemOpt              : Option[NodeInfo] = None,
    includeStructureCheck         : Boolean          = true
  ): NodeInfo = {

    val structureIssues =
      if (includeStructureCheck)
        validateFormStructure(formDefinitionRootElem)
      else
        Nil

    val definitionIssues =
      validateFormDefinition(formDefinitionRootElem)

    val (compatibilityStatusOpt, compatibilityIssues) =
      publishedFormDefinitionRootOpt match {
        case Some(publishedRoot) =>
          val (status, issues) = compareFormDefinitions(publishedRoot, formDefinitionRootElem)
          (Some(status), issues)
        case None =>
          (None, Nil)
      }

    val allIssues    = (structureIssues ++ definitionIssues ++ compatibilityIssues).distinct
    val errorCount   = allIssues.count(_.severity == ValidationSeverity.Error)
    val warningCount = allIssues.count(_.severity == ValidationSeverity.Warning)
    val infoCount    = allIssues.count(_.severity == ValidationSeverity.Info)
    val blocking     = allIssues.exists {
      case _: CircularDependency => true
      case _                     => false
    }

    elemToNodeInfo(
      <validation
        valid={(errorCount == 0 && warningCount == 0).toString}
        blocking={blocking.toString}
        error-count={errorCount.toString}
        warning-count={warningCount.toString}
        info-count={infoCount.toString}
        data-compatibility={compatibilityStatusOpt.map(_.entryName).getOrElse("")}>
        {
          allIssues.map { issue =>
            <issue
              severity={issue.severity.entryName}
              blocking={issue.blocking.toString}
              code={issue.code}
              control-name={issue.controlName.getOrElse("")}>{formatIssueMessage(issue, resourcesElemOpt)}</issue>
          }
        }
      </validation>
    )
  }

  def validateFormStructureAsXml(
    formDefinitionRootElem: NodeInfo,
    resourcesElemOpt      : Option[NodeInfo] = None
  ): NodeInfo = {
    val structureIssues = validateFormStructure(formDefinitionRootElem)
    val circularIssues  = checkCircularDependencies(formDefinitionRootElem)
    val allIssues       = structureIssues ++ circularIssues
    val errorCount      = allIssues.size

    elemToNodeInfo(
      <validation
        valid={(errorCount == 0).toString}
        blocking={(errorCount > 0).toString}
        error-count={errorCount.toString}
        warning-count="0"
        info-count="0"
        data-compatibility="">
        {
          allIssues.map { issue =>
            <issue
              severity={issue.severity.entryName}
              blocking={issue.blocking.toString}
              code={issue.code}
              control-name={issue.controlName.getOrElse("")}>{formatIssueMessage(issue, resourcesElemOpt)}</issue>
          }
        }
      </validation>
    )
  }
}
