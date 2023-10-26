package org.orbeon.oxf.fr.persistence.relational

import cats.Eval
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.datamigration.PathElem
import org.orbeon.oxf.fr.persistence.relational.index.Index.matchForControl
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._


sealed trait StageHeader
object StageHeader {

  case object Unspecified                extends StageHeader
  case class  Specific    (name: String) extends StageHeader

  val HeaderName      = "Orbeon-Workflow-Stage"
  val HeaderNameLower = HeaderName.toLowerCase
}

// Only keep the information needed, also avoiding to point to underlying `NodeInfo`
case class EncryptionAndIndexDetails(
  encryptedControlsPaths: Eval[List[List[PathElem]]],
  indexedControlsXPaths : Eval[List[String]]
)

sealed trait WhatToReindex
object WhatToReindex {
  case object AllData                                     extends WhatToReindex
  case class  DataForDocumentId(documentId: String)       extends WhatToReindex
  case class  DataForForm(appForm: AppForm, version: Int) extends WhatToReindex
}

case class SummarySettings(
  show   : Boolean,
  search : Boolean,
  edit   : Boolean
)

case class IndexedControl(
    name               : String,
    xpath              : String,
    xsType             : String,
    control            : String,
    summarySettings    : SummarySettings,
    staticallyRequired : Boolean,
    htmlLabel          : Boolean,
    resources          : List[(String, NodeInfo)]
  ) {
    def toXML: NodeInfo =
      <query
        name={name}
        path={xpath}
        type={xsType}
        control={control}
        summary-show={summarySettings.show.toString}
        summary-search={summarySettings.search.toString}
        summary-edit={summarySettings.edit.toString}
        statically-required={staticallyRequired.toString}
        match={matchForControl(control)}
        html-label={htmlLabel.toString}>{
        for ((lang, resourceHolder) <- resources)
          yield
            <resources lang={lang}>{
              val labelElemOpt =
                resourceHolder elemValueOpt "label" map { label =>
                  <label>{if (htmlLabel) label else label.escapeXmlMinimal}</label>
                }

              val itemElems = resourceHolder child "item" map { item =>
                <item>{
                  <label>{item elemValue "label"}</label>
                  <value>{item elemValue "value"}</value>
                }</item>
              }

              labelElemOpt.toList ++ itemElems
            }</resources>
      }</query>
  }