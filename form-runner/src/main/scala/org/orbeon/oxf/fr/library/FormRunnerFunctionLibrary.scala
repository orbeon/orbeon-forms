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

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.process.SimpleProcess
import org.orbeon.oxf.fr.{FormRunner, XMLNames}
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.analysis.model.ValidationLevels.ErrorLevel
import org.orbeon.oxf.xforms.function.xxforms.{XXFormsAncestorOrganizations, XXFormsUserOrganizations, XXFormsUserRoles}
import org.orbeon.oxf.xml.{FunctionSupport, OrbeonFunctionLibrary, RuntimeDependentFunction}
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.value.{BooleanValue, DateTimeValue, IntegerValue, StringValue}
import org.orbeon.scaxon.XML._
import org.orbeon.xbl.Wizard

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
    Fun("user-roles",                  classOf[XXFormsUserRoles],             op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE)
    Fun("user-organizations",          classOf[XXFormsUserOrganizations],     op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE)

    Fun("user-ancestor-organizations", classOf[XXFormsAncestorOrganizations], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("run-process-by-name", classOf[FRRunProcessByName], op = 0, min = 2, Type.ITEM_TYPE, EMPTY,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("run-process", classOf[FRRunProcess], op = 0, min = 2, Type.ITEM_TYPE, EMPTY,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )
  }
}

private object FormRunnerFunctions {

  val StringGettersByName = List(
    "mode"                 → (() ⇒ FormRunner.FormRunnerParams().mode),
    "app-name"             → (() ⇒ FormRunner.FormRunnerParams().app),
    "form-name"            → (() ⇒ FormRunner.FormRunnerParams().form),
    "document-id"          → (() ⇒ FormRunner.FormRunnerParams().document.orNull),
    "lang"                 → (() ⇒ FormRunner.currentLang.stringValue),
    "username"             → (() ⇒ NetUtils.getExternalContext.getRequest.credentials map     (_.username) orNull),
    "user-group"           → (() ⇒ NetUtils.getExternalContext.getRequest.credentials flatMap (_.group)    orNull)
  )

  val BooleanGettersByName = List(
    "is-design-time"       → (() ⇒ FormRunner.isDesignTime),
    "is-readonly-mode"     → (() ⇒ FormRunner.isReadonlyMode),
    "is-noscript"          → (() ⇒ FormRunner.isNoscript),
    "is-form-data-valid"   → (() ⇒ countValidationsByLevel(ErrorLevel) == 0),
    "is-form-data-saved"   → (() ⇒ FormRunner.isFormDataSaved),
    "is-wizard-toc-shown"  → (() ⇒ Wizard.isWizardTocShown),
    "is-wizard-body-shown" → (() ⇒ Wizard.isWizardBodyShown),
    "is-wizard-first-page" → (() ⇒ Wizard.isWizardFirstPage),
    "is-wizard-last-page"  → (() ⇒ Wizard.isWizardLastPage),
    "can-create"           → (() ⇒ FormRunner.canCreate),
    "can-read"             → (() ⇒ FormRunner.canRead),
    "can-update"           → (() ⇒ FormRunner.canUpdate),
    "can-delete"           → (() ⇒ FormRunner.canDelete)
  )

  val IntGettersByName = List(
    "form-version"         → (() ⇒ FormRunner.FormRunnerParams().formVersion)
  )

  val DateTimeGettersByName = List(
    "created-date"         → (() ⇒ FormRunner.documentCreatedDate),
    "modified-date"        → (() ⇒ FormRunner.documentModifiedDate)
  )

  val IndexedStringFunctions = (
    for {
      ((_, fun), index) ← StringGettersByName.zipWithIndex
    } yield
      index → fun
  ).toMap

  val IndexedBooleanFunctions = (
    for {
      ((_, fun), index) ← BooleanGettersByName.zipWithIndex
    } yield
      index → fun
  ).toMap

  val IndexedIntFunctions = (
    for {
      ((_, fun), index) ← IntGettersByName.zipWithIndex
    } yield
      index → fun
  ).toMap

  val IndexedDateTimeFunctions = (
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
    override def evaluateItem(context: XPathContext): Item = {
      SimpleProcess.runProcessByName(stringArgument(0)(context), stringArgument(1)(context))
      null
    }
  }

  class FRRunProcess extends FunctionSupport with RuntimeDependentFunction {
    override def evaluateItem(context: XPathContext): Item = {
      SimpleProcess.runProcess(stringArgument(0)(context), stringArgument(1)(context))
      null
    }
  }
}
