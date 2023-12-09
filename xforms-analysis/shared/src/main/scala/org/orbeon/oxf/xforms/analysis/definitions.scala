package org.orbeon.oxf.xforms.analysis


sealed trait XPathErrorDetails
object XPathErrorDetails {
  case class ForBindMip          (bindNameOpt: Option[String], mipName: String) extends XPathErrorDetails
  case class ForBindMipReferences(bindNameOpt: Option[String], mipName: String, references: Set[String]) extends XPathErrorDetails
  case class ForAnalysis         () extends XPathErrorDetails
}
