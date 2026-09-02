package org.orbeon.oxf.util

import cats.syntax.option.*
import org.orbeon.datatypes.Mediatype
import org.orbeon.oxf.externalcontext.ExternalContext
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

  val jpgImg = URI.create("oxf:/org/orbeon/oxf/util/hs-2010-13-a-web.jpg")
  val pngImg = URI.create("oxf:/org/orbeon/oxf/util/hs-2010-13-a-web.png")

  describe("Image transformation") {

    val Expected = List(
      (jpgImg, None,     None,     jpegMediatype.some, None),
      (jpgImg, None,     None,     pngMediatype.some,  Some((pngMediatype,  400, 368))),
      (jpgImg, 200.some, None,     None,               Some((jpegMediatype, 200, 184))),
      (jpgImg, None,     200.some, None,               Some((jpegMediatype, 217, 200))),
      (jpgImg, 200.some, None,     pngMediatype.some,  Some((pngMediatype,  200, 184))),
      (jpgImg, None,     200.some, pngMediatype.some,  Some((pngMediatype,  217, 200))),

      (pngImg, None,     None,     jpegMediatype.some, Some((jpegMediatype, 400, 368))),
      (pngImg, None,     None,     pngMediatype.some,  None),
      (pngImg, 200.some, None,     None,               Some((pngMediatype,  200, 184))),
      (pngImg, None,     200.some, None,               Some((pngMediatype,  217, 200))),
      (pngImg, 200.some, None,     pngMediatype.some,  Some((pngMediatype,  200, 184))),
      (pngImg, None,     200.some, pngMediatype.some,  Some((pngMediatype,  217, 200))),
    )

    for ((imgUri, maxWidthOpt, maxHeightOpt, mediatypeOpt, expectedOpt) <- Expected)
      it(s"must pass for $imgUri/$maxWidthOpt/$maxHeightOpt/$mediatypeOpt") {
        PipelineSupport.withPipelineContextAndTestExternalContext() { (_, ec) =>

          implicit val externalContext: ExternalContext = ec

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

  describe("Rotation and orientation transformations") {

    val Width  = 100
    val Height = 60

    it("must properly composite transparent pixels over white for `TYPE_INT_ARGB` (compared to transformImageOld)") {
      val sourceImage = new java.awt.image.BufferedImage(Width, Height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
      // (0, 0) is opaque red, (1, 1) is fully transparent
      sourceImage.setRGB(0, 0, 0xFFFF0000)
      sourceImage.setRGB(1, 1, 0x00000000)

      val transform = ImageSupport.findTransformation(6, Width, Height).get // 90-degree CW
      val newResult = ImageSupport.transformImage(sourceImage, transform)

      // `transformImage` produces a standard `TYPE_INT_RGB` image where transparent pixel (1, 1) is composited to white
      assert(newResult.getType == java.awt.image.BufferedImage.TYPE_INT_RGB)
      assert(newResult.getWidth == Height)
      assert(newResult.getHeight == Width)
      assert((newResult.getRGB(Height - 1, 0) & 0x00FFFFFF) == 0x00FF0000) // Opaque red rotated to top-right
      assert((newResult.getRGB(1, 1) & 0x00FFFFFF) == 0x00FFFFFF) // White

      // Output can be compressed to JPEG without error
      val jpegBytes = ImageSupport.compressJpegImage(newResult, 0.8f)
      assert(jpegBytes.nonEmpty)
    }

    it("must handle all EXIF orientation transformations (2 through 8)") {
      for (orientation <- 2 to 8) {
        val sourceImage = new java.awt.image.BufferedImage(Width, Height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        // Draw red pixel at (0, 0) and green pixel at (Width - 1, Height - 1)
        sourceImage.setRGB(0, 0, 0x00FF0000)
        sourceImage.setRGB(Width - 1, Height - 1, 0x0000FF00)

        val transform = ImageSupport.findTransformation(orientation, Width, Height).get
        val result    = ImageSupport.transformImage(sourceImage, transform)

        val expectsSwappedDimensions = Set(5, 6, 7, 8).contains(orientation)
        val expectedWidth  = if (expectsSwappedDimensions) Height else Width
        val expectedHeight = if (expectsSwappedDimensions) Width else Height

        assert(result.getWidth == expectedWidth, s"Orientation $orientation width mismatch")
        assert(result.getHeight == expectedHeight, s"Orientation $orientation height mismatch")
        assert(result.getType == java.awt.image.BufferedImage.TYPE_INT_RGB)

        // Check expected target position for (0, 0) red pixel
        val (expectedRedX, expectedRedY) = orientation match {
          case 2 => (Width - 1, 0)              // Flip horizontal
          case 3 => (Width - 1, Height - 1)     // 180-deg rotation
          case 4 => (0, Height - 1)             // Flip vertical
          case 5 => (0, 0)                      // Transpose
          case 6 => (Height - 1, 0)             // 90-deg CW rotation
          case 7 => (Height - 1, Width - 1)     // Transverse transpose
          case 8 => (0, Width - 1)              // 270-deg CW rotation
        }

        assert((result.getRGB(expectedRedX, expectedRedY) & 0x00FFFFFF) == 0x00FF0000,
          s"Orientation $orientation red pixel mismatch at ($expectedRedX, $expectedRedY)")
      }
    }

    it("must preserve grayscale type and dimensions for TYPE_BYTE_GRAY") {
      val sourceImage = new java.awt.image.BufferedImage(Width, Height, java.awt.image.BufferedImage.TYPE_BYTE_GRAY)
      val transform = ImageSupport.findTransformation(6, Width, Height).get // 90-degree CW

      val result = ImageSupport.transformImage(sourceImage, transform)
      assert(result.getWidth == Height)
      assert(result.getHeight == Width)
      assert(result.getType == java.awt.image.BufferedImage.TYPE_BYTE_GRAY)
    }
  }
}
