package org.orbeon.oxf.fr.library

import org.orbeon.oxf.common.VersionSupport
import org.orbeon.oxf.fr.Names
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.scaxon.SimplePath.NodeInfoOps


object FRCreatedWithOrNewerSupport {

  def isCreatedWithOrNewer(xblContainer: XBLContainer, paramVersion: String): Boolean = {

    val part = xblContainer.associatedControlOpt.map(_.container).getOrElse(xblContainer.containingDocument).partAnalysis

    val metadataVersionOpt =
      for {
        metadata           <- FRComponentParamSupport.findConstantMetadataRootElem(part)
        createdWithVersion <- metadata elemValueOpt Names.CreatedWithVersion
      } yield
        createdWithVersion

    metadataVersionOpt match {
      case None =>
        // If no version info the metadata, or no metadata, do as if the form was created with an old version
        false
      case Some(metadataVersion) =>
        VersionSupport.compare(metadataVersion, paramVersion).exists(_ >= 0)
    }
  }
}
