package org.orbeon.oxf.util

import cats.syntax.option._
import org.apache.commons.fileupload.disk.{DiskFileItem, DiskFileItemFactory}
import org.apache.commons.fileupload.{FileItem, FileItemIterator, FileItemStream}
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.io.IOUtils._
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.ExpirationScope.entryName
import org.orbeon.oxf.util.StringUtils._

import java.io.{File, IOException, InputStream}
import java.net.URI
import scala.util.control.NonFatal


sealed trait ExpirationScope
object ExpirationScope {
  case object Request     extends ExpirationScope
  case object Session     extends ExpirationScope
  case object Application extends ExpirationScope

  def entryName(scope: ExpirationScope): String =
    scope match {
      case ExpirationScope.Request     => "request"
      case ExpirationScope.Session     => "session"
      case ExpirationScope.Application => "application"
    }
}

object FileItemSupport {

  val Logger = LoggerFactory.createLogger("org.orbeon.tmpfiles")

  implicit class FileItemOps(private val fileItem: FileItem) extends AnyVal {

    def nameOpt: Option[String] =
      fileItem.getName.trimAllToOpt

    def contentTypeOpt: Option[String] =
      fileItem.getContentType.trimAllToOpt

    def fileLocationOpt: Option[File] =
      Option(fileItem.asInstanceOf[DiskFileItem].getStoreLocation)

    def debugFileLocation: String =
      fileLocationOpt.map(_.getCanonicalPath).getOrElse("[no location]")
  }

  import Private._

  def inputStreamToAnyURI(
    inputStream : InputStream,
    scope       : ExpirationScope
  ): (URI, Long) = {

    val fileItem = prepareFileItem(scope)
    var sizeAccumulator = 0L

    copyStreamAndClose(inputStream, fileItem.getOutputStream, read => sizeAccumulator += read, doCloseOut = true)

    val file =
      fileItem.fileLocationOpt.getOrElse(throw new IllegalStateException) |!>
      (_.createNewFile())

    Logger.debug(s"FileItemSupport created ${entryName(scope)}-scoped `FileItem` (disk location: `${file.getCanonicalPath}`)")

    (file.toURI, sizeAccumulator)
  }

  def prepareFileItem(scope: ExpirationScope): FileItem = {

    val fileItem = fileItemFactory.createItem("dummy", null, false, null)

    scope match {
      case ExpirationScope.Request     => deleteFileOnRequestEnd(fileItem)
      case ExpirationScope.Session     => deleteFileOnSessionTermination(fileItem.fileLocationOpt.filter(_.exists()))
      case ExpirationScope.Application => deleteFileOnApplicationDestroyed(fileItem)
    }

    fileItem
  }

  def renameAndExpireWithSession(existingFileUri: URI): File = {

    // Assume the file will be deleted with the request so rename it first
    val newPath = {
      val newFile = File.createTempFile("xforms_upload_", null)
      val r = newFile.getCanonicalPath
      newFile.delete()
      r
    }

    val oldFile = new File(existingFileUri)
    val newFile = new File(newPath)
    val success = oldFile.renameTo(newFile)
    try
      Logger.debug(s"${if (success) "renamed" else "could not rename"} temporary file from `${oldFile.getCanonicalPath}` to `$newPath`")
    catch {
      case _: IOException => // NOP
    }

    newFile.deleteOnExit()
    deleteFileOnSessionTermination(newFile.some)

    newFile
  }

  def urlForFileItemCreateIfNeeded(fileItem: FileItem, scope: ExpirationScope): URI =
    fileItem.fileLocationOpt match {
      case Some(file) =>
        file.createNewFile() // https://github.com/orbeon/orbeon-forms/issues/4466
        file.toURI
      case None =>
        // File does not exist on disk, must convert
        // NOTE: Conversion occurs every time this method is called. Not optimal.
        inputStreamToAnyURI(fileItem.getInputStream, scope)._1
    }

  def deleteFileItem(fileItem: FileItem, scope: Option[ExpirationScope]): Unit =
    fileItem.fileLocationOpt foreach (deleteFile(_, scope))

  def deleteFile(file: File, scopeForDebug: Option[ExpirationScope]): Unit = {

    def logIfNeeded(prefix: String, error: Option[String] = None): Unit =
      if (Logger.isDebugEnabled) {
        val scopeString = scopeForDebug.map(entryName).map(_ + "-scoped ").getOrElse("")
        val errorString = error.map(" (" + _ + ")").getOrElse("")
        Logger.debug(s"$prefix temporary ${scopeString}file: `${file.getCanonicalPath}`$errorString")
      }

    try {
      logIfNeeded("about to delete")
      if (file.delete())
        logIfNeeded("-> success deleting")
      else
        logIfNeeded("-> failure deleting")
    } catch {
      case NonFatal(t) =>
        logIfNeeded("-> failure deleting", OrbeonFormatter.getThrowableMessage(t))
    }
  }

  // The file will expire with the request
  // We now set the threshold of `DiskFileItem` to `-1` so that a file is already created in the first
  // place, so this should never create a file but just use the one from the `DiskItem`. One unclear
  // case is that of a zero-length file, which will probably not be created by `DiskFileItem` as nothing
  // is written.
  def fileFromFileItemCreateIfNeeded(fileItem: DiskFileItem): java.io.File =
    new java.io.File(FileItemSupport.urlForFileItemCreateIfNeeded(fileItem, ExpirationScope.Request))

  def asScalaIterator(i: FileItemIterator): Iterator[FileItemStream] = new Iterator[FileItemStream] {
    def hasNext: Boolean = i.hasNext
    def next(): FileItemStream = i.next()
    override def toString = "Iterator wrapping FileItemIterator" // `super.toString` is dangerous when running in a debugger
  }

  private object Private {

    lazy val fileItemFactory = new DiskFileItemFactory(0, NetUtils.getTemporaryDirectory)

    def deleteFileOnRequestEnd(fileItem: FileItem): Unit =
      PipelineContext.get.addContextListener((_: Boolean) => deleteFileItem(fileItem, ExpirationScope.Request.some))

    def deleteFileOnSessionTermination(file: => Option[File]): Unit =
      CoreCrossPlatformSupport.externalContext.getSessionOpt(false) match {
        case Some(session) =>
          try
            session.addListener(
              (_: ExternalContext.Session) => file foreach (deleteFile(_, ExpirationScope.Session.some))
            )
          catch {
            case e: IllegalStateException =>
              Logger.info(s"unable to add session listener: ${e.getMessage}")
              file foreach (deleteFile(_, ExpirationScope.Session.some)) // remove immediately
              throw e
          }
        case None =>
          // TODO: This should probably throw an error.
          Logger.warn(s"no existing session found so cannot register temporary file deletion upon session destruction: `${file.map(_.getCanonicalPath).getOrElse("[no file provided]")}`")
      }

    def deleteFileOnApplicationDestroyed(fileItem: FileItem): Unit =
      CoreCrossPlatformSupport.externalContext.getWebAppContext.addListener(
        () => deleteFileItem(fileItem, ExpirationScope.Application.some)
      )
  }
}
