package org.orbeon.oxf.fb.xbl

import io.circe.{Decoder, Encoder}
import org.orbeon.date.{IsoDate, DateFormat}
import org.orbeon.oxf.fr.ui.ScalaToXml
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}


object DateFormatUi extends ScalaToXml {

  type MyState = DateFormat

  import io.circe.generic.auto._

  val encoder: Encoder[DateFormat] = implicitly
  val decoder: Decoder[DateFormat] = implicitly

  //@XPathFunction
  def dateFormatToXml(dateFormat: String): DocumentInfo =
    fullXmlToSimplifiedXml(stateToFullXml(IsoDate.parseFormat(dateFormat)))

  //@XPathFunction
  def xmlFormatToFormatString(dateFormatRootElem: NodeInfo): String =
    simplifiedXmlToState(dateFormatRootElem).map(IsoDate.generateFormat).toOption.orNull

  //@XPathFunction
  def formatDateWithFormat(dateFormatRootElem: NodeInfo, isoDate: String): String =
    simplifiedXmlToState(dateFormatRootElem)
      .toOption
      .flatMap(f => IsoDate.parseIsoDate(isoDate).map(IsoDate.formatDate(_, f)))
      .orNull
}
