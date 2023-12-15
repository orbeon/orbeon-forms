package org.orbeon.xbl

import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.util.StaticXPath.ValueRepresentationType
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.analysis.XPathErrorDetails
import org.orbeon.oxf.xforms.event.events.XXFormsXPathErrorEvent
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.value.{AtomicValue, SequenceExtent}
import org.orbeon.scaxon.Implicits._



object ConsoleSupport {

  //@XPathFunction
  def tryToUnwrapExpression(expression: String): String =
    StaticXPath.tryToUnwrapExpression(expression)

  //@XPathFunction
  def getStaticXPathErrors: Item = {

    val rows =
      inScopeContainingDocument
        .staticState
        .topLevelPart
        .getStaticXPathErrors
        .map { case (expression, throwableOpt, details) =>

          val error =
            throwableOpt match {
              case Some(throwable) => throwable.getMessage
              case None            => "Unknown error"
            }

          mapForDetails(expression, error, details)
      }

    SaxonUtils.newArrayItem(rows.toVector)
  }

  //@XPathFunction
  def getCurrentEventErrorDetails(): Item =
    inScopeContainingDocument.currentEventOpt.flatMap {
      case event: XXFormsXPathErrorEvent =>
        event.detailsOpt.map(
          mapForDetails(
            event.expressionOpt.getOrElse(""),
            event.messageOpt.getOrElse(""),
            _
          )
        )
      case _ =>
        None
    } .orNull

  private def mapForDetails(
    expression: String,
    error     : String,
    details   : XPathErrorDetails
  ): Item = {

    val bindNameOpt =
      details match {
        case XPathErrorDetails.ForBindMip(bindNameOpt, _)              => bindNameOpt
        case XPathErrorDetails.ForBindMipReferences(bindNameOpt, _, _) => bindNameOpt
        case _                                                         => None
      }

    val contextName =
      details match {
        case XPathErrorDetails.ForBindMip(_, mipName)              => mipName
        case XPathErrorDetails.ForBindMipReferences(_, mipName, _) => mipName
        case XPathErrorDetails.ForAnalysis()                       => "analysis"
        case XPathErrorDetails.ForAttribute(_)                     => "attribute"
        case XPathErrorDetails.ForVariable(_)                      => "variable"
        case XPathErrorDetails.ForOther(_)                         => "other"
      }

    val references =
      details match {
        case XPathErrorDetails.ForBindMipReferences(_, _, r) => r
        case _                                               => Set.empty[String]
      }

    SaxonUtils.newMapItem(
      Map[AtomicValue, ValueRepresentationType](
        (SaxonUtils.fixStringValue("expression"),   expression),
        (SaxonUtils.fixStringValue("error"),        error),
        (SaxonUtils.fixStringValue("bind-name"),    bindNameOpt.getOrElse(""): String),
        (SaxonUtils.fixStringValue("context-name"), contextName),
        (SaxonUtils.fixStringValue("references"),   new SequenceExtent(references.toList))
      )
    )
  }
}
