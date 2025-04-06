package org.orbeon.oxf.fb

import org.apache.pdfbox.pdmodel.PDDocument
import org.orbeon.connection.ConnectionResult
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.fr.FormDefinitionVersion
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.util.Logging.{debug, error}
import org.orbeon.saxon.om
import org.orbeon.scaxon.NodeConversions.*

import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success}


trait PdfOps {

  import FormBuilder.logger
  implicit def coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport

  def findPdfFields(pathOrTmpFileUri: String): om.NodeInfo = {

    val servicePathQuery = pathOrTmpFileUri
    val formVersionOpt   = Some(1) // Form Builder

    // TODO: code duplication with `processAttachment()`
    val connectionResult = PersistenceApi.connectPersistence(
      method         = HttpMethod.GET,
      pathQuery      = servicePathQuery,
      formVersionOpt = formVersionOpt.map(v => Left(FormDefinitionVersion.Specific(v)))
    )

    val rootElem =
      ConnectionResult.tryWithSuccessConnection(
        connectionResult,
        closeOnSuccess = true
      ) { is =>
        <_>{
          useAndClose(PDDocument.load(is)) { pdd =>
            Option(pdd.getDocumentCatalog.getAcroForm) match {
              case Some(acroForm) =>
                acroForm.getFieldIterator.asScala.map { field =>
                  <field
                    name={field.getPartialName}
                    fully-qualified-name={field.getFullyQualifiedName}
                    alternate-field-name={field.getAlternateFieldName}/>
                }
              case None =>
            }
          }
        }</_>
      } match {
        case Success(v) =>
          debug(s"success retrieving attachment when calling `findPdfFields($servicePathQuery)`")
          v
        case Failure(_) =>
          error(s"failure retrieving attachment when calling `findPdfFields($servicePathQuery)`")
          <_/>
      }

    elemToNodeInfo(rootElem)
  }
}
