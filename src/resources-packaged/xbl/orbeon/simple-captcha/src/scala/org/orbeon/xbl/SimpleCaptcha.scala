package org.orbeon.xbl

import collection.JavaConverters._
import nl.captcha.Captcha
import nl.captcha.backgrounds.GradiatedBackgroundProducer
import nl.captcha.text.renderer.ColoredEdgesWordRenderer
import java.awt.{Font, Color}
import javax.imageio.ImageIO
import java.io.ByteArrayOutputStream
import org.orbeon.oxf.util.{Base64, NetUtils}

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
