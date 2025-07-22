package org.orbeon.xbl

import cats.effect.IO
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.XFormsContainingDocument

import java.net.URI


object ImageAttachmentSupport extends ImageAttachmentSupportTrait {
  def retrieveResource(
    containingDocument: XFormsContainingDocument,
    forEffectiveId    : String,
    uri               : URI,
    contentType       : Option[String]
  )(implicit
    logger            : IndentedLogger
  ): IO[String] =
    throw new UnsupportedOperationException(
      "ImageAttachmentSupport.retrieveResource is not supported in the JVM environment"
    )
}