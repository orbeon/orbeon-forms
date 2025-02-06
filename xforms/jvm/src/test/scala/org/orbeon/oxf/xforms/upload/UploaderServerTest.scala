package org.orbeon.oxf.xforms.upload

import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.{HttpMultipartMode, MultipartEntityBuilder}
import org.orbeon.connection.StreamedContent
import org.orbeon.datatypes.{MaximumCurrentFiles, MaximumSize}
import org.orbeon.oxf.externalcontext.{ExternalContext, LocalRequest, SafeRequestContext, TestWebAppContext}
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.test.{PipelineSupport, ResourceManagerSupport, TestHttpClient}
import org.orbeon.oxf.util.{ContentTypes, LoggerFactory}
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xforms.submission.SubmissionUtils
import org.orbeon.oxf.xforms.upload.UploaderServer.UploadResponse
import org.orbeon.oxf.xforms.upload.api.FileScanProvider
import org.orbeon.oxf.xforms.upload.api.java.{FileScan2, FileScanProvider2, FileScanResult}
import org.orbeon.xforms.Constants
import org.scalatest.funspec.AnyFunSpecLike

import java.io.{File, InputStream}
import java.net.URI
import java.util
import scala.collection.mutable
import scala.util.{Success, Try}


class UploaderServerTest extends ResourceManagerSupport with AnyFunSpecLike {

  private val Logger = LoggerFactory.createLogger(classOf[UploaderServerTest])

  def new8000BytesContentInputStream: InputStream =
    ResourceManagerWrapper.instance.getContentAsStream("/org/orbeon/oxf/util/miserables-8000.txt")

  def makeContentRequest: LocalRequest = {
    val content =
      buildMultipartContent(
        is           = new8000BytesContentInputStream,
        fieldName    = "xf-5",
        mediatypeOpt = Some("text/plain"),
        filenameOpt  = Some("miserables-8000.txt")
      )

    val headers = Map(
      Headers.ContentType   -> content.contentType.toList,
      Headers.ContentLength -> content.contentLength.toList.map(_.toString)
    )

    LocalRequest(
      safeRequestCtx     =
        SafeRequestContext(
          new TestWebAppContext(Logger, mutable.LinkedHashMap[String, AnyRef]()),
          TestHttpClient.makeBaseRequest()
        ),
      pathQuery               = "/upload",
      method                  = HttpMethod.POST,
      headersMaybeCapitalized = headers,
      content                 = Some(content)
    )
  }

  def withTestExternalContext[T](body: ExternalContext => T): T =
    PipelineSupport.withTestExternalContext(
      XFormsStateManager.sessionCreated,
      XFormsStateManager.sessionDestroyed)(
      body
    )

  describe("Test size constraints") {

    it(s"must upload when size constraints are satisfied") {
      withTestExternalContext { _ =>

        val (uploadResponses, None) =
          new TestUploaderServer(
            maximumSize       = MaximumSize.UnlimitedSize,
            maximumFiles      = MaximumCurrentFiles.UnlimitedFiles,
            allowedMediatypes = AllowedMediatypes.AllowedAnyMediatype,
            fileScanProvider  = None
          )
          .processUpload(makeContentRequest)

        uploadResponses
          .foreach { case UploadResponse(_, _, mediatypeOpt, filenameOpt, _, actualSize)  =>
            assert(filenameOpt.contains("miserables-8000.txt"))
            assert(actualSize == 8000)
            assert(mediatypeOpt.contains("text/plain"))
        }
      }
    }

    it(s"must not upload when size constraints are not satisfied") {
      withTestExternalContext { _ =>

        val (items, throwableOpt) =
          new TestUploaderServer(
            maximumSize       = MaximumSize.LimitedSize(4000),
            maximumFiles      = MaximumCurrentFiles.UnlimitedFiles,
            allowedMediatypes = AllowedMediatypes.AllowedAnyMediatype,
            fileScanProvider  = None
          )
          .processUpload(makeContentRequest)

        assert(throwableOpt.isDefined)
        assert(items.isEmpty)
      }
    }
  }

  describe("Test mediatype constraints") {

    val AllowTextPlainMediatypeVariations = List(
      AllowedMediatypes.AllowedAnyMediatype,
      AllowedMediatypes.unapply("text/plain").get,
      AllowedMediatypes.unapply("text/*").get,
      AllowedMediatypes.unapply("image/*, text/*, application/pdf").get
    )

    for (mediatype <- AllowTextPlainMediatypeVariations)
      it(s"must upload when mediatype constraints are satisfied: `$mediatype`") {
        withTestExternalContext { _ =>

          val (uploadResponses, None) =
            new TestUploaderServer(
              maximumSize       = MaximumSize.UnlimitedSize,
              maximumFiles      = MaximumCurrentFiles.UnlimitedFiles,
              allowedMediatypes = mediatype,
              fileScanProvider  = None
            )
            .processUpload(makeContentRequest)

          uploadResponses
            .foreach { case UploadResponse(_, _, mediatypeOpt, filenameOpt, _, actualSize) =>
              assert(filenameOpt.contains("miserables-8000.txt"))
              assert(actualSize == 8000)
              assert(mediatypeOpt.contains("text/plain"))
            }
        }
      }

    val DisallowTextPlainMediatypeVariations = List(
      AllowedMediatypes.unapply("text/html").get,
      AllowedMediatypes.unapply("image/*, application/pdf").get
    )

    for (mediatype <- DisallowTextPlainMediatypeVariations)
      it(s"must not upload when mediatype constraints are not satisfied: `$mediatype`") {
        withTestExternalContext { _ =>

          val (items, throwableOpt) =
            new TestUploaderServer(
              maximumSize       = MaximumSize.UnlimitedSize,
              maximumFiles      = MaximumCurrentFiles.UnlimitedFiles,
              allowedMediatypes = mediatype,
              fileScanProvider  = None
            )
            .processUpload(makeContentRequest)

          assert(throwableOpt.isDefined)
          assert(items.isEmpty)
        }
      }

    val AllowTextMediatypes = List(
      AllowedMediatypes.unapply("text/plain, text/html").get,
      AllowedMediatypes.unapply("text/*").get,
    )

    for (mediatype <- AllowTextMediatypes)
      it(s"must upload when mediatype constraints are satisfied after file scan updates them: `$mediatype`") {
        withTestExternalContext { _ =>

          val (items, None) =
            new TestUploaderServer(
              maximumSize       = MaximumSize.UnlimitedSize,
              maximumFiles      = MaximumCurrentFiles.UnlimitedFiles,
              allowedMediatypes = mediatype,
              fileScanProvider  = Some(new TestFileScanProvider(newMediatypeOpt = Some("text/html")))
            )
            .processUpload(makeContentRequest)

          items
            .foreach { case UploadResponse(_, _, mediatypeOpt, filenameOpt, _, actualSize) =>
              assert(filenameOpt.contains("miserables-8000.txt"))
              assert(actualSize == 8000)
              assert(mediatypeOpt.contains("text/html"))
            }
        }
      }

    for (mediatype <- AllowTextMediatypes)
      it(s"must not upload when mediatype constraints are not satisfied after file scan updates them: `$mediatype`") {
        withTestExternalContext { _ =>

          val (items, throwableOpt) =
            new TestUploaderServer(
              maximumSize       = MaximumSize.UnlimitedSize,
              maximumFiles      = MaximumCurrentFiles.UnlimitedFiles,
              allowedMediatypes = mediatype,
              fileScanProvider  = Some(new TestFileScanProvider(newMediatypeOpt = Some("image/png")))
            )
            .processUpload(makeContentRequest)

          assert(throwableOpt.isDefined)
          assert(items.isEmpty)
        }
      }
  }

  def buildMultipartContent(
    is          : InputStream,
    fieldName   : String,
    mediatypeOpt: Option[String],
    filenameOpt : Option[String]
  ): StreamedContent = {

    val UUID = "3160b6fe0df34b94ee31a19cb1f79c95b6351441"

    val httpEntity =
      MultipartEntityBuilder.create()
        .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
        .setMimeSubtype("form-data")
        .setBoundary("WebKitFormBoundaryicZu65oDuNAzes9o")
        .addTextBody(
          Constants.UuidFieldName,
          UUID
        )
        .addBinaryBody(
          fieldName,
          SubmissionUtils.inputStreamToByteArray(is),
          ContentType.create(mediatypeOpt.getOrElse(ContentTypes.OctetStreamContentType)),
          filenameOpt.orNull
        )
        .build()

    StreamedContent(
      inputStream   = httpEntity.getContent,
      contentType   = Option(httpEntity.getContentType).map(_.getValue),
      contentLength = Option(httpEntity.getContentLength).filter(_ >= 0),
      title         = None
    )
  }

  class TestFileScanProvider(
    newMediatypeOpt: Option[String]      = None,
    newFilenameOpt : Option[String]      = None,
    newContent     : Option[InputStream] = None
  ) extends FileScanProvider2 {
    def init(): Unit = ()
    def destroy(): Unit = ()
    def startStream(filename: String, headers: util.Map[String, Array[String]], language: String, extension: util.Map[String, Any]): FileScan2 = {
      new FileScan2 {
        def bytesReceived(bytes: Array[Byte], offset: Int, length: Int): FileScanResult =
          new FileScanResult.FileScanAcceptResult()
        def complete(file: File): FileScanResult =
          new FileScanResult.FileScanAcceptResult(
            null,
            newMediatypeOpt.orNull,
            newFilenameOpt.orNull,
            newContent.orNull,
            null
          )
        def abort(): Unit = ()
      }
    }
  }

  class TestUploaderServer(
    maximumSize      : MaximumSize,
    maximumFiles     : MaximumCurrentFiles,
    allowedMediatypes: AllowedMediatypes,
    fileScanProvider : Option[FileScanProvider2]
  ) extends UploaderServer {

    protected def getUploadConstraintsForControl(uuid: String, controlEffectiveId: String): Try[((MaximumSize, MaximumCurrentFiles, AllowedMediatypes), URI)] =
      Success(((maximumSize, maximumFiles, allowedMediatypes), URI.create("http://localhost:8080/orbeon/test-upload")))

    protected def fileScanProviderOpt: Option[Either[FileScanProvider2, FileScanProvider]] =
      fileScanProvider.map(Left.apply)
  }
}
