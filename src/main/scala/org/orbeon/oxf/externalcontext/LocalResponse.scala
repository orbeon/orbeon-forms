/**
 * Copyright (C) 2014 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.externalcontext

import org.orbeon.connection.StreamedContent
import org.orbeon.io.IOUtils.runQuietly
import org.orbeon.io.StringBuilderWriter
import org.orbeon.oxf.externalcontext.ExternalContext.Response
import org.orbeon.oxf.http.{EmptyInputStream, Headers, StatusCode}
import org.orbeon.oxf.util.NetUtils

import java.io.*
import java.lang.ref.Cleaner
import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.util.control.NonFatal


object LocalResponse {
  val DefaultInMemoryThresholdBytes: Int = 64 * 1024
  private[externalcontext] val TempFileCleaner: Cleaner = Cleaner.create()
}

class LocalResponse(
  rewriter              : URLRewriter,
  inMemoryThresholdBytes: Int = LocalResponse.DefaultInMemoryThresholdBytes
) extends Response with CachingResponseSupport {

  private var _statusCode                         = StatusCode.Ok
  private var _serverSideRedirect: Option[String] = None
  private val _lowerCaseHeaders                   = mutable.LinkedHashMap[String, List[String]]()

  private var _stringWriter  : StringBuilderWriter  = null
  private var _printWriter   : PrintWriter          = null
  private var _spoolingStream: SpoolingOutputStream = null
  private var _inputStream   : InputStream          = null

  def streamedContent: StreamedContent = {
    val responseHeaders = capitalizedHeaders
    StreamedContent(
      inputStream   = getInputStream,
      contentType   = Headers.firstItemIgnoreCase(responseHeaders, Headers.ContentType),
      contentLength = Headers.firstNonNegativeLongHeaderIgnoreCase(responseHeaders, Headers.ContentLength),
      title         = None
    )
  }

  def serverSideRedirect: Option[String] = _serverSideRedirect

  def capitalizedHeaders: Map[String, List[String]] =
    _lowerCaseHeaders.toList map
    { case (k, v) => Headers.capitalizeCommonOrSplitHeader(k) -> v } toMap

  def getInputStream: InputStream = {
    if (_inputStream eq null) {
      _inputStream =
        if (_stringWriter ne null) {
          val bytes = _stringWriter.builder.toString.getBytes(ExternalContext.StandardCharacterEncoding)
          new ByteArrayInputStream(bytes, 0, bytes.length)
        } else if (_spoolingStream ne null) {
          _spoolingStream.toInputStream()
        } else {
          EmptyInputStream
        }
    }
    _inputStream
  }

  override def sendInputStream(inputStream: InputStream): Unit = {
    require(inputStream ne null)
    if ((_spoolingStream ne null) || (_stringWriter ne null) || (_inputStream ne null)) {
      runQuietly(inputStream.close())
      throw new IllegalStateException("Response body has already been set on `LocalResponse`")
    }
    _inputStream = inputStream
  }

  def setHeader(name: String, value: String): Unit =
    _lowerCaseHeaders += name.toLowerCase -> List(value)

  def addHeader(name: String, value: String): Unit =
    _lowerCaseHeaders += name.toLowerCase  -> (_lowerCaseHeaders.getOrElse(name.toLowerCase , Nil) :+ value)

  def getCharacterEncoding: String = null

  def getNamespacePrefix: String = rewriter.getNamespacePrefix

  def getOutputStream: OutputStream = {
    if (_inputStream ne null)
      throw new IllegalStateException("Response body stream has already been set on `LocalResponse`")
    if (_spoolingStream eq null)
      _spoolingStream = new SpoolingOutputStream(inMemoryThresholdBytes)
    _spoolingStream
  }

  def getWriter: PrintWriter = {
    if (_inputStream ne null)
      throw new IllegalStateException("Response body stream has already been set on `LocalResponse`")
    if (_stringWriter eq null) {
      _stringWriter = new StringBuilderWriter
      _printWriter = new PrintWriter(_stringWriter)
    }
    _printWriter
  }

  def isCommitted = false

  def reset(): Unit = {
    close()
    _stringWriter   = null
    _printWriter    = null
    _spoolingStream = null
    _inputStream    = null
  }

  def close(): Unit = {
    if (_inputStream ne null)
      runQuietly(_inputStream.close())
    if (_spoolingStream ne null)
      _spoolingStream.discard()
  }

  def rewriteActionURL(urlString: String): String =
    rewriter.rewriteActionURL(urlString)

  def rewriteRenderURL(urlString: String): String =
    rewriter.rewriteRenderURL(urlString)

  def rewriteActionURL(urlString: String, portletMode: String, windowState: String): String =
    rewriter.rewriteActionURL(urlString, portletMode, windowState)

  def rewriteRenderURL(urlString: String, portletMode: String, windowState: String): String =
    rewriter.rewriteRenderURL(urlString, portletMode, windowState)

  def rewriteResourceURL(urlString: String, rewriteMode: UrlRewriteMode): String =
    rewriter.rewriteResourceURL(urlString, rewriteMode)

  def sendError(sc: Int): Unit =
    this._statusCode = sc

  def sendRedirect(location: String, isServerSide: Boolean, isExitPortal: Boolean): Unit =
    if (isServerSide) {
      this._serverSideRedirect = Some(location)
    } else {
      this._statusCode = StatusCode.Found
      setHeader(Headers.LocationLower, location)
    }

  def setStatus(status: Int): Unit = {
    if (! StatusCode.isSuccessCode(status))
        responseCachingDisabled = true
    this._statusCode = status
  }

  def getStatus: Int = this._statusCode

  def setContentLength(len: Int): Unit =
    setHeader(Headers.ContentLength, len.toString)

  def setContentType(contentType: String): Unit =
    setHeader(Headers.ContentType, contentType)

  def setTitle(title: String): Unit = ()

  def getNativeResponse: AnyRef =
    throw new UnsupportedOperationException
}

private class LocalByteArrayOutputStream extends ByteArrayOutputStream {
  def getByteArray: Array[Byte] = buf
}

private final class TempFileCleanup(path: Path) extends Runnable {
  def run(): Unit = runQuietly(Files.deleteIfExists(path))
}

private final class TempFileHandle(val path: Path) {
  private val cleanable = LocalResponse.TempFileCleaner.register(this, new TempFileCleanup(path))
  def deleteNow(): Unit = cleanable.clean()
}

private class SpoolingOutputStream(
  thresholdBytes: Int
) extends OutputStream {

  private var _byteStream    : LocalByteArrayOutputStream = new LocalByteArrayOutputStream
  private var _tempFileHandle: TempFileHandle             = null
  private var _fileOut       : OutputStream               = null
  private var _closed        : Boolean                    = false

  override def write(b: Int): Unit = {
    ensureOpen()
    if (_fileOut ne null) {
      _fileOut.write(b)
    } else if (_byteStream.size + 1 <= thresholdBytes) {
      _byteStream.write(b)
    } else {
      spillToDisk()
      _fileOut.write(b)
    }
  }

  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0))
      throw new IndexOutOfBoundsException
    if (len > 0) {
      ensureOpen()
      if (_fileOut ne null) {
        _fileOut.write(b, off, len)
      } else if (_byteStream.size.toLong + len.toLong <= thresholdBytes.toLong) {
        _byteStream.write(b, off, len)
      } else {
        spillToDisk()
        _fileOut.write(b, off, len)
      }
    }
  }

  override def flush(): Unit =
    if (_fileOut ne null)
      _fileOut.flush()

  override def close(): Unit =
    if (! _closed) {
      _closed = true
      if (_fileOut ne null)
        _fileOut.close()
    }

  def toInputStream(): InputStream = {
    close()
    if (_tempFileHandle ne null) {
      val handle = _tempFileHandle
      _tempFileHandle = null
      val rawIn =
        try Files.newInputStream(handle.path)
        catch {
          case NonFatal(t) =>
            handle.deleteNow()
            throw t
        }
      new FilterInputStream(rawIn) {
        // Hold `handle` strongly for the lifetime of this `InputStream` and delete immediately on `close()`.
        private val retainedHandle: TempFileHandle = handle
        override def close(): Unit =
          try super.close()
          finally retainedHandle.deleteNow()
      }
    } else if (_byteStream ne null) {
      new ByteArrayInputStream(_byteStream.getByteArray, 0, _byteStream.size)
    } else {
      EmptyInputStream
    }
  }

  def discard(): Unit = {
    runQuietly(close())
    if (_tempFileHandle ne null) {
      _tempFileHandle.deleteNow()
      _tempFileHandle = null
    }
    _byteStream = null
    _fileOut    = null
  }

  private def ensureOpen(): Unit =
    if (_closed)
      throw new IOException("Stream closed")

  private def spillToDisk(): Unit = {
    val tempDir = NetUtils.getTemporaryDirectory.toPath
    Files.createDirectories(tempDir)
    val path = Files.createTempFile(tempDir, "orbeon-local-response-", ".tmp")
    path.toFile.deleteOnExit()
    val handle = new TempFileHandle(path)

    val out =
      try new BufferedOutputStream(Files.newOutputStream(path))
      catch {
        case NonFatal(t) =>
          handle.deleteNow()
          throw t
      }

    try {
      if (_byteStream.size > 0)
        out.write(_byteStream.getByteArray, 0, _byteStream.size)
      _tempFileHandle = handle
      _fileOut        = out
      _byteStream     = null
    } catch {
      case NonFatal(t) =>
        runQuietly(out.close())
        handle.deleteNow()
        throw t
    }
  }
}
