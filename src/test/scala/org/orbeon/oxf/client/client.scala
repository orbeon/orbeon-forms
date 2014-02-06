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

import collection.JavaConverters._
import java.net.URL
import java.util.concurrent.TimeUnit
import org.apache.commons.lang3.StringUtils
import org.junit.{After, AfterClass, BeforeClass}
import org.openqa.selenium._
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.XFormsConstants.COMPONENT_SEPARATOR
import org.scalatest.concurrent.Eventually._
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.selenium.WebBrowser
import org.scalatest.time._

// Basic client API
trait OrbeonFormsOps extends WebBrowser with ShouldMatchers {

    type STElement = this.Element

    implicit def webDriver: WebDriver = OrbeonClientBase.driver
    implicit val patienceConfig       = PatienceConfig(timeout = scaled(OrbeonClientBase.DefaultTimeout), interval = scaled(Span(100, Millis)))

    def loadOrbeonPage(path: String) =
        go to "http://localhost:8080/orbeon/" + dropStartingSlash(path)

    def loadHomePage() = loadOrbeonPage("/")

    // After each tests, make sure to clear window.onbeforeunload so that navigation to the next test can happen without
    // showing the browser's confirmation dialog. There doesn't seem to be an easy way to accept the modal dialog at the
    // right time.
    @After def doAfterTest(): Unit =
        executeScript("window.onbeforeunload = null;")

    def closeModalAlertIfAny() =
        // Try to close any modal alert
        try webDriver.switchTo.alert.accept()
        catch {
            case _: NoAlertPresentException ⇒ // NOP
        }

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

    def patientlyClick(css: CssSelectorQuery): Unit = eventually { click on css.element }
    def patientlySendKeys(css: CssSelectorQuery, keys: CharSequence): Unit = eventually { css.element.underlying.sendKeys(keys) }

    implicit class EventuallyMonad[A](operation: ⇒ A) {
        def map[B](continuation: A ⇒ B): B                                        = continuation(eventually(operation))
        def flatMap[B](continuation: A ⇒ EventuallyMonad[B]): EventuallyMonad[B]  = continuation(eventually(operation))
        def foreach[B](continuation: A ⇒ Unit): Unit                              = continuation(eventually(operation))
    }

    // For a given id, return:
    //
    // 1. that id if it exists as is on the client
    // 2. if not, the first id for which it is a suffix
    //
    // NOTE: This doesn't handle repeat iterations yet.
    def clientId(id: String) = {
        val withPrefix = COMPONENT_SEPARATOR + id
        // NOTE: XPath 1 doesn't have ends-with()
        xpath(s"//*[@id = '$id' or contains(@id, '$withPrefix') and @id = concat(substring-before(@id, '$withPrefix'), '$withPrefix')]").element.attribute("id").get
    }

    // Remove known prefixes used with separator layout
    def removeCaseGroupRepeatPrefix(clientId: String) = {
        val prefixes = Seq("xforms-case-begin-", "group-begin-", "repeat-begin-")
        prefixes.foldLeft(clientId)(StringUtils.removeStart)
    }

    def elementByStaticId(staticId: String) =
        id(clientId(staticId)).element

    def isCaseSelected(clientIdNoCasePrefix: String) =
        executeScript(s"return ORBEON.xforms.Controls.isCaseSelected('$clientIdNoCasePrefix')") == true

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
        def tabOut(wait: Boolean = true) = sendKeys(Keys.TAB)
        def sendKeys(keys: CharSequence, wait: Boolean = true): STElement = e match {
            case control if classes("xforms-control") ⇒ // && isFocusable
                withAjaxAction(wait) {
                    nativeControlUnder(e.attribute("id").get).underlying.sendKeys(keys)
                }
                e
            case _ ⇒ throw new IllegalArgumentException("Element is not a focusable XForms control")
        }

        def findAll(by: By) = e.underlying.findElements(by).asScala.toIterator

        // Firefox: name(input) = 'INPUT' and local-name(input) = 'input'.
        // Chrome:  name(input) = 'input' and local-name(input) = 'input'.
        private def nativeControlUnder(clientId: String) =
            xpath(s"//*[@id = '$clientId']//*[local-name() = 'input' or local-name() = 'textarea' or local-name() = 'select' or local-name() = 'button']").element
    }

    implicit class TextfieldOps(val textfield: TextField) {
        def enter(): Unit = textfield.underlying.sendKeys(Keys.ENTER)
    }
}

// Form Runner API
trait FormRunnerOps extends OrbeonFormsOps {

    object Summary {
        def navigate(app: String, form: String) = loadOrbeonPage("/fr/" + app + "/" + form + "/summary")

        def firstPage() = clickElementByCSS(".fr-navigate-first a")
        def prevPage()  = clickElementByCSS(".fr-navigate-prev a")
        def nextPage()  = clickElementByCSS(".fr-navigate-next a")
        def lastPage()  = clickElementByCSS(".fr-navigate-last a")

        def paging = cssSelector(".fr-paging-numbers:not(.xforms-group-begin-end)").element.text
    }

    object Wizard {
        def nextPage(wait: Boolean = true) = clickElementByCSS(".fr-wizard-next a", wait)
        def lastPage(wait: Boolean = true) = clickElementByCSS(".fr-wizard-prev a", wait)
        def togglePage(id: String) = ???
        def pageSelected(id: String) = isCaseSelected(removeCaseGroupRepeatPrefix(clientId(id + "-section-case")))
    }
}

trait FormBuilderOps extends FormRunnerOps {

    object Builder {
        val NewContinueButton = cssSelector("*[id $= 'fb-metadata-continue-trigger'] button")
        val SaveButton = cssSelector(".fr-save-button button")

        def onNewForm[T](block: ⇒ T): Unit = {
            loadOrbeonPage("/fr/orbeon/builder/new")
            elementByStaticId("fb-application-name-input").sendKeys("a")
            elementByStaticId("fb-form-name-input").sendKeys("a")
            click on NewContinueButton
            waitForAjaxResponse()
            block
            eventually { click on SaveButton }
            waitForAjaxResponse()
        }
    }

}

// The abstract base class has static forwarders to the object. This means that any class deriving from OrbeonClientBase
// will have the @BeforeClass/@AfterClass annotations. This also means that the web driver is started and stopped once
// for each class deriving from OrbeonClientBase.
abstract class OrbeonClientBase

object OrbeonClientBase {

    private var _driver: WebDriver = _
    def driver = _driver ensuring (_ ne null)
    val DefaultTimeout = Span(10, Seconds)

    @BeforeClass
    def createAndStartService(): Unit = {
        val capabilities = (
            DesiredCapabilities.firefox()
            |!> (_.setCapability("version", "5"))
            |!> (_.setCapability("platform", Platform.XP))
        )
        val server = {
            val username = System.getenv("SAUCE_USERNAME")
            val password = System.getenv("SAUCE_ACCESS_KEY")
            new URL("http://" + username + ":" + password + "@localhost:4445/wd/hub")
        }
        _driver = new RemoteWebDriver(server, capabilities)

        // Set default timeout when searching for an element
        // (Note that this doesn't solve all our problems: the element could be in the DOM, but not visible yet,
        // or its value not set properly, or with HTML replacement we might be getting an element that will be replaced
        // at the next Ajax response.)
        driver.manage.timeouts.implicitlyWait(DefaultTimeout.totalNanos, TimeUnit.NANOSECONDS)
    }

    @AfterClass
    def createAndStopService(): Unit = {
        _driver.quit()
    }
}