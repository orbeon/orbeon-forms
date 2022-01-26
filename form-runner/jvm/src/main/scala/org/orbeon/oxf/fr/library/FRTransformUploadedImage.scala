package org.orbeon.oxf.fr.library

import org.orbeon.datatypes.Mediatype
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.processor.pdf.ImageSupport
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, Mediatypes, PathUtils}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xml.{FunctionSupport, RuntimeDependentFunction}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.saxon.om.EmptyIterator
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import shapeless.syntax.typeable.typeableOps

import java.net.URI
import scala.util.{Failure, Success}


class FRTransformUploadedImage extends FunctionSupport with RuntimeDependentFunction {

  override def iterate(context: XPathContext): om.SequenceIterator = {

    implicit val xpathContext: XPathContext    = context
    implicit val ec          : ExternalContext = CoreCrossPlatformSupport.externalContext
    implicit val logger      : IndentedLogger  = XFormsAPI.inScopeContainingDocument.controls.indentedLogger

    val binding = itemArgument(0).narrowTo[om.NodeInfo].getOrElse(throw new IllegalArgumentException("not a node"))

    val bindingValue = binding.getStringValue
    val maxWidthOpt  = stringArgumentOpt(1).flatMap(_.trimAllToOpt).map(_.toInt)
    val maxHeightOpt = stringArgumentOpt(2).flatMap(_.trimAllToOpt).map(_.toInt)
    val formatOpt    = stringArgumentOpt(4).flatMap(_.trimAllToOpt)
    val quality      = stringArgumentOpt(3).flatMap(_.trimAllToOpt).map(_.toFloat / 100f).getOrElse(0.8f)

    val mediatypeStringOpt = formatOpt flatMap Mediatypes.findMediatypeForExtension
    val mediatypeOpt       = mediatypeStringOpt.flatMap(Mediatype.unapply)

    val hasAtLeastOneTransformParameter =
      maxWidthOpt.isDefined || maxHeightOpt.isDefined || mediatypeOpt.isDefined

    def updateExtensionIfNeeded(filename: String, mediatype: Mediatype): Option[String] = {
      for {
        oldExt <- PathUtils.findExtension(filename)
        newExt <- Mediatypes.findExtensionForMediatype(mediatype.toString)
        if oldExt != newExt
        newFilename <- PathUtils.maybeReplaceExtension(filename, newExt)
      } yield
        newFilename
    }

    def updatedFilename(filename: String, mediatypeOpt: Option[Mediatype]): String = {

      val newFilenameOpt =
        for {
          mediatype   <- mediatypeOpt
          newFilename <- updateExtensionIfNeeded(filename, mediatype)
        } yield
          newFilename

      newFilenameOpt getOrElse filename
    }

    if (hasAtLeastOneTransformParameter)
      bindingValue.trimAllToOpt foreach { uriString =>

        // For now we limit the ability to transform to a just-uploaded temporary file
        require(PathUtils.getProtocol(uriString) == "file")
        require(XFormsUploadControl.verifyMAC(uriString))

        ImageSupport.tryMaybeTransformImage(
          new URI(uriString),
          maxWidthOpt,
          maxHeightOpt,
          mediatypeOpt,
          quality
        ) match {
          case Success((newUri, newSize)) =>
            XFormsUploadControl.updateExternalValueAndMetadata(
              boundNode   = binding,
              rawNewValue = newUri.toString,
              filename    = binding attValueOpt "filename" map (updatedFilename(_, mediatypeOpt)),
              mediatype   = mediatypeStringOpt orElse (binding attValueOpt "mediatype"),
              size        = newSize
            )
          case Failure(t) =>
            throw t
        }
      }

    EmptyIterator.getInstance
  }
}
