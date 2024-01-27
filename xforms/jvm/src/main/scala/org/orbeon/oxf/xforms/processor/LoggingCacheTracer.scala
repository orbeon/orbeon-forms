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
package org.orbeon.oxf.xforms.processor

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xforms.state.XFormsStaticStateCache.CacheTracer


class LoggingCacheTracer(implicit logger: IndentedLogger) extends CacheTracer {

  def digestAndTemplateStatus(digestIfFound: Option[String]): Unit =
    digestIfFound match {
      case Some(digest) => debug("template and static state digest obtained from cache", List("digest" -> digest))
      case None         => debug("template and static state digest not obtained from cache.")
    }

  def staticStateStatus(found: Boolean, digest: String): Unit =
    if (found)
      debug("found up-to-date static state by digest in cache", List("digest" -> digest))
    else
      debug("did not find static state by digest in cache", List("digest" -> digest))
}
