package org.orbeon.fr

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.util.PathUtils
import org.orbeon.oxf.util.PathUtils.PathOps


object FormRunnerPath {

  def formRunnerPath(app: String, form: String, mode: String, documentId: Option[String], query: Option[String]): String =
    PathUtils.appendQueryString(s"/fr/$app/$form/$mode${documentId.map("/" +).getOrElse("")}", query.getOrElse(""))

  def formRunnerHomePath(query: Option[String]): String =
    PathUtils.appendQueryString("/fr/", query.getOrElse(""))

  def formRunnerURL(baseURL: String, path: String, embeddable: Boolean): String =
    PathUtils.appendQueryString(baseURL.dropTrailingSlash + path, if (embeddable) s"${ExternalContext.EmbeddableParam}=true" else "")
}
