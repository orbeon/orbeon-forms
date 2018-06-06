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
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.process.{FormRunnerRenderedFormat, SimpleProcess}
import org.orbeon.oxf.fr.{FormRunner, FormRunnerMetadata, XMLNames}
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.XFormsConstants.XFORMS_NAMESPACE_URI
import org.orbeon.oxf.xforms.analysis.model.ValidationLevel.ErrorLevel
import org.orbeon.oxf.xforms.function.xxforms.XXFormsComponentParam
import org.orbeon.oxf.xforms.function.{Instance, XFormsFunction}
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary
import org.orbeon.oxf.xml.{FunctionSupport, OrbeonFunctionLibrary, RuntimeDependentFunction, SaxonUtils}
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.`type`.Type
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

// The Form Runner function library
object FormRunnerFunctionLibrary extends OrbeonFunctionLibrary {

  import FormRunnerFunctions._

  Namespace(List(XMLNames.FR)) {

    // Register simple string functions
    for {
      ((name, _), index) ← StringGettersByName.zipWithIndex
    } locally {
      Fun(name, classOf[StringFunction], index, 0, STRING, ALLOWS_ZERO_OR_ONE)
    }

    // Register simple boolean functions
    for {
      ((name, _), index) ← BooleanGettersByName.zipWithIndex
    } locally {
      Fun(name, classOf[BooleanFunction], index, 0, BOOLEAN, ALLOWS_ZERO_OR_ONE)
    }

    // Register simple int functions
    for {
      ((name, _), index) ← IntGettersByName.zipWithIndex
    } locally {
      Fun(name, classOf[IntFunction], index, 0, INTEGER, ALLOWS_ZERO_OR_ONE)
    }

    // Register simple dateTime functions
    for {
      ((name, _), index) ← DateTimeGettersByName.zipWithIndex
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
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("control-typed-value", classOf[FRControlTypedValue], op = 0, min = 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("component-param-value", classOf[FRComponentParam], op = 0, min = 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("pdf-templates", classOf[FRListPdfTemplates], op = 0, min = 0, ANY_ATOMIC, ALLOWS_ZERO_OR_MORE)
  }
}

private object FormRunnerFunctions {

  val StringGettersByName = List(
    "mode"                        → (() ⇒ FormRunner.FormRunnerParams().mode),
    "app-name"                    → (() ⇒ FormRunner.FormRunnerParams().app),
    "form-name"                   → (() ⇒ FormRunner.FormRunnerParams().form),
    "document-id"                 → (() ⇒ FormRunner.FormRunnerParams().document.orNull),
    "form-title"                  → (() ⇒ FormRunner.formTitleFromMetadata.orNull),
    "lang"                        → (() ⇒ FormRunner.currentLang),
    "username"                    → (() ⇒ NetUtils.getExternalContext.getRequest.credentials map     (_.username) orNull),
    "user-group"                  → (() ⇒ NetUtils.getExternalContext.getRequest.credentials flatMap (_.group)    orNull),
    "relevant-form-values-string" → (() ⇒ FormRunnerMetadata.findAllControlsWithValues)
  )

  val BooleanGettersByName = List(
    "is-design-time"              → (() ⇒ FormRunner.isDesignTime),
    "is-readonly-mode"            → (() ⇒ FormRunner.isReadonlyMode),
    "is-noscript"                 → (() ⇒ false),
    "is-form-data-valid"          → (() ⇒ countValidationsByLevel(ErrorLevel) == 0),
    "is-form-data-saved"          → (() ⇒ FormRunner.isFormDataSaved),
    "is-wizard-toc-shown"         → (() ⇒ Wizard.isWizardTocShown),
    "is-wizard-body-shown"        → (() ⇒ Wizard.isWizardBodyShown),
    "is-wizard-first-page"        → (() ⇒ Wizard.isWizardFirstPage),
    "is-wizard-last-page"         → (() ⇒ Wizard.isWizardLastPage),
    "can-create"                  → (() ⇒ FormRunner.canCreate),
    "can-read"                    → (() ⇒ FormRunner.canRead),
    "can-update"                  → (() ⇒ FormRunner.canUpdate),
    "can-delete"                  → (() ⇒ FormRunner.canDelete),
    "owns-lease-or-none-required" → (() ⇒ FormRunner.userOwnsLeaseOrNoneRequired)
  )

  val IntGettersByName = List(
    "form-version"                → (() ⇒ FormRunner.FormRunnerParams().formVersion)
  )

  val DateTimeGettersByName = List(
    "created-dateTime"            → (() ⇒ FormRunner.documentCreatedDate),
    "modified-dateTime"           → (() ⇒ FormRunner.documentModifiedDate),
    "created-date"                → (() ⇒ FormRunner.documentCreatedDate), // only keep until 2017.1 or 2017.2 for backward compatibility
    "modified-date"               → (() ⇒ FormRunner.documentModifiedDate) // only keep until 2017.1 or 2017.2 for backward compatibility
  )

  private val IndexedStringFunctions = (
    for {
      ((_, fun), index) ← StringGettersByName.zipWithIndex
    } yield
      index → fun
  ).toMap

  private val IndexedBooleanFunctions = (
    for {
      ((_, fun), index) ← BooleanGettersByName.zipWithIndex
    } yield
      index → fun
  ).toMap

  private val IndexedIntFunctions = (
    for {
      ((_, fun), index) ← IntGettersByName.zipWithIndex
    } yield
      index → fun
  ).toMap

  private val IndexedDateTimeFunctions = (
    for {
      ((_, fun), index) ← DateTimeGettersByName.zipWithIndex
    } yield
      index → fun
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

      val instanceFn = new Instance
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
        actionSourceAbsoluteId = XFormsId.effectiveIdToAbsoluteId(XFormsFunction.context.sourceEffectiveId),
        targetControlName      = stringArgument(0),
        followIndexes          = booleanArgumentOpt(1) getOrElse false
      ) map { _ map (_.getStringValue): SequenceIterator
      } getOrElse
        EmptyIterator.getInstance
    }
  }

  class FRControlTypedValue extends FunctionSupport with RuntimeDependentFunction {

    override def evaluateItem(context: XPathContext): Item = {

      implicit val ctx = context

      val resolvedItems =
        FormRunner.resolveTargetRelativeToActionSourceOpt(
          actionSourceAbsoluteId = XFormsId.effectiveIdToAbsoluteId(XFormsFunction.context.sourceEffectiveId),
          targetControlName      = stringArgument(0),
          followIndexes          = booleanArgumentOpt(1) getOrElse false
        ) getOrElse
          Iterator.empty

      val allItems =
        resolvedItems map { item ⇒
          try {
            // `TypedNodeWrapper.getTypedValue` *should* return a single value or throw
            Option(item.getTypedValue.next()) getOrElse EmptySequence.getInstance
          } catch {
            case _: TypedValueException ⇒ EmptySequence.getInstance
          }
        }

      ArrayFunctions.createValue(allItems.to[Vector])
    }
  }

  // For instance, `fr:component-param-value('decimal-separator')`
  // returns the value of `oxf.xforms.xbl.fr.currency.decimal-separator.*.*`
  class FRComponentParam extends FunctionSupport with RuntimeDependentFunction {

    override def evaluateItem(context: XPathContext): Item = {

      implicit val ctx  = context
      val paramName     = QName(stringArgument(0))

      def searchWithoutAppForm : Option[AtomicValue] = XXFormsComponentParam.evaluate(paramName, Nil)
      def searchWithAppForm    : Option[AtomicValue] = {
        FormRunner.parametersInstance.isDefined.flatOption {
          val frParams      = FormRunner.FormRunnerParams()
          val appNameSuffix = List(frParams.app, frParams.form)
          XXFormsComponentParam.evaluate(paramName, appNameSuffix)
        }
      }

      searchWithAppForm
        .orElse(searchWithoutAppForm)
        .orNull
    }
  }

  class FRListPdfTemplates extends FunctionSupport with RuntimeDependentFunction {

    override def iterate(context: XPathContext): SequenceIterator =
      FormRunnerRenderedFormat.listPdfTemplates map { template ⇒
        MapFunctions.createValue(
          Map[AtomicValue, ValueRepresentation](
            (SaxonUtils.fixStringValue("path"), template.path),
            (SaxonUtils.fixStringValue("name"), template.nameOpt map stringToStringValue getOrElse EmptySequence.getInstance),
            (SaxonUtils.fixStringValue("lang"), template.langOpt map stringToStringValue getOrElse EmptySequence.getInstance)
          )
        )
      }
  }
}
