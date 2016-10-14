/**
 * Copyright (C) 2015 Orbeon, Inc.
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
package org.orbeon.xbl

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.oxf.xforms.control.controls.XFormsVariableControl
import org.orbeon.saxon.om.ValueRepresentation
import org.orbeon.saxon.value.Value
import org.orbeon.scaxon.XML._

object Wizard {

  //@XPathFunction
  def isWizardValidate =
    formRunnerProperty("oxf.xforms.xbl.fr.wizard.validate")(FormRunnerParams()) contains "true"

  //@XPathFunction
  def isWizardSeparateToc =
    formRunnerProperty("oxf.xforms.xbl.fr.wizard.separate-toc")(FormRunnerParams()) contains "true"

  private def findWizardState =
    XFormsAPI.resolveAs[XFormsComponentControl]("fr-view-wizard") flatMap
      (_.nestedContainer.defaultModel)                            map
      (_.getDefaultInstance.rootElement)

  def isWizardTocShown =
    findWizardState map (_ elemValue "show-toc") contains "true"

  def isWizardBodyShown =
    findWizardState map (_ elemValue "show-body") contains "true"

  private def findWizardVariableValue(staticOrAbsoluteId: String) =
    XFormsAPI.resolveAs[XFormsVariableControl](staticOrAbsoluteId) flatMap (_.valueOpt)

  private def booleanValue(value: ValueRepresentation) = value match {
    case v: Value    ⇒ v.effectiveBooleanValue
    case _           ⇒ false
  }

  def isWizardLastPage =
    findWizardVariableValue("fr-wizard-is-last-nav") exists booleanValue

  def isWizardFirstPage =
    findWizardVariableValue("fr-wizard-is-first-nav") exists booleanValue

}
