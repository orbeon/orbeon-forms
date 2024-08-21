package org.orbeon.oxf.fr


sealed trait Version

object Version {

  case object Unspecified                                       extends Version
  case object Next                                              extends Version
  case class  Specific   (version: Int)                         extends Version
  case class  ForDocument(documentId: String, isDraft: Boolean) extends Version

  val OrbeonForDocumentId              : String = "Orbeon-For-Document-Id"
  val OrbeonForDocumentIsDraft         : String = "Orbeon-For-Document-IsDraft"
  val OrbeonFormDefinitionVersion      : String = "Orbeon-Form-Definition-Version"

  val AllVersionHeadersLower: Set[String] =
    Set(OrbeonForDocumentId, OrbeonForDocumentIsDraft, OrbeonFormDefinitionVersion).map(_.toLowerCase())

  def apply(documentId: Option[String], isDraft: Option[String], version: Option[String]): Version =
    documentId match {
      case Some(id) => ForDocument(id, isDraft.exists(_.toBoolean))
      case None     =>
        version match {
          case None         => Unspecified
          case Some("next") => Next
          case Some(v)      => Specific(v.toInt)
        }
    }
}


sealed trait                                  FormDefinitionVersion

object                                        FormDefinitionVersion {
  case object Latest                  extends FormDefinitionVersion { val string = "latest" }
  case class  Specific (version: Int) extends FormDefinitionVersion
}


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
