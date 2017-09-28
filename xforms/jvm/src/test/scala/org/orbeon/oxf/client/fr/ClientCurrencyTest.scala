/**
 *  Copyright (C) 2013 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.client.fr

import org.junit.Test
import org.orbeon.oxf.client.FormRunnerOps
import org.scalatest.junit.AssertionsForJUnit

trait ClientCurrencyTest extends AssertionsForJUnit with FormRunnerOps {

  // https://github.com/orbeon/orbeon-forms/issues/1026
  @Test def displayUpdateWhenNoXFormsUpdate(): Unit = {

    val currencyInput = cssSelector(".xbl-fr-currency .xbl-fr-number-visible-input")
    val emailInput = cssSelector(".xforms-type-email input")

    for {
      _ ← loadOrbeonPage("/fr/orbeon/controls/new")
      _ ← clickOn(LinkTextQuery("Typed Controls"))
    }()

    def enterCheck(input: String, result: String) = for {
      _ ← clickOn(currencyInput)
      _ ← textField(currencyInput).value = input
      _ ← clickOn(emailInput)
      _ ← assert(textField(currencyInput).value === result)
    }()

    enterCheck(".9", "0.90")
    enterCheck(".9998", ".9998")
  }
}
