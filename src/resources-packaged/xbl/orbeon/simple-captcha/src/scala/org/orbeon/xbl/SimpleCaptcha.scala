package org.orbeon.xbl

import org.orbeon.oxf.util.NetUtils
import nl.captcha.Captcha

object SimpleCaptcha {

    def isCorrect(value: String): Boolean = {
        val request = NetUtils.getExternalContext.getRequest
        val captcha = request.getSession(false).getAttributesMap.get(Captcha.NAME).asInstanceOf[Captcha]
        captcha.isCorrect(value)
    }
}
