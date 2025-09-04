package org.orbeon.fr.rpc

import cats.effect.IO
import cats.syntax.option.*
import org.orbeon.oxf.util.CoreCrossPlatformSupport.runtime
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.{Dispatch, EventCollector, XFormsEventFactory, XFormsEventTarget}
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

  // Method 1: send event
  def setCurrentPage(pagerElementId: String, pageNumber: Int): Future[Unit] = {
    val containingDocument = inScopeContainingDocument

    IO {
      val componentControlOpt = containingDocument.findControlByEffectiveId(pagerElementId).collect {
        case componentControl: XFormsComponentControl => componentControl
      }

      val eventTargetOpt = componentControlOpt.collect {
        case eventTarget: XFormsEventTarget => eventTarget
      }

      eventTargetOpt.foreach { eventTarget =>
        Dispatch.dispatchEvent(
          XFormsEventFactory.createEvent(
            eventName  = "fr-set-current-page",
            target     = eventTarget,
            properties = Map("page-number" -> Some(pageNumber.toString)),
          ),
          EventCollector.Throw
        )
      }
    }.unsafeToFuture()
  }

  // Method 2: set instance value directly
  /*def setCurrentPage2(pagerElementId: String, pageNumber: Int): Future[Unit] = {
    val containingDocument = inScopeContainingDocument

    IO {
      val componentControlOpt = containingDocument.findControlByEffectiveId(pagerElementId).collect {
        case componentControl: XFormsComponentControl => componentControl
      }

      for {
        componentControl <- componentControlOpt
        nestedContainer  <- componentControl.nestedContainerOpt
        instance         <- nestedContainer.findInstance("fr-paging-instance")
      } {
        XFormsAPI.setvalue(instance.rootElement / "page-number", pageNumber.toString)
      }
    }.unsafeToFuture()
  }*/
}
