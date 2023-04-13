package org.orbeon.oxf.fr.persistence.relational


sealed trait Version

// xxx move to parent package in separate commit
object Version {

  case object Unspecified                                       extends Version
  case object Next                                              extends Version
  case class  Specific   (version: Int)                         extends Version
  case class  ForDocument(documentId: String, isDraft: Boolean) extends Version

  val OrbeonForDocumentId              : String = "Orbeon-For-Document-Id"
  val OrbeonForDocumentIsDraft         : String = "Orbeon-For-Document-IsDraft"
  val OrbeonFormDefinitionVersion      : String = "Orbeon-Form-Definition-Version"

  val OrbeonFormDefinitionVersionLower : String = OrbeonFormDefinitionVersion.toLowerCase // only used by tests

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