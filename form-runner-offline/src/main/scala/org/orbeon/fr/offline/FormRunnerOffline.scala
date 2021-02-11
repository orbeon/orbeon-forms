package org.orbeon.fr.offline

import cats.syntax.option._
import org.log4s.{Debug, Info}
import org.orbeon.dom.{Document, Element}
import org.orbeon.facades.{JSZip, TextDecoder, ZipObject}
import org.orbeon.fr.FormRunnerApp
import org.orbeon.oxf.fr.library._
import org.orbeon.oxf.http.{BasicCredentials, StatusCode, StreamedContent}
import org.orbeon.oxf.util
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.{Connection, ConnectionResult, PathUtils}
import org.orbeon.oxf.xforms.processor.XFormsURIResolver
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.utils.Configuration
import org.orbeon.xforms.embedding.SubmissionProvider
import org.orbeon.xforms.offline.OfflineSupport
import org.orbeon.xforms.offline.OfflineSupport.{CompiledForm, RuntimeForm, SerializedBundle}
import org.orbeon.xforms.offline.demo.LocalClientServerChannel
import org.orbeon.xforms.{App, XFormsApp, XFormsUI}
import org.scalajs.dom.html

import java.io.InputStream
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.JSConverters._
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

  @JSExport
  def warmupFormIfNeeded(
    appName          : String,
    formName         : String,
    formVersion      : Int
  ): Unit =
    OfflineSupport.renderCompiledForm(
      None,
      staticStateCache.getOrElse(
        CacheKey(appName, formName, formVersion),
        throw new IllegalStateException(s"form not in cache for $appName/$formName/$formVersion")
      ),
      FormRunnerFunctionLibraryList,
      Some(
        new XFormsURIResolver {
          def readAsDom4j(urlString: String, credentials: BasicCredentials): Document =
            urlString match {
              case "input:instance" => createFormParamInstance(appName, formName, formVersion, "test", None)
              case _                => throw new UnsupportedOperationException(s"resolving `$urlString")
            }
          def readAsTinyTree(configuration: Configuration, urlString: String, credentials: BasicCredentials): NodeInfo =
            throw new UnsupportedOperationException(s"resolving readonly `$urlString")
        }
      )
    )

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

                var futures: List[Future[(String, Either[String, Uint8Array])]] = Nil

                jsZip.forEach((relativePath: String, zipObject: ZipObject) => {
                  println(s"xxxx found `$relativePath` in zip")

                  futures ::=
                    zipObject.async("uint8array").toFuture map { data =>

                      val FormRe = "/([^/]+)/([^/]+)/([^/]+)/form/form.json".r
                      val XmlRe  = "/([^.]+).xml".r

                      relativePath -> (
                        relativePath match {
                          case FormRe(appName, formName, formVersion) =>
                            println(s"xxx got form for `$appName/$formName/$formVersion`")
                            Left(new TextDecoder().decode(data))
                          case XmlRe(path) =>
                            println(s"xxx got XML for `$path`")
                            Right(data)
                          case other =>
                            println(s"xxx got other for `$other`")
                            Right(data)
                        }
                      )
                    }
                })

                Future.sequence(futures) map { values =>

                  val resources =
                    values collect {
                      case (path, Right(data)) =>
                        if (path.startsWith("/fr"))
                          path -> data
                        else
                          s"oxf:$path" -> data // TODO xxx oxf:
                    }

                  val resourcesMap = resources.toMap

                  println(s"xxxx resources ${resourcesMap.keys mkString ", " }")

                  // Store a connection resolver that resolves to resources included in the compiled form
                  Connection.resourceResolver = (urlString: String) => {

                    // Special case: normalize Form Runner resources so that we don't have to duplicate them for each form.
                    // This prevents overriding resources for each form individually, but this is usually not necessary.
                    // And if we wanted to allow that, we should find a more efficient way to do it anyway.
                    val updatedUrlString =
                      if (urlString.startsWith("/fr/service/i18n/fr-resources/"))
                        "/fr/service/i18n/fr-resources/orbeon/offline"
                      else
                        urlString

                    resourcesMap.get(updatedUrlString) map { data =>

                      val contentType = "application/xml" // XXX TODO

                      val is = new InputStream {

                        val it = data.iterator

                        def read(): Int =
                          if (it.hasNext)
                            it.next()
                          else
                            -1
                      }

                      ConnectionResult(
                        url                = updatedUrlString,
                        statusCode         = StatusCode.Ok,
                        headers            = Map.empty,
                        content            = StreamedContent(is, contentType.some, data.length.toLong.some, None),
                        dontHandleResponse = false,
                      )
                    }
                  }

                  values collectFirst
                    { case (path, Left(json)) => json } getOrElse
                    (throw new IllegalArgumentException("missing form in Zip"))
                }
              }
            case v: String =>
              Future(v)
            case null =>
              throw new IllegalArgumentException(s"cannot pass `null` form definition Zip file if it is not in cache for key $cacheKey")
            case _ =>
              throw new IllegalArgumentException(s"unexpected form definition object passed for key $cacheKey")
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

    XFormsUI.showModalProgressPanelImmediate()

    val future =
      compileAndCacheFormIfNeededF(serializedBundle, appName, formName, formVersion) map { compiledForm =>

        try {
          OfflineSupport.renderCompiledForm(
            container.some,
            compiledForm,
            FormRunnerFunctionLibraryList,
            Some(
              new XFormsURIResolver {
                def readAsDom4j(urlString: String, credentials: BasicCredentials): Document =
                  urlString match {
                    case "input:instance" => createFormParamInstance(appName, formName, formVersion, mode, documentId.toOption)
                    case _                => throw new UnsupportedOperationException(s"resolving `$urlString")
                  }
                def readAsTinyTree(configuration: Configuration, urlString: String, credentials: BasicCredentials): NodeInfo =
                  throw new UnsupportedOperationException(s"resolving readonly `$urlString")
              }
            )
          )
        } finally {
          XFormsUI.hideModalProgressPanelImmediate()
        }
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

    def createFormParamInstance(
      appName          : String,
      formName         : String,
      formVersion      : Int,
      mode             : String,
      documentIdOpt    : Option[String]
    ): Document = {

      val root = Element("request")

      root.addElement("app").addText(appName)
      root.addElement("form").addText(formName)
      root.addElement("form-version").addText(formVersion.toString)
      val documentElem = root.addElement("document")
      documentIdOpt foreach documentElem.addText
      root.addElement("mode").addText(mode)

      Document(root)
    }
  }
}
