package org.orbeon.datatypes


sealed trait MaximumCurrentFiles

object MaximumCurrentFiles {

  case class  LimitedFiles   (current: Int, max: Int) extends MaximumCurrentFiles
  case object UnlimitedFiles                          extends MaximumCurrentFiles

}