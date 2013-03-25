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
package org.orbeon.oxf.client

import org.junit.{AfterClass, BeforeClass}
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.{Keys, WebDriver}
import org.orbeon.oxf.util.ScalaUtils._
import org.scalatest.concurrent.Eventually._
import org.scalatest.selenium.WebBrowser
import org.scalatest.time._

// Basic client API
trait OrbeonFormsOps extends WebBrowser {

    type STElement = this.Element

    implicit def webDriver: WebDriver = OrbeonClientBase.driver
    implicit val patienceConfig       = PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(100, Millis)))

    def loadOrbeonPage(path: String) =
        go to "http://localhost:8080/orbeon/" + dropStartingSlash(path)

    def loadHomePage() = loadOrbeonPage("/")

    def waitForAjaxResponse() = eventually {
        assert(true == executeScript("return ! ORBEON.xforms.Globals.requestInProgress && ORBEON.xforms.Globals.eventQueue.length == 0"))
    }

    def withAjaxAction[T](wait: Boolean = true)(body: ⇒ T) = {
        val result = body
        if (wait)
            waitForAjaxResponse()
        result
    }

    def $(selector: String) = cssSelector(selector).element.underlying

    def clickElementByCSS(css: String, wait: Boolean = true) =
        withAjaxAction(wait) {
            click on cssSelector(css).element
        }

    def clickImgByAlt(alt: String, wait: Boolean = true) =
        withAjaxAction(wait) {
            click on xpath(s"//img[@alt = '$alt']").element
        }

    def clientId(id: String) = {
        val withPrefix = '$' + id
        // NOTE: XPath 1 doesn't have ends-with()
        xpath(s"//*[@id = '$id' or contains(@id, '$withPrefix') and @id = concat(substring-before(@id, '$withPrefix'), '$withPrefix')]").element.attribute("id").get
    }

    def elementByStaticId(staticId: String) = id(clientId(staticId)).element
    def isCaseSelected(clientId: String) = executeScript(s"return ORBEON.xforms.Controls.isCaseSelected('$clientId')") == true

    // Functions from xforms.js we must provide access to:
    //
    // - isRelevant
    // - isReadonly
    // - isRequired
    // - isValid
    // - getForm
    // - getCurrentValue
    // - getControlLHHA
    // - getControlForLHHA
    // - getLabelMessage
    // - getHelpMessage
    // - getAlertMessage
    // - getHintMessage
    // - setFocus
    // - removeFocus

    // Extension methods on Element
    implicit class ElementOps(val e: STElement) {
        def classes = e.attribute("class") map stringToSet getOrElse Set()
        def tabOut(wait: Boolean = true) = e match {
            case control if classes("xforms-control") ⇒ // && isFocusable
                withAjaxAction(wait) {
                    nativeControlUnder(e.attribute("id").get).underlying.sendKeys(Keys.TAB)
                }
            case _ ⇒ throw new IllegalArgumentException("Element is not a focusable XForms control")
        }

        // Firefox: name(input) = 'INPUT' and local-name(input) = 'input'.
        // Chrome:  name(input) = 'input' and local-name(input) = 'input'.
        private def nativeControlUnder(clientId: String) =
            xpath(s"//*[@id = '$clientId']//*[local-name() = 'input' or local-name() = 'textarea' or local-name() = 'select' or local-name() = 'button']").element
    }
}

// Form Runner API
trait FormRunnerOps extends OrbeonFormsOps {

    object Summary {
        def navigate(app: String, form: String) = loadOrbeonPage("/fr/" + app + "/" + form + "/summary")

        def firstPage() = clickImgByAlt("First")
        def nextPage()  = clickImgByAlt("Next")
        def prevPage()  = clickImgByAlt("Prev")
        def lastPage()  = clickImgByAlt("Last")

        def paging = cssSelector(".fr-paging-numbers:not(.xforms-group-begin-end)").element.text
    }

    object Wizard {
        def nextPage(wait: Boolean = true) = clickElementByCSS(".fr-wizard-next a", wait)
        def lastPage(wait: Boolean = true) = clickElementByCSS(".fr-wizard-prev a", wait)
        def togglePage(id: String) = ???
        def pageSelected(id: String) = isCaseSelected(clientId(id + "-section-case"))
    }
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