package org.orbeon.oxf.util

import org.apache.commons.fileupload.FileItem
import org.apache.commons.fileupload.disk.{DiskFileItem, DiskFileItemFactory}
import org.orbeon.io.IOUtils._
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.SLF4JLogging._
import org.slf4j.Logger

import java.io.{File, IOException, InputStream}
import java.net.URI


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

  import Private._

  def inputStreamToAnyURI(
    inputStream : InputStream,
    scope       : ExpirationScope)(implicit
    logger      : Logger
  ): (String, Long) = {

    val (fileItem, size) = prepareFileItemFromInputStream(inputStream, scope)

    val storeLocation = fileItem.asInstanceOf[DiskFileItem].getStoreLocation

    // Escape "+" because at least in one environment (JBoss 5.1.0 GA on OS X) not escaping the "+" in a file URL causes
    // later incorrect conversion to space.
    // 2022-02-18: Not sure if this is still relevant or needed.
    (storeLocation.toURI.toString.replace("+", "%2B"), size)
  }

  def prepareFileItem(scope: ExpirationScope)(implicit logger: Logger): FileItem = {

    val fileItem = fileItemFactory.createItem("dummy", "dummy", false, null)

    scope match {
      case ExpirationScope.Request     => deleteFileOnRequestEnd(fileItem)
      case ExpirationScope.Session     => deleteFileOnSessionTermination(fileItem)
      case ExpirationScope.Application => deleteFileOnApplicationDestroyed(fileItem)
    }

    fileItem
  }

  def renameAndExpireWithSession(existingFileUri: String)(implicit logger: Logger): File = {

      // Assume the file will be deleted with the request so rename it first
      val newPath = {
        val newFile = File.createTempFile("xforms_upload_", null)
        val r = newFile.getCanonicalPath
        newFile.delete()
        r
      }

      val oldFile = new File(new URI(existingFileUri))
      val newFile = new File(newPath)
      val success = oldFile.renameTo(newFile)
      try
        debug(s"${if (success) "renamed" else "could not rename"} temporary file from `${oldFile.getCanonicalPath}` to `$newPath`")
      catch {
        case _: IOException => // NOP
      }

      // Mark deletion of the file on exit and on session termination
      newFile.deleteOnExit()

      CoreCrossPlatformSupport.externalContext.getSessionOpt(false) match {
        case Some(session) =>
          try
            session.addListener((_: ExternalContext.Session) => NetUtils.deleteFile(newFile, logger))
          catch {
            case e: IllegalStateException =>
              info(s"unable to add session listener: ${e.getMessage}")
              NetUtils.deleteFile(newFile, logger) // remove immediately
              throw e
          }
        case None =>
          debug(s"no existing session found so cannot register temporary file deletion upon session destruction: `$newPath`")
      }

      newFile
    }

  def urlForFileItemCreateIfNeeded(fileItem: FileItem, scope: ExpirationScope)(implicit logger: Logger): String = {
    // Only a reference to the file is output (xs:anyURI)
    val diskFileItem = fileItem.asInstanceOf[DiskFileItem]
    if (! fileItem.isInMemory) {
      // File must exist on disk since `isInMemory()` returns `false`
      val file = diskFileItem.getStoreLocation
      if (! file.exists)
        file.createNewFile() // https://github.com/orbeon/orbeon-forms/issues/4466
      file.toURI.toString
    } else {
      // File does not exist on disk, must convert
      // NOTE: Conversion occurs every time this method is called. Not optimal.
      inputStreamToAnyURI(fileItem.getInputStream, scope)._1
    }
  }

  private object Private {

    lazy val fileItemFactory = new DiskFileItemFactory(0, NetUtils.getTemporaryDirectory)

    def prepareFileItemFromInputStream(
      inputStream : InputStream,
      scope       : ExpirationScope)(implicit
      logger      : Logger
    ): (FileItem, Long) = {

      val fileItem = prepareFileItem(scope)
      var sizeAccumulator = 0L

      copyStreamAndClose(inputStream, fileItem.getOutputStream, read => sizeAccumulator += read, doCloseOut = true)

      fileItem.asInstanceOf[DiskFileItem].getStoreLocation.createNewFile()

      (fileItem, sizeAccumulator)
    }

    def deleteFileOnRequestEnd(fileItem: FileItem)(implicit logger: Logger): Unit =
      PipelineContext.get.addContextListener((_: Boolean) => deleteFileItem(fileItem, ExpirationScope.Request))

    def deleteFileOnSessionTermination(fileItem: FileItem)(implicit logger: Logger): Unit =
      CoreCrossPlatformSupport.externalContext.getSessionOpt(false) match {
        case Some(session) =>
          try
            session.addListener(
              (_: ExternalContext.Session) => deleteFileItem(fileItem, ExpirationScope.Session)
            )
          catch {
            case e: IllegalStateException =>
              info(s"Unable to add session listener: ${e.getMessage}")
              deleteFileItem(fileItem, ExpirationScope.Session) // remove immediately
              throw e
          }
        case None =>
          debug(s"No existing session found so cannot register temporary file deletion upon session destruction: `${fileItem.getName}`")
      }

    def deleteFileOnApplicationDestroyed(fileItem: FileItem)(implicit logger: Logger): Unit =
      CoreCrossPlatformSupport.externalContext.getWebAppContext.addListener(
        () => deleteFileItem(fileItem, ExpirationScope.Application)
      )

    def deleteFileItem(fileItem: FileItem, scope: ExpirationScope)(implicit logger: Logger): Unit = {
      fileItem.delete()
      ifDebug {
        fileItem match {
          case diskFileItem: DiskFileItem =>
            val storeLocation = diskFileItem.getStoreLocation
            if (storeLocation ne null) {

              val entryName    = ExpirationScope.entryName(scope)
              val absolutePath = storeLocation.getAbsolutePath

              debug(s"deleting temporary $entryName-scoped file upon session destruction: `$absolutePath`")
            }
          case _ =>
        }
      }
    }
  }
}
