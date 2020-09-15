/**
 * Copyright (C) 2018 Orbeon, Inc.
 */
package org.orbeon.oxf.fr.persistence.proxy

import java.io.InputStream

import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.util.IndentedLogger

object FieldEncryption {

  def encryptDataIfNecessary(
    request            : Request,
    requestInputStream : InputStream,
    app                : String,
    form               : String,
    filename           : Option[String])(
    implicit logger    : IndentedLogger
  ): Option[(InputStream, Option[Long])] = None
}
