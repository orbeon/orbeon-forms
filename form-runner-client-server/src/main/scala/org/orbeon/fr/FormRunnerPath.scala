package org.orbeon.fr

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.PathUtils.*


object FormRunnerPath {

  def formRunnerPath(app: String, form: String, mode: String, documentId: Option[String], query: Option[String]): String =
    appendQueryString(s"/fr/$app/$form/$mode${documentId.map(_.prependSlash).getOrElse("")}", query.getOrElse(""))

  def formRunnerHomePath(query: Option[String]): String =
    appendQueryString("/fr/", query.getOrElse(""))

  def formRunnerURL(baseURL: String, path: String, embeddable: Boolean): String =
    appendQueryString(baseURL.dropTrailingSlash + path, if (embeddable) s"${ExternalContext.EmbeddableParam}=true" else "")

  def formRunnerPath(
    app       : String,
    form      : String,
    mode      : String,
    documentId: Option[String]                 = None,
    query     : IterableOnce[(String, String)] = Nil,
    background: Boolean                        = false
  ): String =
    recombineQuery(
      s"/fr/${if (background) "service/" else ""}$app/$form/$mode${documentId.map(_.prependSlash).getOrElse("")}",
      query
     )
}
