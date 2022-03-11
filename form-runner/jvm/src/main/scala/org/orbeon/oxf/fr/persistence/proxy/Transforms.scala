package org.orbeon.oxf.fr.persistence.proxy

import cats.syntax.option._
import org.orbeon.dom.saxon.{DocumentWrapper, NodeWrapper}
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.externalcontext.{ExternalContext, URLRewriter, UrlRewriteMode}
import org.orbeon.oxf.fr.FormRunnerPersistence.findFormDefinitionFormatFromStringVersions
import org.orbeon.oxf.fr.XMLNames.{XBLBindingTest, XBLXBLTest}
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.datamigration.MigrationSupport
import org.orbeon.oxf.fr.datamigration.MigrationSupport.MigrationsFromForm
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{IndentedLogger, URLRewriterUtils, XPath}
import org.orbeon.oxf.xforms.model.BasicIdIndex
import org.orbeon.oxf.xforms.submission.SubmissionUtils
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.scaxon.SimplePath._

import java.io.{InputStream, OutputStream}
import java.net.URI
import javax.xml.transform.OutputKeys
import javax.xml.transform.stream.StreamResult


object Transforms {

  // TODO: Move this.
  def loadComponentBindings(
    appForm         : AppForm,
    frDocCtx        : FormRunnerDocContext)(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): DocumentInfo = {

      val orbeonLibraryVersion = (frDocCtx.metadataRootElem / "library-versions" / "orbeon").stringValue.trimAllToOpt.getOrElse(1)
      val appLibraryVersion    = (frDocCtx.metadataRootElem / "library-versions" / "app").stringValue.trimAllToOpt.getOrElse(1)

      SubmissionUtils.readTinyTree(
        headersGetter       = _ => None, // Q: Do we need any header forwarding here?
        resolvedAbsoluteUrl = URI.create(
          URLRewriterUtils.rewriteServiceURL(
            externalContext.getRequest,
            s"/fr/service/custom/orbeon/builder/toolbox?application=${appForm.app}&form=${appForm.form}&orbeon-library-version=$orbeonLibraryVersion&app-library-version=$appLibraryVersion",
            UrlRewriteMode.Absolute
          )
        ),
        handleXInclude = false
      )._1
    }

  def readFormData(
    appForm         : AppForm,
    documentId      : String)(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): (DocumentInfo, Map[String, List[String]]) =
    SubmissionUtils.readTinyTree(
      headersGetter       = _ => None, // Q: Do we need any header forwarding here?
      resolvedAbsoluteUrl = URI.create(
        URLRewriterUtils.rewriteServiceURL(
          externalContext.getRequest,
          s"/fr/service/persistence/crud/${appForm.app}/${appForm.form}/data/$documentId/data.xml",
          UrlRewriteMode.Absolute
        )
      ),
      handleXInclude = false
    )

  def migrateFormDefinition(
    dstVersion      : DataFormatVersion,
    app             : String,
    form            : String)(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): (InputStream, OutputStream) => Unit = {

    val appForm = AppForm(app, form)

    // We are explicitly asked to downgrade a form definition format
    // The database may contain a form definition in any format

    (is, os) => {

      // Read document and component bindings
      val formDoc =
        new DocumentWrapper(TransformerUtils.readOrbeonDom(is, null, false, false), null, XPath.GlobalConfiguration)

      val frDocCtx: FormRunnerDocContext = new InDocFormRunnerDocContext(formDoc)

      // Set an id index to help with migration performance
      // The index is not updated with `insert` and `delete`, but we convinced ourselves that it's ok because:
      //
      // - We only support migrating "down" right now, from 2019.1.0 to 4.8.0.
      // - Inline data: migration is not affected by id changes.
      // - Binds: elements are moved down one level.
      // - Controls: `bind` attributes are removed and that's it.
      // - Templates: search for controls.
      object Index extends BasicIdIndex {
        def documentInfo: DocumentNodeInfoType = formDoc
      }

      formDoc.setIdGetter(Index.idGetter(formDoc))

      val componentBindings = loadComponentBindings(appForm, frDocCtx)

      val migrationsFromForm =
        new MigrationsFromForm(
          outerDocument        = formDoc,
          availableXBLBindings = componentBindings.some,
          legacyGridsOnly      = false
        )

      // 1. All grids must have ids for what follows
      // This is already the case, since the form definition was created with a version of
      // Orbeon Forms which places ids everywhere.

      // 2. Migrate inline instance data

      // If we don't find a version in the form definition, it means it was last updated with a version older than 2018.2
      // TODO: We should discriminate between 4.8.0 and 4.0.0 ideally. Currently we don't have a user use case but it would
      //   be good for correctness.
      val srcVersionFromMetadataOrGuess =
        findFormDefinitionFormatFromStringVersions(
          (frDocCtx.metadataRootElem / "updated-with-version" ++ frDocCtx.metadataRootElem / "created-with-version") map
            (_.stringValue)
        ) getOrElse DataFormatVersion.V480

      val dataRootElem =
        frDocCtx.dataRootElem.asInstanceOf[NodeWrapper]

      MigrationSupport.migrateDataInPlace(
        dataRootElem     = dataRootElem,
        srcVersion       = srcVersionFromMetadataOrGuess,
        dstVersion       = dstVersion,
        findMigrationSet = migrationsFromForm
      )

      GridDataMigration.updateDataFormatVersionInPlace(appForm, dstVersion, dataRootElem)

      // 3. Migrate other aspects such as binds and controls
      MigrationSupport.migrateOtherInPlace(
        formRootElem     = formDoc,
        srcVersion       = srcVersionFromMetadataOrGuess,
        dstVersion       = dstVersion,
        findMigrationSet = migrationsFromForm
      )

      // 4. Migrate templates
      FormRunnerTemplatesOps.updateTemplates(
        None,
        componentBindings.rootElement / XBLXBLTest / XBLBindingTest)(
        frDocCtx
      )

      // Serialize out the result
      val receiver =
        TransformerUtils.getIdentityTransformerHandler |!>
          (_.setResult(new StreamResult(os)))

      receiver.getTransformer |!>
        (_.setOutputProperty(OutputKeys.ENCODING,                     CharsetNames.Utf8)) |!>
        (_.setOutputProperty(OutputKeys.METHOD,                       "xml"))             |!>
        (_.setOutputProperty(OutputKeys.VERSION,                      "1.0"))             |!>
        (_.setOutputProperty(OutputKeys.INDENT,                       "no"))              |!>
        (_.setOutputProperty(TransformerUtils.INDENT_AMOUNT_PROPERTY, "0"))

      TransformerUtils.writeTinyTree(formDoc, receiver)
    }
  }
}
