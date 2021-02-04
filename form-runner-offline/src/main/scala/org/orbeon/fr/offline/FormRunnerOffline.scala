package org.orbeon.fr.offline

import org.log4s.{Debug, Info}
import org.orbeon.dom.{Document, Element}
import org.orbeon.facades.{JSZip, TextDecoder}
import org.orbeon.fr.FormRunnerApp
import org.orbeon.oxf.fr.library._
import org.orbeon.oxf.http.BasicCredentials
import org.orbeon.oxf.util
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.PathUtils
import org.orbeon.oxf.xforms.processor.XFormsURIResolver
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.utils.Configuration
import org.orbeon.xforms.embedding.SubmissionProvider
import org.orbeon.xforms.offline.OfflineSupport
import org.orbeon.xforms.offline.OfflineSupport.{CompiledForm, RuntimeForm, SerializedBundle}
import org.orbeon.xforms.offline.demo.LocalClientServerChannel
import org.orbeon.xforms.{App, XFormsApp}
import org.scalajs.dom.html

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.RegExp
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.typedarray.Uint8Array


trait FormRunnerProcessor {

//  @JSExport
//  def compileForm(
//    serializedForm  : SerializedForm,
//    functionLibrary : FunctionLibrary
//  ): CompiledForm

//  @JSExport
//  def renderForm(
//    container  : html.Element,
//    inputForm  : SerializedForm | CompiledForm,
//    appName    : String, // Q: do we need this? We are passing the form definition!
//    formName   : String, // Q: do we need this? We are passing the form definition!
//    mode       : String,
//    documentId : js.UndefOr[String]
//  ): Unit

  @JSExport
  def destroyForm(container: html.Element): Unit =
    OfflineSupport.destroyForm(container)
}

@JSExportTopLevel("FormRunnerOffline")
object FormRunnerOffline extends App with FormRunnerProcessor {

  import Private._

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

  @JSExport
  def compileAndCacheFormIfNeeded(
    serializedBundle : SerializedBundle,
    appName          : String, // needed for the cache!
    formName         : String, // needed for the cache!
    formVersion      : Int     // needed for the cache!
  ): js.Promise[CompiledForm] =
    compileAndCacheFormIfNeededF(
      serializedBundle,
      appName,
      formName,
      formVersion
    ).toJSPromise

  private def compileAndCacheFormIfNeededF(
    serializedBundle : SerializedBundle,
    appName          : String, // needed for the cache!
    formName         : String, // needed for the cache!
    formVersion      : Int     // needed for the cache!
  ): Future[CompiledForm] = {

    val cacheKey = CacheKey(appName, formName, formVersion)

    staticStateCache.get(cacheKey) match {
      case Some(compiledForm) =>
        Future(compiledForm)
      case None =>

        val jsonStringF =
          (serializedBundle: Any) match {
            case v: Uint8Array =>
              JSZip.loadAsync(v.buffer).toFuture flatMap { jsZip =>
                jsZip.file(new RegExp("form\\.json")).headOption match { // first `form.json` in the Zip
                  case Some(zipObject) => zipObject.async("uint8array").toFuture map (b => new TextDecoder().decode(b))
                  case None            => Future.failed(new IllegalArgumentException("missing `form.json` in Zip archive"))
                }
              }
            case v: String => Future(v)
          }

        jsonStringF map { serializedForm =>
          val result =
            OfflineSupport.compileForm(
              serializedForm,
              FormRunnerFunctionLibraryList
            )
          staticStateCache += cacheKey -> result
          result
        }
    }
  }

  @JSExport
  def renderForm(
    container        : html.Element,
    serializedBundle : SerializedBundle,
    appName          : String, // needed for the cache!
    formName         : String, // needed for the cache!
    formVersion      : Int,    // needed for the cache!
    mode             : String,
    documentId       : js.UndefOr[String]
  ): js.Promise[RuntimeForm] = {

    val future =
      compileAndCacheFormIfNeededF(serializedBundle, appName, formName, formVersion) map { compiledFormF =>
        OfflineSupport.renderCompiledForm(
          container,
          compiledFormF,
          FormRunnerFunctionLibraryList,
          Some(
            new XFormsURIResolver {
              def readAsDom4j(urlString: String, credentials: BasicCredentials): Document =
                urlString match {
                  case "input:instance" =>

                    val root = Element("request")

                    root.addElement("app").addText(appName)
                    root.addElement("form").addText(formName)
                    root.addElement("form-version").addText(formVersion.toString)
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
      }

    future.toJSPromise
  }

  @JSExport
  def testFetchForm(
    appName     : String,
    formName    : String,
    formVersion : Int,
    zip         : Boolean
  ): js.Promise[SerializedBundle] = {

    configure(DemoSubmissionProvider)

    val query =
      PathUtils.recombineQuery(
        s"${OfflineSupport.findBasePathForTesting}/fr/service/$appName/$formName/compile",
        ("form-version" -> formVersion.toString) :: (zip list ("format" -> "zip"))
      )

    val future =
      OfflineSupport.fetchSerializedFormForTesting(query) map { serializedBundle =>

        val (resType, resLength) =
          (serializedBundle: Any) match {
            case v: Uint8Array => ("Uint8Array", v.length)
            case v: String     => ("String", v.length)
          }

        println(s"fetched serialized form from server for `$query`, type: $resType, length: $resLength")

        serializedBundle
      }

    future.toJSPromise
  }

  private object Private {

    val FormRunnerFunctionLibraryList: FunctionLibrary = {

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
          FormRunnerNumberSupportFunctionLibrary,
          FormRunnerWizardFunctionLibrary,
          FormRunnerFileMetadataFunctionLibrary
        )

      new FunctionLibraryList |!> (fll =>
        (OfflineSupport.XFormsFunctionLibraries.iterator ++ formRunnerLibraries).foreach(fll.addFunctionLibrary)
      )
    }

    case class CacheKey(appName: String, formName: String, formVersion: Int)

    var staticStateCache = Map[CacheKey, CompiledForm]()
  }
}
