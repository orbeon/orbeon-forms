package org.orbeon.fr.rpc

import cats.syntax.option.*
import org.orbeon.oxf.util.CoreCrossPlatformSupport.runtime
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import org.orbeon.xbl.ImageAttachmentSupport

import java.net.URI
import scala.concurrent.Future


object FormRunnerRpcApiImpl extends FormRunnerRpcApi {

  import Router.logger

  def retrieveResource(clientId: String): Future[Option[URI]] = {

    val containingDocument = inScopeContainingDocument

    containingDocument
      .findControlByEffectiveId(clientId)
      .collect { case c: XFormsValueControl => c.boundNodeOpt }
      .flatten
      .map { node =>
        ImageAttachmentSupport.retrieveResource(
          containingDocument,
          clientId,
          URI.create(node.getStringValue),
          node.attValueOpt("mediatype")
        )
        .map(URI.create(_).some)
        .unsafeToFuture()
      }
      .getOrElse(Future.successful(None))
  }
}
