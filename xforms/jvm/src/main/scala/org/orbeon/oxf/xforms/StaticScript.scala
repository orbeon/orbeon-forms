/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms

import org.orbeon.oxf.util.SecureUtils.digestString
import org.orbeon.oxf.xforms.analysis.ElementAnalysis

import scala.collection.mutable

case class ShareableScript(
  digest     : String,
  clientName : String,
  body       : String,
  paramNames : List[String]
)

case class StaticScript(
  prefixedId       : String,
  scriptType       : ScriptType,
  paramExpressions : List[String],
  shared           : ShareableScript
)

sealed trait ScriptType
case object  JavaScriptScriptType extends ScriptType
case object  XPathScriptType      extends ScriptType

object StaticScript {

  def apply(
    prefixedId        : String,
    scriptType        : ScriptType,
    body              : String,
    params            : List[(String, String)],
    shareableByDigest : mutable.Map[String, ShareableScript]
  ): StaticScript = {

    val paramNames  = params map (_._1)
    val paramValues = params map (_._2)
    val digest      = digestString(body + '|' + (paramNames mkString "|"), "hex")

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
    case TypeExtractor("javascript") => Some(JavaScriptScriptType)
    case TypeExtractor("xpath")      => Some(XPathScriptType)
    case _                           => None
  }

  def scriptTypeFromElem(elem: ElementAnalysis, default: Option[ScriptType]): Option[ScriptType] =
    scriptTypeFromMediatype(elem.element.attributeValue("type"), default)
}

case class ScriptInvocation(
  script              : StaticScript,
  targetEffectiveId   : String,
  observerEffectiveId : String,
  paramValues         : List[String]
)