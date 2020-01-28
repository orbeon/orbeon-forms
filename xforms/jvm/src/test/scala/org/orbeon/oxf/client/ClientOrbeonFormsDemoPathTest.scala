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
package org.orbeon.oxf.client

import org.junit.Test
import org.openqa.selenium.By
import org.scalatest.concurrent.Eventually._
import org.scalatestplus.junit.AssertionsForJUnit

trait ClientOrbeonFormsDemoPathTest extends AssertionsForJUnit with FormRunnerOps { // with AssertionsForJUnit

  // RFE: split into parts so we can start in the middle
  @Test def demoPath(): Unit = {

    for {
      _ <- loadHomePage()
      _ <- assert(pageTitle === "Creating forms with Form Builder")

      // For Chrome, which in some cases (like this one) is unable to click on the anchor if not visible
      _ <- executeScript("window.scrollTo(0, $(document).height())")
      _ <- click on partialLinkText("examples coded")
      _ <- assert(pageTitle === "Creating forms by writing XForms by hand")

      // RFE: test XForms examples
      _ <- click on partialLinkText("creating forms")
      _ <- eventually(pageTitle should be ("Creating forms with Form Builder"))

      _ <- click on linkText("Controls Form")
      // https://github.com/orbeon/orbeon-forms/issues/887
      _ <- eventually(assert(pageTitle === "Form Builder Controls"))

      _ <- eventually(assert(Wizard.pageSelected("text-controls")))

      inputControl <- elementByStaticId("input-control")
      passwordField <- elementByStaticId("secret-control")

      _ <- inputControl.classes  should not contain ("xforms-visited")
      _ <- passwordField.classes should not contain ("xforms-visited")

      _ <- passwordField.classes should contain ("xforms-required")

      _ <- inputControl.tabOut()
      _ <- inputControl.classes should contain ("xforms-visited")
      _ <- passwordField.classes should not contain ("xforms-visited")

      _ <- passwordField.tabOut()
      _ <- passwordField.classes should contain ("xforms-visited")

      _ <- Wizard.nextPage()
      _ <- assert(! Wizard.pageSelected("text-controls"))
      _ <- assert(Wizard.pageSelected("typed-controls"))

      // Switch language and check a control label
      _ <- click on partialLinkText("Français")
      numberControl <- elementByStaticId("number-control")
      _ <- assert("Nombre" === numberControl.findAll(By.tagName("label")).next.getText)
      _ <- click on partialLinkText("English")

      // RFE: etc.
    }()
  }
}
