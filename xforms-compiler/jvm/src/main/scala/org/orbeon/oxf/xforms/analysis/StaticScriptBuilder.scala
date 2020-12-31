package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.util.SecureUtils.digestString
import org.orbeon.oxf.xforms._

import scala.collection.mutable


object StaticScriptBuilder {

  def apply(
    prefixedId        : String,
    scriptType        : ScriptType,
    body              : String,
    params            : List[(String, String)],
    shareableByDigest : mutable.Map[String, ShareableScript]
  ): StaticScript = {

    val paramNames = params map (_._1)
    val paramValues = params map (_._2)
    val digest = digestString(body + '|' + (paramNames mkString "|"), "hex")

    def newShareableScript =
      ShareableScript(
        digest,
        "xf_" + digest, // digest must be JavaScript-safe (e.g. a hex string)
        body,
        paramNames
      )

    StaticScript(
      prefixedId,
      scriptType,
      paramValues,
      shareableByDigest.getOrElseUpdate(digest, newShareableScript)
    )
  }

  private val TypeExtractor = "(?:(?:text|application)/)?([a-z]+)".r

  def scriptTypeFromMediatype(mediatype: String, default: Option[ScriptType]): Option[ScriptType] = mediatype match {
    case null                        => default
    case TypeExtractor("javascript") => Some(ScriptType.JavaScript)
    case TypeExtractor("xpath")      => Some(ScriptType.XPath)
    case _                           => None
  }

  def scriptTypeFromElem(elem: ElementAnalysis, default: Option[ScriptType]): Option[ScriptType] =
    scriptTypeFromMediatype(elem.element.attributeValue("type"), default)
}
