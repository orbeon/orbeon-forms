package org.orbeon.oxf.test

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.remote.DesiredCapabilities
import org.openqa.selenium.remote.RemoteWebDriver
import org.junit.Assert.assertEquals
import org.openqa.selenium.{JavascriptExecutor, By, WebDriver}
import java.io.File

class SeleniumTest extends AssertionsForJUnit {

    import SeleniumTest._
    private var driver: WebDriver = _
    private var js: JavascriptExecutor = _

    @Test
    def testEventProperties() {
        driver.get("http://localhost:8080/orbeon/xforms-sandbox/sample/test-event-properties")
        checkOutputs(Seq("triggered" → "false", "p1" → "", "p2" → ""))
        $("#send-event button").click()
        waitForAjaxResponse()
        checkOutputs(Seq("triggered" → "true", "p1" → "", "p2" → ""))
        //checkOutputs(Seq("triggered" → "true", "p1" → "v1", "p2" → "v2"))
    }

    @Before
    def createDriver() {
        driver = new RemoteWebDriver(service.getUrl, DesiredCapabilities.chrome())
        js = driver.asInstanceOf[JavascriptExecutor]
    }

    @After
    def quitDriver() {
        driver.quit()
    }

    private def $(selector: String) = driver findElement By.cssSelector(selector)
    private def checkOutputs(outputs: Seq[(String, String)]) {
        outputs.foreach { case (cssClass, expected) ⇒ {
            val actual = $("." + cssClass + " span").getText
            assertEquals(expected, actual)
        }}
    }

    private def waitForAjaxResponse() {
        while (js.executeScript("""return ORBEON.xforms.Globals.requestInProgress
                                       || ORBEON.xforms.Globals.eventQueue.length > 0""").asInstanceOf[Boolean])
            Thread.sleep(100)
    }
}

object SeleniumTest {

    private var service: ChromeDriverService = _

    @BeforeClass
    def createAndStartService() {
        service = ChromeDriverService.createDefaultService()
        service.start()
    }

    @AfterClass
    def createAndStopService() {
        service.stop()
    }
}

