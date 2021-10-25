package org.orbeon.oxf.fr.persistence.proxy

import cats.syntax.option._
import org.orbeon.dom.saxon.{DocumentWrapper, NodeWrapper}
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.externalcontext.{ExternalContext, URLRewriter}
import org.orbeon.oxf.fr.FormRunnerPersistence.findFormDefinitionFormatFromStringVersions
import org.orbeon.oxf.fr.XMLNames.{XBLBindingTest, XBLXBLTest}
import org.orbeon.oxf.fr.datamigration.MigrationSupport
import org.orbeon.oxf.fr.datamigration.MigrationSupport.MigrationsFromForm
import org.orbeon.oxf.fr.{AppForm, DataFormatVersion, FormRunnerDocContext, FormRunnerTemplatesOps}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{IndentedLogger, URLRewriterUtils, XPath}
import org.orbeon.oxf.xforms.submission.SubmissionUtils
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
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
        resolvedAbsoluteUrl = new URI(
          URLRewriterUtils.rewriteServiceURL(
            externalContext.getRequest,
            s"/fr/service/custom/orbeon/builder/toolbox?application=${appForm.app}&form=${appForm.form}&orbeon-library-version=$orbeonLibraryVersion&app-library-version=$appLibraryVersion",
            URLRewriter.REWRITE_MODE_ABSOLUTE
          )
        ),
        handleXInclude = false
      )
    }

  def migrateFormDefinition(
    dstVersion      : DataFormatVersion,
    app             : String,
    form            : String)(implicit
    logger          : IndentedLogger,
    externalContext : ExternalContext
  ): (InputStream, OutputStream) => Unit = {

      // We are explicitly asked to downgrade a form definition format
      // The database may contain a form definition in any format

      def migrate(is: InputStream, os: OutputStream): Unit = {

        // Read document and component bindings
        val formDoc =
          new DocumentWrapper(TransformerUtils.readOrbeonDom(is, null, false, false), null, XPath.GlobalConfiguration)

        val frDocCtx: FormRunnerDocContext = new FormRunnerDocContext {
          val formDefinitionRootElem: NodeInfo = formDoc.rootElement
        }

        val componentBindings = loadComponentBindings(AppForm(app, form), frDocCtx)

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

        MigrationSupport.migrateDataInPlace(
          dataRootElem     = frDocCtx.dataRootElem.asInstanceOf[NodeWrapper],
          srcVersion       = srcVersionFromMetadataOrGuess,
          dstVersion       = dstVersion,
          findMigrationSet = migrationsFromForm
        )

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

      migrate
    }
}
