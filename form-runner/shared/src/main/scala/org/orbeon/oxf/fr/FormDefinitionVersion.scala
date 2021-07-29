package org.orbeon.oxf.fr

sealed trait                                  FormDefinitionVersion
object                                        FormDefinitionVersion {
  case object Latest                  extends FormDefinitionVersion
  case class  Specific (version: Int) extends FormDefinitionVersion
}