package org.orbeon.fr.offline

import cats.syntax.option._
import io.circe.generic.auto._
import io.circe.parser.decode
import org.log4s.{Debug, Info}
import org.orbeon.dom.{Document, Element}
import org.orbeon.facades.{Fflate, JSZip, TextDecoder, ZipObject}
import org.orbeon.fr.FormRunnerApp
import org.orbeon.oxf.fr.library._
import org.orbeon.oxf.http.{BasicCredentials, StatusCode, StreamedContent}
import org.orbeon.oxf.util
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.{Connection, ConnectionResult, PathUtils, XPath}
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache
import org.orbeon.oxf.xforms.model.InstanceCaching
import org.orbeon.oxf.xforms.processor.XFormsURIResolver
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.utils.Configuration
import org.orbeon.xforms.embedding.SubmissionProvider
import org.orbeon.xforms.offline.OfflineSupport
import org.orbeon.xforms.offline.demo.LocalClientServerChannel
import org.orbeon.xforms._
import org.scalajs.dom.html
import org.orbeon.oxf.util.Logging._

import java.io.InputStream
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}
import scala.util.{Failure, Try}


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

  import OfflineSupport._
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

  def onPageContainsFormsMarkup(): Unit =
    XFormsApp.onPageContainsFormsMarkup()

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

  private def findFormInZipDataAndHookupResolvers(
    zipValues : List[(String, Uint8Array)],
    appName   : String,
    formName  : String
  ): String = {

    debug(s"got `zipValues`")

    // Read and decode the manifest
    val manifestEntries = findManifestEntries(zipValues).get

    // Collect resources described in the manifest
    val resourcesByUri = {

      val resourcesByZipPath = zipValues.toMap

      manifestEntries flatMap { case ManifestEntry(uri, zipPath, contentType) =>
        resourcesByZipPath.get(zipPath) map { data =>
          uri -> (data, contentType)
        }
      } toMap
    }

    debug(s"computed `resourcesByUri`")

    val NormalizedResourcesPath = "/fr/service/i18n/fr-resources/orbeon/offline"

    // Pre-cache resources if needed
    resourcesByUri collectFirst {
      case (k, (data, _)) if k.startsWith("/fr/service/i18n/fr-resources/") =>

        // Lazy so that we do the work zero or one time whether the document is already in
        // the cache for the specific app/form names, already in the cache for other app/form
        // names, or not in the cache at all.
        lazy val formRunnerResourcesXmlDoc = {
          XFormsCrossPlatformSupport.readTinyTree(
            configuration  = XPath.GlobalConfiguration,
            inputStream    = new Uint8ArrayInputStream(data),
            systemId       = NormalizedResourcesPath,
            handleXInclude = false,
            handleLexical  = true
          )
        }

        // We want to store the XML in the cache for both the normalized path AND the regular path, but
        // it's the same immutable document in both cases. We store the normalized path so that we can
        // easily find it in the cache. We store the regular path so that Form Runner will find that.
        // Here we `foldLeft()` so that we ensure parsing the XML at most once.
        List(NormalizedResourcesPath, s"/fr/service/i18n/fr-resources/$appName/$formName")
          .foldLeft(None: Option[DocumentNodeInfoType]) {
            case (prevDocOpt, path) =>
              val instanceCaching = InstanceCaching(-1, handleXInclude = false, path, None)
              XFormsServerSharedInstancesCache.findContent(
                instanceCaching  = instanceCaching,
                readonly         = true,
                exposeXPathTypes = false
              ) match {
                case None =>
                  debug(s"preemptively storing into cache for `$path`")
                  val newDoc = prevDocOpt.getOrElse(formRunnerResourcesXmlDoc)
                  XFormsServerSharedInstancesCache.sideLoad(
                    instanceCaching = instanceCaching,
                    doc             = newDoc
                  )
                  newDoc.some
                case Some(existingDoc) =>
                  existingDoc.some
              }
          }
    }

    debug(s"resources found in Zip file: ${resourcesByUri.keys mkString ", " }")

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

      resourcesByUri.get(updatedUrlString) map { case (data, contentType) =>
        ConnectionResult(
          url                = updatedUrlString,
          statusCode         = StatusCode.Ok,
          headers            = Map.empty,
          content            = StreamedContent(new Uint8ArrayInputStream(data), contentType.some, data.length.toLong.some, None),
          dontHandleResponse = false,
        )
      }
    }

    // Find form
    zipValues collectFirst
      { case (path, data) if path.endsWith("form.json") => new TextDecoder().decode(data) } getOrElse
      (throw new IllegalArgumentException("missing form in Zip"))
  }

  private def compileAndCacheFormIfNeededF(
    serializedBundle : SerializedBundle,
    appName          : String, // needed for the cache!
    formName         : String, // needed for the cache!
    formVersion      : Int     // needed for the cache!
  ): Future[CompiledForm] = {

    debug(s"beginning of `compileAndCacheFormIfNeededF`")

    val cacheKey = CacheKey(appName, formName, formVersion)

    staticStateCache.get(cacheKey) match {
      case Some(compiledForm) =>
        Future(compiledForm)
      case None =>

        val jsonStringF =
          (serializedBundle: Any) match {
            case v: Uint8Array =>
              debug(s"before `decodeZipContent`")
              decodeZipContent(v.buffer) map { files =>
                findFormInZipDataAndHookupResolvers(files, appName, formName)
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

        info(s"fetched serialized form from server for `$query`, type: $resType, length: $resLength")

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

    def decodeZipContent(buffer: ArrayBuffer): Future[List[(String, Uint8Array)]] =
      Future(Fflate.unzipSync(new Uint8Array(buffer)).toList)

    def decodeZipContentJsZip(buffer: ArrayBuffer): Future[List[(String, Uint8Array)]] =
      JSZip.loadAsync(buffer).toFuture flatMap { jsZip =>

        var futures: List[Future[(String, Uint8Array)]] = Nil

        jsZip.forEach(
          (relativePath: String, zipObject: ZipObject) =>
            futures ::= zipObject.async("uint8array").toFuture map (relativePath ->)
        )

        Future.sequence(futures)
      }

    def findManifestEntries(zipValues: List[(String, Uint8Array)]): Try[Iterable[ManifestEntry]] =
      zipValues.collectFirst {
        case (ManifestEntry.JsonFilename, data) => decode[Iterable[ManifestEntry]](new TextDecoder().decode(data)).toTry
      } getOrElse
        Failure(new IllegalArgumentException(s"missing `${ManifestEntry.JsonFilename}` contents"))

    class Uint8ArrayInputStream(data: Uint8Array) extends InputStream {

      private val it = data.iterator

      def read(): Int =
        if (it.hasNext)
          it.next()
        else
          -1
    }
  }
}
