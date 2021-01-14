package org.orbeon.fr.offline

import org.log4s.Info
import org.log4s.log4sjs.LevelThreshold
import org.orbeon.dom.{Document, Element}
import org.orbeon.fr.FormRunnerApp
import org.orbeon.oxf.fr.library._
import org.orbeon.oxf.http.{BasicCredentials, Headers, HttpMethod, StatusCode}
import org.orbeon.oxf.util
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.processor.XFormsURIResolver
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.utils.Configuration
import org.orbeon.xforms.embedding.{SubmissionProvider, SubmissionRequest, SubmissionResponse}
import org.orbeon.xforms.offline.demo.OfflineDemo.CompiledForm
import org.orbeon.xforms.offline.demo.{LocalClientServerChannel, OfflineDemo}
import org.orbeon.xforms.{App, XFormsApp}
import org.scalajs.dom.experimental.{Headers => FetchHeaders}
import org.scalajs.dom.html

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.typedarray.Uint8Array


object DemoSubmissionProvider extends SubmissionProvider {

  private var store = Map[String, (Option[String], Uint8Array)]()

  def submit(req: SubmissionRequest): SubmissionResponse = {

    HttpMethod.withNameInsensitive(req.method) match {
      case HttpMethod.GET =>
        // TODO: check pathname is persistence path
        store.get(req.url.pathname) match {
          case Some((responseContentTypeOpt, responseBody)) =>
            new SubmissionResponse {
              val statusCode = StatusCode.Ok
              val headers    = new FetchHeaders(responseContentTypeOpt.toArray.toJSArray.map(x => js.Array(Headers.ContentType, x)))
              val body       = responseBody
            }
          case None =>
            new SubmissionResponse {
              val statusCode = StatusCode.NotFound
              val headers    = new FetchHeaders
              val body       = new Uint8Array(0)
            }
        }
      case HttpMethod.PUT =>

        // TODO: check pathname is persistence path
        val existing = store.contains(req.url.pathname)
        store += req.url.pathname -> (req.headers.get(Headers.ContentType).toOption -> req.body.getOrElse(throw new IllegalArgumentException))

        new SubmissionResponse {
          val statusCode = if (existing) StatusCode.Ok else StatusCode.Created
          val headers    = new FetchHeaders
          val body       = new Uint8Array(0)
        }
      case _ => ???
    }
  }

  def submitAsync(req: SubmissionRequest): js.Promise[SubmissionResponse] = ???
}

trait FormRunnerProcessor {

  @JSExport
  def renderForm(
    container    : html.Element,
    compiledForm : CompiledForm,
    appName      : String,
    formName     : String,
    mode         : String,
    documentId   : js.UndefOr[String]
  ): Unit

  @JSExport
  def destroyForm(container: html.Element): Unit =
    OfflineDemo.destroyForm(container)
}

@JSExportTopLevel("FormRunnerOffline")
object FormRunnerOffline extends App with FormRunnerProcessor {

  def onOrbeonApiLoaded(): Unit = {
    XFormsApp.onOrbeonApiLoaded(LocalClientServerChannel)
    FormRunnerApp.onOrbeonApiLoaded2()

    // Expose the API in the usual place
    val orbeonDyn = g.window.ORBEON

    val frDyn = {
      if (js.isUndefined(orbeonDyn.fr))
        orbeonDyn.fr = new js.Object
      orbeonDyn.fr
    }

    frDyn.FormRunnerOffline = js.Dynamic.global.FormRunnerOffline

    // Initialize logging
    import org.log4s.log4sjs.Log4sConfig._
    setLoggerThreshold("", LevelThreshold(Info))
  }

  def onPageContainsFormsMarkup(): Unit = {
    XFormsApp.onPageContainsFormsMarkup()
    FormRunnerApp.onPageContainsFormsMarkup2()
  }

  @JSExport
  def configure(
    submissionProvider : js.UndefOr[SubmissionProvider]
  ): FormRunnerProcessor = {
    util.Connection.submissionProvider = submissionProvider.toOption
    this
  }

  private val FormRunnerFunctionLibraryList: FunctionLibrary = {

    val formRunnerLibraries =
      List(
        FormRunnerFunctionLibrary,
        FormRunnerInternalFunctionLibrary,
        FormRunnerDateSupportFunctionLibrary,
        FormRunnerErrorSummaryFunctionLibrary,
        FormRunnerSecureUtilsFunctionLibrary,
        FormRunnerPersistenceFunctionLibrary,
        FormRunnerGridDataMigrationFunctionLibrary,
        FormRunnerSimpleDataMigrationFunctionLibrary
      )

      new FunctionLibraryList |!> (fll =>
        (OfflineDemo.XFormsFunctionLibraries.iterator ++ formRunnerLibraries).foreach(fll.addFunctionLibrary)
      )
  }

  @JSExport
  def renderForm(
    container    : html.Element,
    compiledForm : CompiledForm,
    appName      : String,
    formName     : String,
    mode         : String,
    documentId  : js.UndefOr[String]
  ): Unit = // TODO: js.Promise[Something]
    OfflineDemo.renderCompiledForm(
      container,
      compiledForm,
      FormRunnerFunctionLibraryList,
      Some(
        new XFormsURIResolver {
          def readAsDom4j(urlString: String, credentials: BasicCredentials): Document =
            urlString match {
              case "input:instance" =>

                val root = Element("request")

                root.addElement("app").addText(appName)
                root.addElement("form").addText(formName)
                root.addElement("form-version").addText("1") // TODO
                val documentElem = root.addElement("document")
                documentId foreach documentElem.addText
//                root.addElement("document").addText(CoreCrossPlatformSupport.randomHexId) // temp as mode is not read correctly!
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
    container  : html.Element,
    appName    : String,
    formName   : String,
    mode       : String,
    documentId : js.UndefOr[String]
  ): Unit = {

    configure(DemoSubmissionProvider)

//    fetchCompiledForm(s"http://localhost:9090/orbeon/xforms-compiler/service/compile/date.xhtml") foreach { text =>
    OfflineDemo.fetchCompiledFormForTesting(s"${OfflineDemo.findBasePathForTesting}/fr/service/$appName/$formName/compile") foreach { compiledForm =>
      println(s"xxx fetched string length: ${compiledForm.size}")
      renderForm(container, compiledForm, appName, formName, mode, documentId)
    }
  }
}
