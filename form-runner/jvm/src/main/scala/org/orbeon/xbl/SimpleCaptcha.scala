package org.orbeon.xbl

import java.awt.{Color, Font}
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

import nl.captcha.Captcha
import nl.captcha.backgrounds.GradiatedBackgroundProducer
import nl.captcha.text.renderer.ColoredEdgesWordRenderer
import org.orbeon.oxf.util.Base64

import scala.jdk.CollectionConverters._

object SimpleCaptcha {

  def createCaptcha(): Captcha = {
    val colors = Seq(Color.BLACK, Color.BLUE) asJava
    val fonts = Seq(
      new Font("Geneva", Font.ITALIC, 48),
      new Font("Courier", Font.BOLD, 48),
      new Font("Arial", Font.BOLD, 48)) asJava
    val wordRenderer = new ColoredEdgesWordRenderer(colors, fonts)
    val builder = new Captcha.Builder(200, 50)
    builder.addText(wordRenderer).gimp().addNoise().addBackground(new GradiatedBackgroundProducer()).build()
  }

  def image(captcha: Captcha): String = {
    val os = new ByteArrayOutputStream
    ImageIO.write(captcha.getImage, "png", os)
    Base64.encode(os.toByteArray, true)
  }

  def answer(captcha: Captcha): String = captcha.getAnswer
}
