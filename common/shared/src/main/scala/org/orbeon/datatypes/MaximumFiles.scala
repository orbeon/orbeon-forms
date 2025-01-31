package org.orbeon.datatypes

import org.orbeon.oxf.util.StringUtils.OrbeonStringOps

sealed trait MaximumFiles

object MaximumFiles {

  case class  LimitedFiles   (count: Int) extends MaximumFiles
  case object UnlimitedFiles              extends MaximumFiles

  // Return `None` if blank, not a long number, or lower than -1
  def unapply(s: String): Option[MaximumFiles] =
    s.trimAllToOpt flatMap (_.toIntOption match {
      case Some(i) if i >= 0  => Some(LimitedFiles(i))
      case Some(i) if i == -1 => Some(UnlimitedFiles)
      case _                  => None
    })

}