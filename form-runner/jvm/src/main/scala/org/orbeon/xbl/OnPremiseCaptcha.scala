package org.orbeon.xbl

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import com.google.code.kaptcha.impl.DefaultKaptcha
import com.google.code.kaptcha.util.Config
import org.orbeon.oxf.util.Base64
import org.orbeon.oxf.util.CoreUtils._

import java.util.Properties

object OnPremiseCaptcha {

  private lazy val kaptcha: DefaultKaptcha = {
    val props = new Properties()
    new DefaultKaptcha().kestrel(_.setConfig(new Config(props)))
  }

  def answer(): String =
    kaptcha.createText()

  def image(text: String): String = {
    val image: BufferedImage = kaptcha.createImage(text)
    val os = new ByteArrayOutputStream
    ImageIO.write(image, "png", os)
    Base64.encode(os.toByteArray, useLineBreaks = true)
  }
}