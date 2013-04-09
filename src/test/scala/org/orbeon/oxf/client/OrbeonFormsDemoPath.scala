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
import org.scalatest.concurrent.Eventually._
import org.scalatest.junit.MustMatchersForJUnit
import org.openqa.selenium.interactions.{Actions, Action}
import org.openqa.selenium.Keys

trait OrbeonFormsDemoPath extends MustMatchersForJUnit with FormRunnerOps { // with AssertionsForJUnit

    // RFE: split into parts so we can start in the middle
    @Test def demoPath(): Unit = {

        loadHomePage()
        eventually(pageTitle must be ("Creating forms with Form Builder"))
        // For Chrome, which in some cases (like this one) is unable to click on the anchor if not visible
        executeScript("window.scrollTo(0, $(document).height())")
        click on partialLinkText("examples coded")
        eventually(pageTitle must be ("Creating forms by writing XForms by hand"))

        // RFE: test XForms examples

        click on partialLinkText("creating forms")
        eventually(pageTitle must be ("Creating forms with Form Builder"))

        click on linkText("Controls Form")
        // https://github.com/orbeon/orbeon-forms/issues/887
        //eventually(assert(pageTitle === "Form Builder Controls"))

        eventually(assert(Wizard.pageSelected("text-controls")))

        val inputControl  = elementByStaticId("input-control")
        val passwordField = elementByStaticId("secret-control")

        inputControl.classes  must not contain ("xforms-visited")
        passwordField.classes must not contain ("xforms-visited")

        passwordField.classes must contain ("xforms-required")

        inputControl.tabOut()
        inputControl.classes must contain ("xforms-visited")
        passwordField.classes must not contain ("xforms-visited")

        passwordField.tabOut()
        passwordField.classes must contain ("xforms-visited")

        Wizard.nextPage()
        assert(! Wizard.pageSelected("text-controls"))
        assert(Wizard.pageSelected("typed-controls"))

        // RFE: etc.
    }
}
