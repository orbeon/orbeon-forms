/**
 * Copyright (C) 2018 Orbeon, Inc.
 */
package org.orbeon.oxf.fr.persistence.proxy

import java.io.{InputStream, OutputStream}

import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.util.IndentedLogger


object FieldEncryption {

  def encryptDataIfNecessary(
    request            : Request,
    requestInputStream : InputStream,
    appForm            : AppForm,
    isForXmlData       : Boolean)( // vs. attachment data
    implicit logger    : IndentedLogger
  ): Option[(InputStream, Option[Long])] = None

  def decryptDataXmlTransform(
    inputStream  : InputStream,
    outputStream : OutputStream
  ): Unit =
    IOUtils.copyStreamAndClose(inputStream, outputStream)

  def decryptAttachmentTransform(
    inputStream  : InputStream,
    outputStream : OutputStream
  ): Unit =
    IOUtils.copyStreamAndClose(inputStream, outputStream)
}
