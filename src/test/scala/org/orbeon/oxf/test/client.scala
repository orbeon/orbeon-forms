/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.test

import org.openqa.selenium.WebDriver
import org.orbeon.oxf.util.ScalaUtils._
import org.scalatest.concurrent.Eventually._
import org.scalatest.selenium.WebBrowser
import org.scalatest.time._
import org.junit.{AfterClass, BeforeClass}
import org.openqa.selenium.firefox.FirefoxDriver

// Basic client API
trait OrbeonFormsOps extends OrbeonClientBase with WebBrowser {

    implicit def webDriver: WebDriver = OrbeonClientBase.driver

    implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(100, Millis)))

    def loadOrbeonPage(path: String) =
        go to "http://localhost:8080/orbeon/" + dropStartingSlash(path)

    def waitForAjaxResponse() = eventually {
        assert(true == executeScript("return ! ORBEON.xforms.Globals.requestInProgress && ORBEON.xforms.Globals.eventQueue.length == 0"))
    }

    def $(selector: String) = cssSelector(selector).element.underlying
}

// Form Runner API
trait FormRunnerOps extends OrbeonFormsOps {

    def gotoSummary(app: String, form: String) = loadOrbeonPage("/fr/" + app + "/" + form + "/summary")

    def summaryFirst() = summaryNavButton("First")
    def summaryNext()  = summaryNavButton("Next")
    def summaryPrev()  = summaryNavButton("Prev")
    def summaryLast()  = summaryNavButton("Last")

    private def summaryNavButton(alt: String) = {
        click on xpath("//img[@alt = '" + alt + "']").element
        waitForAjaxResponse()
    }

    def summaryPaging = cssSelector(".fr-paging-numbers:not(.xforms-group-begin-end)").element.text
}

// The abstract base class has static forwarders to the object. This means that any class deriving from OrbeonClientBase
// will have the @BeforeClass/@AfterClass annotations. This also means that the web driver is started and stopped once
// for each class deriving from OrbeonClientBase.
abstract class OrbeonClientBase

object OrbeonClientBase {

    private var _driver: WebDriver = _
    def driver = _driver ensuring (_ ne null)

    @BeforeClass
    def createAndStartService() =
        _driver = new FirefoxDriver()

    @AfterClass
    def createAndStopService() =
        _driver.quit()
}