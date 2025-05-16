/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.fr.library

import org.orbeon.dom.QName
import org.orbeon.dom.saxon.TypedNodeWrapper.TypedValueException
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.process.{FormRunnerRenderedFormat, SimpleProcess}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, NetUtils}
import org.orbeon.oxf.xforms.analysis.ElementAnalysis.ancestorsIterator
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xforms.function
import org.orbeon.oxf.xforms.function.{Instance, XFormsFunction}
import org.orbeon.oxf.xforms.function.XFormsFunction.getPathMapContext
import org.orbeon.oxf.xforms.function.xxforms.EvaluateSupport
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xml.{DefaultFunctionSupport, FunctionSupport, OrbeonFunctionLibrary, RuntimeDependentFunction, SaxonUtils, XMLUtils}
import org.orbeon.saxon
import org.orbeon.saxon.`type`.BuiltInAtomicType.*
import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.`type`.Type.ITEM_TYPE
import org.orbeon.saxon.expr.*
import org.orbeon.saxon.expr.PathMap.PathMapNodeSet
import org.orbeon.saxon.expr.StaticProperty.*
import org.orbeon.saxon.function.{AddToPathMap, AncestorOrganizations, Property, UserOrganizations, UserRoles}
import org.orbeon.saxon.functions.SystemFunction
import org.orbeon.saxon.om.*
import org.orbeon.saxon.pattern.NameTest
import org.orbeon.saxon.value.*
import org.orbeon.saxon.{ArrayFunctions, MapFunctions}
import org.orbeon.scaxon.Implicits.*
import org.orbeon.xbl.Wizard
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.XFormsNames.XFORMS_NAMESPACE_URI
import org.orbeon.xforms.analysis.model.ValidationLevel.ErrorLevel


object FormRunnerFunctionLibrary extends OrbeonFunctionLibrary {

  import FormRunnerFunctions.*

  Namespace(List(XMLNames.FR)) {

    // Register simple string functions
    for {
      ((name, _), index) <- StringGettersByName.zipWithIndex
    } locally {
      Fun(name, classOf[StringFunction], index, 0, STRING, ALLOWS_ZERO_OR_ONE)
    }

    // Register simple boolean functions
    for {
      ((name, _), index) <- BooleanGettersByName.zipWithIndex
    } locally {
      Fun(name, classOf[BooleanFunction], index, 0, BOOLEAN, ALLOWS_ZERO_OR_ONE)
    }

    // Register simple dateTime functions
    for {
      ((name, _), index) <- DateTimeGettersByName.zipWithIndex
    } locally {
      Fun(name, classOf[DateTimeFunction], index, 0, DATE_TIME, ALLOWS_ZERO_OR_ONE)
    }

    // Form runner parameter functions
    Fun("mode",                        classOf[FRMode],                 op = 0, min = 0, STRING, EXACTLY_ONE)
    Fun("app-name",                    classOf[FRAppName],              op = 0, min = 0, STRING, EXACTLY_ONE)
    Fun("form-name",                   classOf[FRFormName],             op = 0, min = 0, STRING, EXACTLY_ONE)
    Fun("document-id",                 classOf[FRDocumentId],           op = 0, min = 0, STRING, ALLOWS_ZERO_OR_ONE)

    Fun("is-draft",                    classOf[FRIsDraft],              op = 0, min = 0, BOOLEAN, ALLOWS_ZERO_OR_ONE)
    Fun("is-design-time",              classOf[FRIsDesignTime],         op = 0, min = 0, BOOLEAN, ALLOWS_ZERO_OR_ONE)
    Fun("is-readonly-mode",            classOf[FRIsReadonlyMode],       op = 0, min = 0, BOOLEAN, ALLOWS_ZERO_OR_ONE)

    Fun("form-version",                classOf[FRFormVersion],          op = 0, min = 0, INTEGER, ALLOWS_ZERO_OR_ONE)
    Fun("attachment-form-version",     classOf[FRAttachmentFormVersion], op = 0, min = 0, INTEGER, ALLOWS_ZERO_OR_ONE)

    // Other functions
    Fun("user-roles",                  classOf[UserRoles],             op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE)
    Fun("user-organizations",          classOf[UserOrganizations],     op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE)

    Fun("user-ancestor-organizations", classOf[AncestorOrganizations], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("run-process-by-name", classOf[FRRunProcessByName], op = 0, min = 2, BOOLEAN, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("run-process", classOf[FRRunProcess], op = 0, min = 2, BOOLEAN, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("dataset", classOf[FRDataset], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("control-string-value", classOf[FRControlStringValue], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    Fun("control-typed-value", classOf[FRControlTypedValue], op = 0, min = 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    Fun("component-param-value", classOf[FRComponentParam], op = 0, min = 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("component-param-value-by-type", classOf[FRComponentParamByType], op = 0, min = 2, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
      Arg(QNAME, EXACTLY_ONE),
      Arg(QNAME, EXACTLY_ONE)
    )

    Fun("pdf-templates", classOf[FRListPdfTemplates], op = 0, min = 0, ANY_ATOMIC, ALLOWS_ZERO_OR_MORE)

    Fun("use-pdf-template", classOf[FRUsePdfTemplate], op = 0, min = 0, BOOLEAN, EXACTLY_ONE)

    Fun("created-with-or-newer", classOf[FRCreatedWithOrNewer], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("transform-uploaded-image", classOf[FRTransformUploadedImage], op = 0, min = 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
      Arg(ITEM_TYPE, EXACTLY_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_ONE),
    )

    Fun("form-runner-link", classOf[FRFormRunnerLink], op = 0, min = 1, STRING, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("is-embedded", classOf[FRIsEmbedded], op = 0, min = 0, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("evaluate-from-property", classOf[FREvaluateFromProperty], op = saxon.functions.Evaluate.EVALUATE, min = 2, max = 3, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_ONE),
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )
  }
}

private object FormRunnerFunctions {

  val StringGettersByName: Seq[(String, () => Option[String])] = List(
    "form-title"                  -> (() => FormRunner.formTitleFromMetadata),
    "lang"                        -> (() => Some(FormRunner.currentLang)),
    "workflow-stage-value"        -> (() => FormRunner.documentWorkflowStage),
    "username"                    -> (() => NetUtils.getExternalContext.getRequest.credentials map     (_.userAndGroup.username)),
    "user-group"                  -> (() => NetUtils.getExternalContext.getRequest.credentials flatMap (_.userAndGroup.groupname)),
    "relevant-form-values-string" -> (() => Some(FormRunnerMetadata.findAllControlsWithValues(html = false, Nil))),
    "wizard-current-page-name"    -> (() => Wizard.wizardCurrentPageNameOpt)
  )

  val BooleanGettersByName = List(
    "is-browser-environment"      -> (() => CoreCrossPlatformSupport.isJsEnv),
    "is-pe"                       -> (() => Version.isPE),
    "is-service-path"             -> (() => FormRunner.isServicePath(XFormsFunction.context)),
    "is-background"               -> (() => FormRunner.isBackground(XFormsFunction.context, FormRunnerParams())),
    "is-noscript"                 -> (() => false),
    "is-form-data-valid"          -> (() => countValidationsByLevel(ErrorLevel) == 0),
    "is-form-data-saved"          -> (() => FormRunner.isFormDataSaved),
    "is-wizard-toc-shown"         -> (() => Wizard.isWizardTocShown),
    "is-wizard-body-shown"        -> (() => Wizard.isWizardBodyShown),
    "is-wizard-first-page"        -> (() => Wizard.isWizardFirstPage),
    "is-wizard-last-page"         -> (() => Wizard.isWizardLastPage),
    "is-wizard-separate-toc"      -> (() => Wizard.isWizardSeparateToc),
    "can-create"                  -> (() => FormRunner.canCreate),
    "can-read"                    -> (() => FormRunner.canRead),
    "can-update"                  -> (() => FormRunner.canUpdate),
    "can-delete"                  -> (() => FormRunner.canDelete),
    "can-list"                    -> (() => FormRunner.canList),
    "owns-lease-or-none-required" -> (() => FormRunner.userOwnsLeaseOrNoneRequired)
  )

  val DateTimeGettersByName = List(
    "created-dateTime"            -> (() => FormRunner.documentCreatedDate),
    "modified-dateTime"           -> (() => FormRunner.documentModifiedDate),
    "created-date"                -> (() => FormRunner.documentCreatedDate), // only keep until 2017.1 or 2017.2 for backward compatibility
    "modified-date"               -> (() => FormRunner.documentModifiedDate) // only keep until 2017.1 or 2017.2 for backward compatibility
  )

  private val IndexedStringFunctions = (
    for {
      ((_, fun), index) <- StringGettersByName.zipWithIndex
    } yield
      index -> fun
  ).toMap

  private val IndexedBooleanFunctions = (
    for {
      ((_, fun), index) <- BooleanGettersByName.zipWithIndex
    } yield
      index -> fun
  ).toMap

  private val IndexedDateTimeFunctions = (
    for {
      ((_, fun), index) <- DateTimeGettersByName.zipWithIndex
    } yield
      index -> fun
  ).toMap

  class StringFunction extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(xpathContext: XPathContext): StringValue =
      IndexedStringFunctions(operation).apply()
  }

  class BooleanFunction extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(xpathContext: XPathContext): BooleanValue =
      IndexedBooleanFunctions(operation).apply()
  }

  class DateTimeFunction extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(xpathContext: XPathContext): DateTimeValue =
      IndexedDateTimeFunctions(operation).apply() map
        (new java.util.Date(_))                   map
        DateTimeValue.fromJavaDate                orNull
  }

  class FRRunProcessByName extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(context: XPathContext): BooleanValue =
      SimpleProcess.runProcessByName(stringArgument(0)(context), stringArgument(1)(context)).left.toOption.map(_.isSuccess)
  }

  class FRRunProcess extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(context: XPathContext): BooleanValue =
      SimpleProcess.runProcess(stringArgument(0)(context), stringArgument(1)(context)).left.toOption.map(_.isSuccess)
  }

  class FRDataset extends SystemFunction {

    // Rewrite `fr:dataset($arg1)` into `instance(concat('fr-dataset-', $arg1))`
    override def simplify(visitor: ExpressionVisitor): Expression = {

      simplifyArguments(visitor)

      val concatFn =
        SystemFunction.makeSystemFunction(
          "concat",
          Array(new StringLiteral("fr-dataset-"), getArguments()(0))
        )

      // From `Expression.java`: "The rule is that an implementation of simplify(), typeCheck(), or optimize()
      // that returns a value other than `this` is required to set the location information and parent pointer
      // in the new child expression."
      concatFn.setContainer(getContainer)
      ExpressionTool.copyLocationInfo(this, concatFn)

      val instanceFn = new function.Instance
      instanceFn.setDetails(XFormsFunctionLibrary.getEntry(XFORMS_NAMESPACE_URI, "instance", 1).get)
      instanceFn.setFunctionName(new StructuredQName("", XFORMS_NAMESPACE_URI, "instance"))
      instanceFn.setArguments(Array(concatFn))

      instanceFn.setContainer(getContainer)
      ExpressionTool.copyLocationInfo(this, instanceFn)

      instanceFn.simplify(visitor)

      instanceFn
    }
  }

  class FRControlStringValue extends FunctionSupport with RuntimeDependentFunction {

    override def iterate(context: XPathContext): SequenceIterator = {

      implicit val ctx = context

      FormRunner.resolveTargetRelativeToActionSourceOpt(
        actionSourceAbsoluteId  = XFormsId.effectiveIdToAbsoluteId(XFormsFunction.context.sourceEffectiveId),
        targetControlName       = stringArgument(0),
        followIndexes           = booleanArgumentOpt(1) getOrElse false,
        libraryOrSectionNameOpt = stringArgumentOpt(2).flatMap(_.trimAllToOpt).map(Right.apply)
      ) map {
        _.map(_.getStringValue).toList // https://github.com/orbeon/orbeon-forms/issues/6016
      }
    }
  }

  class FRControlTypedValue extends FunctionSupport with RuntimeDependentFunction {

    override def evaluateItem(context: XPathContext): Item = {

      implicit val ctx = context

      val resolvedItems =
        FormRunner.resolveTargetRelativeToActionSourceOpt(
          actionSourceAbsoluteId  = XFormsId.effectiveIdToAbsoluteId(XFormsFunction.context.sourceEffectiveId),
          targetControlName       = stringArgument(0),
          followIndexes           = booleanArgumentOpt(1) getOrElse false,
          libraryOrSectionNameOpt = stringArgumentOpt(2).flatMap(_.trimAllToOpt).map(Right.apply)
        ) getOrElse
          Iterator.empty

      val allItems =
        resolvedItems map { item =>
          try {
            // `TypedNodeWrapper.getTypedValue` *should* return a single value or throw
            Option(item.getTypedValue.next()) getOrElse EmptySequence.getInstance
          } catch {
            case _: TypedValueException => EmptySequence.getInstance
          }
        }

      ArrayFunctions.createValue(allItems.to(Vector))
    }
  }

  // For instance, `fr:component-param-value('decimal-separator')`
  //
  // This searches bound element attributes, the `fr-form-metadata` instance, and properties.
  //
  // We search `fr-form-metadata` statically, since we know that it is readonly and inline.
  //
  class FRComponentParam extends FunctionSupport with RuntimeDependentFunction {

    override def evaluateItem(context: XPathContext): AtomicValue = {

      implicit val xpc = context
      implicit val xfc = XFormsFunction.context

      FRComponentParamSupport.componentParamValue(
        paramName            = QName(stringArgument(0)),
        sourceComponentIdOpt = stringArgumentOpt(1),
        property             = Property.property
      ).orNull
    }

    override def addToPathMap(
      pathMap        : PathMap,
      pathMapNodeSet : PathMapNodeSet
    ): PathMapNodeSet =
      arguments.head match {
        case s: StringLiteral =>

          val paramName = s.getStringValue

          val context = getPathMapContext(pathMap)

          val boundElemOpt =
            ancestorsIterator(context.element, includeSelf = false) collectFirst {
              case c: ComponentControl => c
            }

          // The following causes the use of `fr:component-param-value()` to be invalidated only if there is an
          // attribute and it might be an AVT. If the parameter is a literal, this makes sense, as it will never change.
          // Any dependency on `fr-form-metadata` does not need to be handled, as that is an immutable instance.
          //
          // TODO: handle AVT dependencies
          val hasAttributeAndIsNotAvt =
            boundElemOpt.exists(_.element.attributeValueOpt(paramName).exists(v => ! XMLUtils.maybeAVT(v)))

          val doesNotHaveAttribute =
            boundElemOpt.exists(_.element.attributeValueOpt(paramName).isEmpty)

          if (hasAttributeAndIsNotAvt || doesNotHaveAttribute) {
            pathMapNodeSet
          } else {
            pathMap.setInvalidated(true)
            null
          }
        case _ =>
          pathMap.setInvalidated(true)
          null
      }
  }

  class FRComponentParamByType extends FunctionSupport with RuntimeDependentFunction {

    override def evaluateItem(context: XPathContext): AtomicValue = {

      implicit val xpc = context
      implicit val xfc = XFormsFunction.context

      FRComponentParamSupport.componentParamValueByType(
        paramName  = qNameArgument(0),
        directName = qNameArgument(1),
        property   = Property.property
      ).orNull
    }
  }

  class FRListPdfTemplates extends FunctionSupport with RuntimeDependentFunction {
    override def iterate(context: XPathContext): SequenceIterator =
      FormRunnerRenderedFormat.listPdfTemplates map { template =>
        MapFunctions.createValue(
          Map[AtomicValue, ValueRepresentation](
            (SaxonUtils.fixStringValue("path"), template.path),
            (SaxonUtils.fixStringValue("name"), template.nameOpt map stringToStringValue getOrElse EmptySequence.getInstance),
            (SaxonUtils.fixStringValue("lang"), template.langOpt map stringToStringValue getOrElse EmptySequence.getInstance)
          )
        )
      }
  }

  class FRUsePdfTemplate extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(context: XPathContext): BooleanValue =
      FormRunnerRenderedFormat.usePdfTemplate(NetUtils.getExternalContext.getRequest)
  }

  class FRCreatedWithOrNewer extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(context: XPathContext): BooleanValue =
      FRCreatedWithOrNewerSupport.isCreatedWithOrNewer(XFormsFunction.context.container, stringArgument(0)(context))
  }

  class FRFormRunnerLink extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(context: XPathContext): StringValue =
      FormRunner.buildLinkBackToFormRunnerUsePageName(
        stringArgument(0)(context),
        booleanArgumentOpt(1)(context).getOrElse(false)
      )
  }

  class FRIsEmbedded extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(context: XPathContext): BooleanValue =
      FormRunner.isEmbedded(stringArgumentOpt(0)(context))
  }

  class FREvaluateFromProperty extends DefaultFunctionSupport with RuntimeDependentFunction {
    override def iterate(xpathContext: XPathContext): SequenceIterator = {

      implicit val xpc   : XPathContext           = xpathContext
      implicit val xfc   : XFormsFunction.Context = XFormsFunction.context
      implicit val logger: IndentedLogger         = FormRunner.newIndentedLogger

      FormRunnerRename.replaceVarReferencesWithFunctionCallsFromPropertyAsExpr(
        propertyName    = stringArgument(1),
        avt             = false,
        libraryName     = None,
        norewrite       = Set.empty,
        functionLibrary = FormRunner.componentsFunctionLibrary
      )
      .map { rewrittenXPathExpr =>
        EvaluateSupport.evaluateInContextFromXPathExpr(
          expr               = rewrittenXPathExpr,
          exprVarEffectiveId = stringArgument(2),
          exprContextItem    = itemArgument(0),
          xfcd               = xfc.containingDocument
        )
      }
    }
  }

  trait FRParamsFunction[R >: Null <: AtomicValue] extends FunctionSupport with RuntimeDependentFunction {

    val elemNames: List[String]
    def fromParams(params: FormRunnerParams): Option[R]

    override def evaluateItem(context: XPathContext): R =
      FormRunnerParamsOpt().flatMap(fromParams).orNull

    override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMapNodeSet): PathMapNodeSet = {

      def newInstanceExpression: Instance = {
        val instanceExpression = new Instance

        instanceExpression.setFunctionName(new StructuredQName("", NamespaceConstant.FN, "instance"))
        instanceExpression.setArguments(Array(new StringLiteral("fr-parameters-instance")))

        instanceExpression.setContainer(getContainer)

        instanceExpression
      }

      elemNames.foreach { elemName =>
        new AxisExpression(
          Axis.CHILD,
          new NameTest(Type.ELEMENT, "", elemName, getExecutable.getConfiguration.getNamePool)
        )
        .addToPathMap(
          pathMap,
          new PathMap.PathMapNodeSet(pathMap.makeNewRoot(newInstanceExpression))
        )
        .setAtomized()
      }

      null
    }
  }

  // Can change, for example between `new` and `edit`
  class FRMode extends FRParamsFunction[StringValue] {
    override val elemNames: List[String] = List("mode")
    override def fromParams(params: FormRunnerParams): Option[StringValue] = Some(params.mode)
  }

  // Probably cannot change over the course of the form's lifetime
  class FRAppName extends FRParamsFunction[StringValue] {
    override val elemNames: List[String] = List("app")
    override def fromParams(params: FormRunnerParams): Option[StringValue] = Some(params.app)
  }

  // Probably cannot change over the course of the form's lifetime
  class FRFormName extends FRParamsFunction[StringValue] {
    override val elemNames: List[String] = List("form")
    override def fromParams(params: FormRunnerParams): Option[StringValue] = Some(params.form)
  }

  // Can change when creating a new document id
  class FRDocumentId extends FRParamsFunction[StringValue] {
    override val elemNames: List[String] = List("document")
    override def fromParams(params: FormRunnerParams): Option[StringValue] = params.document.map(stringToStringValue)
  }

  // Can change between draft and non-draft
  class FRIsDraft extends FRParamsFunction[BooleanValue] {
    override val elemNames: List[String] = List("draft")
    override def fromParams(params: FormRunnerParams): Option[BooleanValue] = Some(booleanToBooleanValue(params.isDraft.getOrElse(false)))
  }

  // Cannot change between design-time and non-design-time over the course of the form's lifetime
  class FRIsDesignTime extends DefaultFunctionSupport with AddToPathMap {
    override def evaluateItem(context: XPathContext): BooleanValue =
      FormRunner.isDesignTime(FormRunnerParams())
  }

  // Cannot change between readonly and non-readonly mode over the course of the form's lifetime
  class FRIsReadonlyMode extends DefaultFunctionSupport with AddToPathMap {
    override def evaluateItem(context: XPathContext): BooleanValue =
      FormRunnerParamsOpt().exists(p => FormRunner.isReadonlyMode(p))
  }

  // A given form version does not change over the course of the form's lifetime
  class FRFormVersion extends DefaultFunctionSupport with AddToPathMap {
    override def evaluateItem(context: XPathContext): IntegerValue =
      FormRunnerParams().formVersion
  }

  // A given attachment form version does not change over the course of the form's lifetime
  class FRAttachmentFormVersion extends DefaultFunctionSupport with AddToPathMap {
    override def evaluateItem(context: XPathContext): IntegerValue =
      FRComponentParamSupport.attachmentFormVersion(FormRunnerParams())
  }
}
