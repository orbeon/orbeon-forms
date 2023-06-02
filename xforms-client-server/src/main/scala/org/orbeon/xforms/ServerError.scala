package org.orbeon.xforms

import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.util.StringUtils._
import scalatags.Text.all._

import scala.collection.compat.IterableOnce


// Represent a non-fatal server XForms error
case class ServerError(
  message  : String,
  fileOpt  : Option[String],
  lineOpt  : Option[Int],
  colOpt   : Option[Int],
  classOpt : Option[String]
)

object ServerError {

  import Private._

  def apply(message: String, location : Option[LocationData], classOpt : Option[String] = None): ServerError =
    ServerError(
      message.trimAllToEmpty,
      location flatMap (l => Option(l.file)),
      location map     (_.line) filter (_ >= 0),
      location map     (_.col)  filter (_ >= 0),
      classOpt
    )

  def getDetailsAsList(error: ServerError): List[(String, String)] =
    collectTuples(error, attributes)

  def errorsAsHtmlString(errors: IterableOnce[ServerError]): String =
    ul {
      errors.toList.map { error =>
        li(
          span(error.message), {
            val tuples = collectTuples(error, description)
            if (tuples.nonEmpty)
              ul {
                tuples.map { case (k, v) =>
                  li(s"$k: $v")
                }
              }
          }
        )
      }
    } .toString

  private object Private {

    val attributes  = List("file", "line", "column", "exception")
    val description = List("in",   "line", "column", "class")

    def collectTuples(error: ServerError, names: List[String]): List[(String, String)] =
      names zip
        List(error.fileOpt, error.lineOpt, error.colOpt, error.classOpt) collect
        { case (k, Some(v)) => k -> v.toString }
  }
}