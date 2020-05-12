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
package org.orbeon.oxf.client.fb

import org.junit.Test
import org.orbeon.oxf.client.{XFormsOps, FormBuilderOps}
import org.scalatestplus.junit.AssertionsForJUnit

trait ClientNewFormParamsTest extends AssertionsForJUnit with FormBuilderOps with XFormsOps {

  import Builder._

  @Test def showNewFormDialogIfNoParameters(): Unit = {
    for {
      _ <- loadOrbeonPage("/fr/orbeon/builder/new")
      _ <- assert(elementByStaticId("fb-app-name-input").isDisplayed)
    }()
  }

  @Test def showNewFormDialogIfInvalidParameter(): Unit = {
    for {
      _ <- loadOrbeonPage("/fr/orbeon/builder/new?fr-app=acme&fr-form=o|rder&fr-title=This+is+a+great+form!&fr-description=Describe+me.")
      _ <- assert("acme"                  === elementByStaticId("fb-app-name-input").fieldText)
      _ <- assert("o|rder"                === elementByStaticId("fb-form-name-input").fieldText)
      _ <- assert("This is a great form!" === elementByStaticId("fb-title-input").fieldText)
      _ <- assert("Describe me."          === elementByStaticId("fb-description-textarea").fieldText)
    }()
  }

  @Test def doNotShowNewFormDialogIfValidParameters(): Unit = {
    for {
      _ <- loadOrbeonPage("/fr/orbeon/builder/new?fr-app=acme&fr-form=order&fr-title=This+is+a+great+form!&fr-description=Describe+me.")
      _ <- assert(countAllToolboxControlButtons == ControlsCount)
      _ <- openFormSettings()
      _ <- assert("acme"                  === elementByStaticId("fb-app-name-input").fieldText)
      _ <- assert("order"                 === elementByStaticId("fb-form-name-input").fieldText)
      _ <- assert("This is a great form!" === elementByStaticId("fb-title-input").fieldText)
      _ <- assert("Describe me."          === elementByStaticId("fb-description-textarea").fieldText)
    }()
  }
}
