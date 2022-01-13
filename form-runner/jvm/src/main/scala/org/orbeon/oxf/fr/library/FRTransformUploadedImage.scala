package org.orbeon.oxf.fr.library

import net.coobird.thumbnailator.Thumbnails
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{Connection, ConnectionResult, CoreCrossPlatformSupport, IndentedLogger, Mediatypes, NetUtils, PathUtils}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xml.{FunctionSupport, RuntimeDependentFunction}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.saxon.om.EmptyIterator
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import shapeless.syntax.typeable.typeableOps

import java.net.URI


class FRTransformUploadedImage extends FunctionSupport with RuntimeDependentFunction {

  override def iterate(context: XPathContext): om.SequenceIterator = {

    implicit val xpathContext: XPathContext    = context
    implicit val ec          : ExternalContext = CoreCrossPlatformSupport.externalContext
    implicit val logger      : IndentedLogger  = XFormsAPI.inScopeContainingDocument.controls.indentedLogger

    val binding = itemArgument(0).narrowTo[om.NodeInfo].getOrElse(throw new IllegalArgumentException("not a node"))

    val bindingValue = binding.getStringValue
    val maxWidthOpt  = stringArgumentOpt(1).flatMap(_.trimAllToOpt).map(_.toInt)
    val maxHeightOpt = stringArgumentOpt(2).flatMap(_.trimAllToOpt).map(_.toInt)
    val qualityOpt   = stringArgumentOpt(3).flatMap(_.trimAllToOpt).map(_.toFloat / 100.0)
    val formatOpt    = stringArgumentOpt(4).flatMap(_.trimAllToOpt)

    bindingValue.trimAllToOpt foreach { uriString =>

      // For now we limit the ability to transform to a just-uploaded temporary file
      require(PathUtils.getProtocol(uriString) == "file")
      require(XFormsUploadControl.verifyMAC(uriString))

      def connectGet: ConnectionResult =
        Connection.connectNow(
          method          = GET,
          url             = new URI(uriString),
          credentials     = None,
          content         = None,
          headers         = Map.empty,
          loadState       = false,
          saveState       = false,
          logBody         = false
        )

      ConnectionResult.tryWithSuccessConnection(connectGet, closeOnSuccess = true) { is =>

        var b = Thumbnails.of(is)
        maxWidthOpt foreach { maxWidth =>
          b = b.width(maxWidth)
        }
        maxHeightOpt foreach { maxHeight =>
          b = b.height(maxHeight)
        }
        qualityOpt foreach { quality =>
          b = b.outputQuality(quality)
        }
        formatOpt foreach { format =>
          b = b.outputFormat(format)
        }

        val fileItem = NetUtils.prepareFileItem(NetUtils.SESSION_SCOPE, logger.logger.logger)

        useAndClose(fileItem.getOutputStream) { os =>
          b.toOutputStream(os)
        }

        val resultingSize =
          fileItem.getSize

        val mediatypeOpt =
          formatOpt flatMap Mediatypes.findMediatypeForExtension orElse // use extension/mediatype logic shortcut
            (binding attValueOpt "mediatype")

        val fileItemUrlString =
          RequestGenerator.urlForFileItemCreateIfNeeded(fileItem, NetUtils.SESSION_SCOPE)

        XFormsUploadControl.updateExternalValueAndMetadata(
          binding,
          fileItemUrlString,
          binding attValueOpt "filename",
          mediatypeOpt,
          resultingSize
        )
      }
    }

    EmptyIterator.getInstance
  }
}
