package org.orbeon.oxf.fb.xbl

import io.circe.{Decoder, Encoder}
import org.orbeon.date.{IsoTime, TimeFormat}
import org.orbeon.oxf.fr.ui.ScalaToXml
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}


object TimeFormatUi extends ScalaToXml {

  type MyState = TimeFormat

  import io.circe.generic.auto._

  val encoder: Encoder[TimeFormat] = implicitly
  val decoder: Decoder[TimeFormat] = implicitly

  //@XPathFunction
  def timeFormatToXml(timeFormat: String): DocumentInfo =
    fullXmlToSimplifiedXml(stateToFullXml(IsoTime.parseFormat(timeFormat)))

  //@XPathFunction
  def xmlFormatToFormatString(timeFormatRootElem: NodeInfo): String =
    simplifiedXmlToState(timeFormatRootElem).map(IsoTime.generateFormat).toOption.orNull
}
