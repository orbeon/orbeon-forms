package org.orbeon.oxf.fr

import org.log4s
import org.orbeon.dom.QName
import org.orbeon.oxf.fr.permission.Operations
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.fr.process.{FormRunnerActionsCommon, FormRunnerExternalMode, FormRunnerRenderedFormat}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, DateUtils, IndentedLogger, SecureUtils}
import org.orbeon.oxf.xforms.NodeInfoFactory.attributeInfo
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath.*


trait FormRunnerPlatformJVM extends FormRunnerPlatform {
  def configCheck(): Set[(String, log4s.LogLevel)] = {

    implicit val logger     : IndentedLogger = RelationalUtils.newIndentedLogger
    implicit val propertySet: PropertySet    = CoreCrossPlatformSupport.properties

    val passwordGeneral         = (! SecureUtils.checkPasswordForKeyUsage(SecureUtils.KeyUsage.General)        ).set("password.general",          log4s.Error)
    val passwordToken           = (! SecureUtils.checkPasswordForKeyUsage(SecureUtils.KeyUsage.Token)          ).set("password.token",            log4s.Info)
    val passwordFieldEncryption = (! SecureUtils.checkPasswordForKeyUsage(SecureUtils.KeyUsage.FieldEncryption)).set("password.field-encryption", log4s.Info)
    val databaseConfiguration   = (! RelationalUtils.databaseConfigurationPresent()                            ).set("database.configuration",    log4s.Error)

    passwordGeneral ++ passwordToken ++ passwordFieldEncryption ++ databaseConfiguration
  }

  private val CreatedLowerQName      = QName(Headers.CreatedLower)
  private val LastModifiedLowerQName = QName(Headers.LastModifiedLower)
  private val ETagLowerQName         = QName(Headers.ETagLower)

  //@XPathFunction
  def updateDocumentMetadata(
    encryptedPrivateModeMetadataOrNull: String,
    persistenceInstanceElem           : om.NodeInfo,
    documentMetadataElem              : om.NodeInfo
  ): Unit =
    encryptedPrivateModeMetadataOrNull
      .trimAllToOpt
      .flatMap(FormRunnerExternalMode.decryptPrivateModeMetadata(_).toOption).foreach {
        privateModeMetadata =>

          XFormsAPI.setvalue(
            ref   = List(persistenceInstanceElem) / "initial-data-status",
            value = privateModeMetadata.dataStatus.entryName
          )

          val documentMetadataElemAsList = List(documentMetadataElem)

          // 2025-10-24: Reproducing the logic we had in `persistence-model.xml`. It's unclear why we set the value
          // of `workflow-stage`, but we delete and create the other attributes. Shouldn't this be done consistently?
          XFormsAPI.setvalue(
            ref   = documentMetadataElemAsList,
            value = privateModeMetadata.workflowStage.getOrElse("")
          )

          XFormsAPI.delete(
            ref = documentMetadataElemAsList /@ @* filter (_.name != Names.WorkflowStage)
          )

          XFormsAPI.insert(
            into    = documentMetadataElemAsList,
            origin  =
              List(
                privateModeMetadata.created     .map(DateUtils.formatRfc1123DateTimeGmt).map(attributeInfo(CreatedLowerQName,      _)),
                privateModeMetadata.lastModified.map(DateUtils.formatRfc1123DateTimeGmt).map(attributeInfo(LastModifiedLowerQName, _)),
                privateModeMetadata.eTag.map(attributeInfo(ETagLowerQName, _))
              ).flatten
          )

        FormRunnerActionsCommon.findUrlsInstanceRootElem.foreach { urlsInstanceRootElem =>
          privateModeMetadata.renderedFormats.foreach { case (key, uri) =>
            FormRunnerRenderedFormat.updateOrCreateRenderedFormatPathElem(urlsInstanceRootElem, key, uri)
          }
        }
      }

  protected def decryptPrivateModeOperations(encryptedPrivateModeMetadataOpt: Option[String]): Option[Operations] =
    encryptedPrivateModeMetadataOpt
      .flatMap(FormRunnerExternalMode.decryptPrivateModeMetadata(_).toOption)
      .flatMap(_.authorizedOperations)
}
