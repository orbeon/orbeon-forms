package org.orbeon.fr.offline

import org.orbeon.dom.{Document, Element}
import org.orbeon.oxf.fr.library.{FormRunnerDateSupportFunctionLibrary, FormRunnerErrorSummaryFunctionLibrary, FormRunnerFunctionLibrary, FormRunnerInternalFunctionLibrary}
import org.orbeon.oxf.http.BasicCredentials
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.xforms.App
import org.orbeon.xforms.offline.demo.OfflineDemo
import org.orbeon.oxf.xforms.processor.XFormsURIResolver
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.utils.Configuration
import org.orbeon.xforms.offline.demo.OfflineDemo.CompiledForm
import org.scalajs.dom.{XMLHttpRequest, html}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}


@JSExportTopLevel("FormRunnerOffline")
object FormRunnerOffline extends App {

  def onOrbeonApiLoaded(): Unit = {
    OfflineDemo.onOrbeonApiLoaded()

    // Expose the API in the usual place
    val orbeonDyn = g.window.ORBEON

    val frDyn = {
      if (js.isUndefined(orbeonDyn.fr))
        orbeonDyn.fr = new js.Object
      orbeonDyn.fr
    }

    frDyn.FormRunnerOffline = js.Dynamic.global.FormRunnerOffline
  }

  def onPageContainsFormsMarkup(): Unit =
    OfflineDemo.onPageContainsFormsMarkup()

  @JSExport
  def renderForm(
    container    : html.Element,
    compiledForm : CompiledForm,
    appName      : String,
    formName     : String,
    mode         : String
  ): Unit =
    OfflineDemo.renderCompiledForm(
      container,
      compiledForm,
      List(
        FormRunnerFunctionLibrary,
        FormRunnerInternalFunctionLibrary,
        FormRunnerDateSupportFunctionLibrary,
        FormRunnerErrorSummaryFunctionLibrary
      ),
      Some(
        new XFormsURIResolver {
          def readAsDom4j(urlString: String, credentials: BasicCredentials): Document =
            urlString match {
              case "input:instance" =>

                val root = Element("request")

                root.addElement("app").addText(appName)
                root.addElement("form").addText(formName)
                root.addElement("form-version").addText("1") // TODO
//                  root.addElement("document")
                root.addElement("document").addText(CoreCrossPlatformSupport.randomHexId) // temp as mode is not read correctly!
                root.addElement("mode").addText(mode)

                Document(root)
              case _ =>
                throw new UnsupportedOperationException(s"resolving `$urlString")
            }

          def readAsTinyTree(configuration: Configuration, urlString: String, credentials: BasicCredentials): NodeInfo = {
            throw new UnsupportedOperationException(s"resolving readonly `$urlString")
          }
        }
      )
    )

  @JSExport
  def testLoadAndRenderForm(
    container : html.Element,
    appName   : String,
    formName  : String,
    mode      : String
  ): Unit =
//    fetchCompiledForm(s"http://localhost:9090/orbeon/xforms-compiler/service/compile/date.xhtml") foreach { text =>
    fetchCompiledForm(s"http://localhost:9090/orbeon/fr/service/$appName/$formName/compile") foreach { compiledForm =>
      renderForm(container, compiledForm, appName, formName, mode)
    }

  private def fetchCompiledForm(url: String): Future[String] = {
    val p = Promise[String]()
    val xhr = new XMLHttpRequest()
    xhr.open(
      method = "GET",
      url    = url
    )
    xhr.onload = { _ =>
      p.success(xhr.responseText)
    }
    xhr.send()

    p.future
  }
}
