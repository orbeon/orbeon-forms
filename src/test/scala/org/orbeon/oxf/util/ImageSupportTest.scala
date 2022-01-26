package org.orbeon.oxf.util

import cats.syntax.option._
import org.orbeon.datatypes.Mediatype
import org.orbeon.oxf.test.{PipelineSupport, ResourceManagerSupport}
import org.orbeon.oxf.util.ImageMetadata.AllMetadata
import org.scalatest.funspec.AnyFunSpecLike

import java.net.URI


class ImageSupportTest
  extends ResourceManagerSupport
     with AnyFunSpecLike {

  implicit val logger: IndentedLogger =
    new IndentedLogger(LoggerFactory.createLogger(classOf[ImageSupportTest]), true)

  val Mediatype(jpegMediatype) = "image/jpeg"
  val Mediatype(pngMediatype)  = "image/png"

  val jpgImg = new URI("oxf:/org/orbeon/oxf/util/hs-2010-13-a-web.jpg")
  val pngImg = new URI("oxf:/org/orbeon/oxf/util/hs-2010-13-a-web.png")

  describe("Image transformation") {

    val Expected = List(
      (jpgImg, None,     None,     jpegMediatype.some, None),
      (jpgImg, None,     None,     pngMediatype.some,  Some((pngMediatype,  400,368))),
      (jpgImg, 200.some, None,     None,               Some((jpegMediatype, 200, 184))),
      (jpgImg, None,     200.some, None,               Some((jpegMediatype, 217, 200))),
      (jpgImg, 200.some, None,     pngMediatype.some,  Some((pngMediatype,  200, 184))),
      (jpgImg, None,     200.some, pngMediatype.some,  Some((pngMediatype,  217, 200))),

      (pngImg, None,     None,     jpegMediatype.some, Some((jpegMediatype, 400,368))),
      (pngImg, None,     None,     pngMediatype.some,  None),
      (pngImg, 200.some, None,     None,               Some((pngMediatype,  200, 184))),
      (pngImg, None,     200.some, None,               Some((pngMediatype,  217, 200))),
      (pngImg, 200.some, None,     pngMediatype.some,  Some((pngMediatype, 200, 184))),
      (pngImg, None,     200.some, pngMediatype.some,  Some((pngMediatype, 217, 200))),
    )

    for ((imgUri, maxWidthOpt, maxHeightOpt, mediatypeOpt, expectedOpt) <- Expected) {
      it(s"must pass for $imgUri/$maxWidthOpt/$maxHeightOpt/$mediatypeOpt") {
        PipelineSupport.withTestExternalContext() { implicit ec =>

          val actualOpt =
            ImageSupport.tryMaybeTransformImage(
              imgUri,
              maxWidthOpt,
              maxHeightOpt,
              mediatypeOpt,
              0.8f
            ).toOption.flatten map {
              case (destinationImageUri, _) =>

                val AllMetadata(resultMediatype, resultWidth, resultHeight, _) =
                  ImageSupport.tryReadAllMetadata(destinationImageUri).get

                (resultMediatype, resultWidth, resultHeight)
            }

          assert(expectedOpt == actualOpt)
        }
      }
    }
  }
}
