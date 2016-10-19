/**
  * Copyright (C) 2004 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.pipeline

import java.io.{FilterOutputStream, OutputStream, OutputStreamWriter, PrintWriter}

import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.util.URLRewriterUtils

/**
  * Simple ExternalContext for command-line applications.
  */
class CommandLineExternalContext() extends SimpleExternalContext {

  this.request  = new CommandLineRequest
  this.response = new CommandLineResponse

  private class CommandLineRequest extends RequestImpl {
    override def getContextPath   = ""
    override def getRequestPath   = "/"
    override def getContainerType = "command-line"
  }

  private class CommandLineResponse extends ResponseImpl {

    override lazy val getWriter: PrintWriter =
      new PrintWriter(new OutputStreamWriter(getOutputStream))

    override lazy val getOutputStream: OutputStream =
      new FilterOutputStream(System.out) {
        override def close(): Unit = {
          // Don't close System.out
          System.out.flush()
        }
      }

    override def rewriteActionURL(urlString: String) =
      rewriteResourceURL(urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)

    override def rewriteRenderURL(urlString: String) =
      rewriteResourceURL(urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)

    override def rewriteActionURL(urlString: String, portletMode: String, windowState: String) =
      rewriteResourceURL(urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)

    override def rewriteRenderURL(urlString: String, portletMode: String, windowState: String) =
      rewriteResourceURL(urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)

    override def rewriteResourceURL(urlString: String) =
      rewriteResourceURL(urlString, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)

    override def rewriteResourceURL(urlString: String, generateAbsoluteURL: Boolean) =
      rewriteResourceURL(
        urlString,
        if (generateAbsoluteURL)
          URLRewriter.REWRITE_MODE_ABSOLUTE
        else
          URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE
      )

    override def rewriteResourceURL(urlString: String, rewriteMode: Int) =
      URLRewriterUtils.rewriteURL(getRequest, urlString, rewriteMode)
  }

}