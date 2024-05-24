package org.orbeon.oxf.fr.persistence


sealed trait SearchVersion

object SearchVersion {

  case object Unspecified               extends SearchVersion
  case object All                       extends SearchVersion
  case class  Specific   (version: Int) extends SearchVersion

  def apply(version: Option[String]): SearchVersion =
    version match {
      case None         => Unspecified
      case Some("all")  => All
      case Some(v)      => Specific(v.toInt)
    }

  def toHeaderString(sv: SearchVersion): Option[String] = sv match {
    case Unspecified => None
    case All         => Some("all")
    case Specific(v) => Some(v.toString)
  }
}
