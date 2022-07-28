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
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.process.{FormRunnerRenderedFormat, SimpleProcess}
import org.orbeon.oxf.fr.{FormRunner, FormRunnerMetadata, XMLNames, _}
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.StringUtils.StringOps
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, NetUtils}
import org.orbeon.oxf.xforms.function
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.function.xxforms.XXFormsComponentParam
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xml.{FunctionSupport, OrbeonFunctionLibrary, RuntimeDependentFunction, SaxonUtils}
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.`type`.Type.ITEM_TYPE
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.saxon.expr._
import org.orbeon.saxon.function.{AncestorOrganizations, UserOrganizations, UserRoles}
import org.orbeon.saxon.functions.SystemFunction
import org.orbeon.saxon.om._
import org.orbeon.saxon.value._
import org.orbeon.saxon.{ArrayFunctions, MapFunctions}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xbl.Wizard
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.XFormsNames.XFORMS_NAMESPACE_URI
import org.orbeon.xforms.analysis.model.ValidationLevel.ErrorLevel

import scala.collection.compat._


object FormRunnerFunctionLibrary extends OrbeonFunctionLibrary {

  import FormRunnerFunctions._

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

    // Register simple int functions
    for {
      ((name, _), index) <- IntGettersByName.zipWithIndex
    } locally {
      Fun(name, classOf[IntFunction], index, 0, INTEGER, ALLOWS_ZERO_OR_ONE)
    }

    // Register simple dateTime functions
    for {
      ((name, _), index) <- DateTimeGettersByName.zipWithIndex
    } locally {
      Fun(name, classOf[DateTimeFunction], index, 0, DATE_TIME, ALLOWS_ZERO_OR_ONE)
    }

    // Other functions
    Fun("user-roles",                  classOf[UserRoles],             op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE)
    Fun("user-organizations",          classOf[UserOrganizations],     op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE)

    Fun("user-ancestor-organizations", classOf[AncestorOrganizations], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("run-process-by-name", classOf[FRRunProcessByName], op = 0, min = 2, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("run-process", classOf[FRRunProcess], op = 0, min = 2, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("dataset", classOf[FRDataset], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("control-string-value", classOf[FRControlStringValue], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
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
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("pdf-templates", classOf[FRListPdfTemplates], op = 0, min = 0, ANY_ATOMIC, ALLOWS_ZERO_OR_MORE)

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

    Fun("form-runner-link", classOf[FRLinkBackToFormRunner], op = 0, min = 1, STRING, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("is-embedded", classOf[FRIsEmbedded], op = 0, min = 0, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )
  }
}

private object FormRunnerFunctions {

  val StringGettersByName = List(
    "mode"                        -> (() => FormRunnerParams().mode),
    "app-name"                    -> (() => FormRunnerParams().app),
    "form-name"                   -> (() => FormRunnerParams().form),
    "document-id"                 -> (() => FormRunnerParams().document.orNull),
    "form-title"                  -> (() => FormRunner.formTitleFromMetadata.orNull),
    "lang"                        -> (() => FormRunner.currentLang),
    "workflow-stage-value"        -> (() => FormRunner.documentWorkflowStage.orNull),
    "username"                    -> (() => NetUtils.getExternalContext.getRequest.credentials map     (_.userAndGroup.username) orNull),
    "user-group"                  -> (() => NetUtils.getExternalContext.getRequest.credentials flatMap (_.userAndGroup.groupname) orNull),
    "relevant-form-values-string" -> (() => FormRunnerMetadata.findAllControlsWithValues(html = false)),
    "wizard-current-page-name"    -> (() => Wizard.wizardCurrentPageNameOpt.orNull)
  )

  val BooleanGettersByName = List(
    "is-browser-environment"      -> (() => CoreCrossPlatformSupport.isJsEnv),
    "is-pe"                       -> (() => Version.isPE),
    "is-draft"                    -> (() => FormRunnerParams().isDraft.getOrElse(false)),
    "is-design-time"              -> (() => FormRunner.isDesignTime(FormRunnerParams())),
    "is-readonly-mode"            -> (() => FormRunner.isReadonlyMode(FormRunnerParams())),
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
    "owns-lease-or-none-required" -> (() => FormRunner.userOwnsLeaseOrNoneRequired)
  )

  val IntGettersByName = List(
    "form-version"                -> (() => FormRunnerParams().formVersion),
    "attachment-form-version"     -> (() => FRComponentParamSupport.formAttachmentVersion(FormRunnerParams()))
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

  private val IndexedIntFunctions = (
    for {
      ((_, fun), index) <- IntGettersByName.zipWithIndex
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

  class IntFunction extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(xpathContext: XPathContext): IntegerValue =
      IndexedIntFunctions(operation).apply()
  }

  class DateTimeFunction extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(xpathContext: XPathContext): DateTimeValue =
      IndexedDateTimeFunctions(operation).apply() map
        (new java.util.Date(_))                   map
        DateTimeValue.fromJavaDate                orNull
  }

  class FRRunProcessByName extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(context: XPathContext): BooleanValue =
      SimpleProcess.runProcessByName(stringArgument(0)(context), stringArgument(1)(context)).isSuccess
  }

  class FRRunProcess extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(context: XPathContext): BooleanValue =
      SimpleProcess.runProcess(stringArgument(0)(context), stringArgument(1)(context)).isSuccess
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
        _ map (_.getStringValue): SequenceIterator
      } getOrElse
        EmptyIterator.getInstance
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

      implicit val ctx  = context

      val paramName = QName(stringArgument(0))

      import XXFormsComponentParam._

      findSourceComponent(XFormsFunction.context) flatMap { sourceComponent =>

        val staticControl   = sourceComponent.staticControl
        val concreteBinding = staticControl.bindingOrThrow

        def fromAttributes: Option[AtomicValue] =
          fromElem(
            atts        = concreteBinding.boundElementAtts.lift,
            paramName   = paramName
          )

        def fromMetadataAndProperties: Option[AtomicValue] =
          FRComponentParamSupport.fromMetadataAndProperties(
            partAnalysis  = sourceComponent.container.partAnalysis,
            directNameOpt = staticControl.commonBinding.directName,
            paramName     = paramName
          )

        fromAttributes orElse fromMetadataAndProperties map {
            case paramValue: StringValue => stringToStringValue(sourceComponent.evaluateAvt(paramValue.getStringValue))
            case paramValue              => paramValue
          }

      } orNull
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

  class FRCreatedWithOrNewer extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(context: XPathContext): BooleanValue = {

      val metadataVersionOpt =
        for {
          sourceControl      <- XFormsFunction.context.container.associatedControlOpt
          part               = sourceControl.container.partAnalysis
          metadata           <- FRComponentParamSupport.findConstantMetadataRootElem(part)
          createdWithVersion <- metadata elemValueOpt Names.CreatedWithVersion
        } yield
          createdWithVersion

      metadataVersionOpt match {
        case None =>
          // If no version info the metadata, or no metadata, do as if the form was created with an old version
          false
        case Some(metadataVersion) =>
          val paramVersion = stringArgument(0)(context)
          Version.compare(metadataVersion, paramVersion).exists(_ >= 0)
      }
    }
  }

  class FRLinkBackToFormRunner extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(context: XPathContext): StringValue =
      FormRunner.buildLinkBackToFormRunnerUsePageName(stringArgument(0)(context))
  }

  class FRIsEmbedded extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(context: XPathContext): BooleanValue =
      FormRunner.isEmbedded(stringArgumentOpt(0)(context))
  }
}
