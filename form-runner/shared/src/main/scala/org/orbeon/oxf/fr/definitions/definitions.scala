package org.orbeon.oxf.fr.definitions

import org.orbeon.oxf.fr.permission.Operations


sealed trait ModeType

object ModeType {

  case object Creation extends ModeType

  case object Edition extends ModeType

  case object Readonly extends ModeType

  // NOTE: `tiff` and `test-pdf` are reduced to `pdf` at the XForms level, but not at the XSLT level. We don't
  // yet expose this to XSLT, but we might in the future, so check on those modes as well.
  // 2021-12-22: `schema` could be a readonly mode, but we consider this special as it is protected as a service.
  val CreationModes = Set("new", "import", "validate", "test")
  val EditionModes = Set("edit")
  val ReadonlyModes = Set("view", "pdf", "email", "controls", "tiff", "test-pdf", "export", "excel-export", "schema") // `excel-export` is legacy

  def unapply(modeString: String): Option[ModeType] =
    if (CreationModes(modeString))
      Some(Creation)
    else if (EditionModes(modeString))
      Some(Edition)
    else if (ReadonlyModes(modeString))
      Some(Readonly)
    else
      None
}

sealed trait ModeTypeAndOps {
  val modeType: ModeType
}

object ModeTypeAndOps {
  case object Creation extends ModeTypeAndOps {
    val modeType: ModeType = ModeType.Creation
  }

  case class Other(modeType: ModeType, ops: Operations) extends ModeTypeAndOps
}
