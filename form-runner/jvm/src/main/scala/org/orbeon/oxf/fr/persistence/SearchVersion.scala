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
}
