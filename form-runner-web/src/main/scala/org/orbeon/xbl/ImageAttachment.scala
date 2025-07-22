package org.orbeon.xbl

import autowire.*
import org.orbeon.fr.rpc.{FormRunnerRpcApi, FormRunnerRpcClient}
import org.orbeon.xforms.Constants.DUMMY_IMAGE_URI
import org.orbeon.xforms.{Page, XFormsApp}
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.scalajs.js

object ImageAttachment {

  XBL.declareCompanion("fr|image-attachment", js.constructorOf[ImageAttachmentCompanion])

  private class ImageAttachmentCompanion(containerElem: html.Element) extends XBLCompanion {

    private def imageElOpt: Option[html.Image] =
      Option(containerElem.querySelector(".xforms-mediatype-image img").asInstanceOf[html.Image])

    // Only in the JS environment, we may need to asynchronously produce a `blob:` URL for the image
    // https://github.com/orbeon/orbeon-forms/issues/7149
    override def init(): Unit =
      if (XFormsApp.isBrowserEnvironment)
        if (imageElOpt.exists(_.src.contains(DUMMY_IMAGE_URI)))
          FormRunnerRpcClient[FormRunnerRpcApi]
            .retrieveResource(getXFormsFormOrThrow.deNamespaceIdIfNeeded(containerElem.id))
            .call()
            .foreach { urlOpt =>
              urlOpt.foreach { url =>
                imageElOpt.foreach { img =>
                  img.src = url.toString
                  if (url.getScheme == "blob")
                    img.onload = _ => dom.URL.revokeObjectURL(img.src)
                }
              }
            }

    override def destroy(): Unit =
      if (XFormsApp.isBrowserEnvironment) {
        // No cleanup needed as we revoke the object URL when the image loads
      }
  }
}
