package org.orbeon.fr.offline

import org.log4s.{Debug, Info}
import org.orbeon.dom.{Document, Element}
import org.orbeon.fr.FormRunnerApp
import org.orbeon.oxf.fr.library._
import org.orbeon.oxf.http.BasicCredentials
import org.orbeon.oxf.util
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.processor.XFormsURIResolver
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.utils.Configuration
import org.orbeon.xforms.embedding.SubmissionProvider
import org.orbeon.xforms.offline.demo.OfflineDemo.{CompiledForm, SerializedForm}
import org.orbeon.xforms.offline.demo.{LocalClientServerChannel, OfflineDemo}
import org.orbeon.xforms.{App, XFormsApp}
import org.scalajs.dom.html

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.JSConverters._
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.|


trait FormRunnerProcessor {

  @JSExport
  def compileForm(
    serializedForm  : SerializedForm,
    functionLibrary : FunctionLibrary
  ): CompiledForm

  @JSExport
  def renderForm(
    container  : html.Element,
    inputForm  : SerializedForm | CompiledForm,
    appName    : String, // Q: do we need this? We are passing the form definition!
    formName   : String, // Q: do we need this? We are passing the form definition!
    mode       : String,
    documentId : js.UndefOr[String]
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
    setLoggerThreshold("", Info)
    setLoggerThreshold("org.orbeon.oxf.xforms.processor.XFormsServer", Debug)
    setLoggerThreshold("org.orbeon.offline", Debug)
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
        FormRunnerSimpleDataMigrationFunctionLibrary,
        FormRunnerNumberSupportFunctionLibrary
      )

      new FunctionLibraryList |!> (fll =>
        (OfflineDemo.XFormsFunctionLibraries.iterator ++ formRunnerLibraries).foreach(fll.addFunctionLibrary)
      )
  }

  @JSExport
  def compileForm(
    serializedForm  : SerializedForm,
    functionLibrary : FunctionLibrary
  ): CompiledForm =
    OfflineDemo.compileForm(
      serializedForm,
      functionLibrary
    )

  @JSExport
  def renderForm(
    container  : html.Element,
    inputForm  : SerializedForm | CompiledForm,
    appName    : String, // Q: do we need this? We are passing the form definition!
    formName   : String, // Q: do we need this? We are passing the form definition!
    mode       : String,
    documentId : js.UndefOr[String]
  ): Unit = // TODO: js.Promise[Something]
    OfflineDemo.renderCompiledForm(
      container,
      inputForm,
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
  def testLoadAndCompileForm(
    appName  : String,
    formName : String // TODO: version
  ): js.Promise[CompiledForm] = {

    configure(DemoSubmissionProvider)

    val future =
      OfflineDemo.fetchSerializedFormForTesting(s"${OfflineDemo.findBasePathForTesting}/fr/service/$appName/$formName/compile") map { serializedForm =>
        println(s"fetched serialized form from server for `$appName`/`$formName`, string length: ${serializedForm.size}")
        compileForm(serializedForm, FormRunnerFunctionLibraryList)
      }

    future.toJSPromise
  }

  @JSExport
  def testLoadAndRenderForm(
    container  : html.Element,
    appName    : String,
    formName   : String,
    mode       : String,
    documentId : js.UndefOr[String]
  ): Unit = {

    configure(DemoSubmissionProvider)

    OfflineDemo.fetchSerializedFormForTesting(s"${OfflineDemo.findBasePathForTesting}/fr/service/$appName/$formName/compile") foreach { serializedForm =>
      println(s"fetched serialized form from server for `$appName`/`$formName`, string length: ${serializedForm.size}")
      renderForm(container, serializedForm, appName, formName, mode, documentId)
    }
  }
}
