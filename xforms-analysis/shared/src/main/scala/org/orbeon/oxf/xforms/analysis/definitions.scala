package org.orbeon.oxf.xforms.analysis


sealed trait XPathErrorDetails
object XPathErrorDetails {
  case class ForBindMip          (bindNameOpt: Option[String], mipName: String)                          extends XPathErrorDetails
  case class ForBindMipReferences(bindNameOpt: Option[String], mipName: String, references: Set[String]) extends XPathErrorDetails
  case class ForAnalysis         ()                                                                      extends XPathErrorDetails
  case class ForAttribute        (attName: String)                                                       extends XPathErrorDetails
  case class ForVariable         (varName: String)                                                       extends XPathErrorDetails
  case class ForOther            (message: String)                                                       extends XPathErrorDetails

  def message(detail: XPathErrorDetails): String = detail match {
    case ForBindMip          (Some(bindName), mipName)       => s"evaluating MIP `$mipName` for bind with name `$bindName`"
    case ForBindMipReferences(Some(bindName), mipName, refs) => s"missing variable reference(s) ${refs.mkString("`$", "`, `$", "`")} evaluating MIP `$mipName` for bind with name `$bindName`"
    case ForBindMip          (None,           mipName)       => s"evaluating MIP `$mipName` for bind" // TODO: should have other identifying info
    case ForBindMipReferences(None,           mipName, refs) => s"missing variable reference(s) ${refs.mkString("`$", "`, `$", "`")} evaluating MIP `$mipName` for bind" // TODO: should have other identifying info
    case ForAnalysis         ()                              => "during analysis"
    case ForAttribute        (attName)                       => s"evaluating attribute `$attName`"
    case ForVariable         (varName)                       => s"evaluating variable `$varName`"
    case ForOther            (message)                       => message
  }
}
