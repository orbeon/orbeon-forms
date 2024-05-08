package org.orbeon.io

import org.orbeon.oxf.util.PathUtils

import java.net.URI


object UriUtils {

  def removeQueryAndFragment(uri: URI): URI =
    if (uri.isOpaque)
      new URI(uri.getScheme, PathUtils.splitQuery(uri.getSchemeSpecificPart)._1, null)
    else
      new URI(uri.getScheme, uri.getAuthority, uri.getPath, null)
}
