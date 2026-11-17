package org.orbeon.fr

import org.orbeon.fr.DockerSupport.removeContainerByImage
import org.scalatest.*
import org.scalatest.funspec.FixtureAnyFunSpecLike


class AllClientTest
  extends FixtureAnyFunSpecLike
  with BeforeAndAfterAll
  with ClientTestSupport
  with FormRunnerApiTests
  with DynamicDropdownTests
  with NumberControlTests
  with AttachmentTests
  with DateTimeTests
  with DateTimeFormatTests
  with RecaptchaTests
  with FormBuilderSchemaDialogTests {

  val ServerExternalPort = 8888
  val OrbeonServerUrl    = externalServerUrlOpt.getOrElse(s"http://localhost:$ServerExternalPort/orbeon")

  type FixtureParam = Unit

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    if (externalServerUrlOpt.isEmpty) {
      val r = runTomcatContainer(
        containerName     = "FormRunnerTomcat",
        port              = ServerExternalPort,
        checkImageRunning = true,
        network           = None,
        configDirectory   = DefaultConfigDirectory
      )
      assert(r.isSuccess)
    }
    assert(waitForServerCookie(None, OrbeonServerUrl).isSuccess)
  }

  override protected def afterAll(): Unit =
    try {
      closePlaywright()
      if (externalServerUrlOpt.isEmpty)
        removeContainerByImage(TomcatImageName)
    } finally {
      super.afterAll()
    }

  def withFixture(test: OneArgTest): Outcome =
    withFixture(test.toNoArgTest(()))
}
