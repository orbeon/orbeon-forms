package org.orbeon.oxf.fr.persistence.relational

import cats.Eval
import org.orbeon.oxf.fr.persistence.relational.index.Index.matchForControl
import org.orbeon.oxf.fr.{AppForm, FormRunnerCommon}
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._


trait RequestCommon {
  val provider : Provider
  val appForm  : AppForm
  val version  : Version
}

sealed trait StageHeader
object StageHeader {

  case object Unspecified                extends StageHeader
  case class  Specific    (name: String) extends StageHeader

  val HeaderName      = "Orbeon-Workflow-Stage"
  val HeaderNameLower = HeaderName.toLowerCase
}

case class EncryptionAndIndexDetails(
  encryptedFields: Eval[List[FormRunnerCommon.ControlBindPathHoldersResources]],
  indexedFields  : Eval[List[IndexedControl]]
)

sealed trait WhatToReindex
object WhatToReindex {
  case object  AllData                                     extends WhatToReindex
  case class   DataForDocumentId(documentId: String)       extends WhatToReindex
  case class   DataForForm(appForm: AppForm, version: Int) extends WhatToReindex
}

case class IndexedControl(
    name      : String,
    inSearch  : Boolean,
    inSummary : Boolean,
    xpath     : String,
    xsType    : String,
    control   : String,
    htmlLabel : Boolean,
    resources : List[(String, NodeInfo)]
  ) {
    def toXML: NodeInfo =
      <query
        name={name}
        path={xpath}
        type={xsType}
        control={control}
        search-field={inSearch.toString}
        summary-field={inSummary.toString}
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