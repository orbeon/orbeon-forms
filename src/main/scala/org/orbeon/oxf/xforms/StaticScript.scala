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

case class ShareableScript(
  digest     : String,
  clientName : String,
  body       : String,
  paramNames : List[String]
)

case class StaticScript(
  prefixedId       : String,
  scriptType       : String,
  paramExpressions : List[String],
  shared           : ShareableScript
)

object StaticScript {

  def apply(
    prefixedId : String,
    scriptType : String,
    body       : String,
    params     : List[(String, String)]
  ): StaticScript = {

    val paramNames  = params map (_._1)
    val paramValues = params map (_._2)
    val digest      = digestString(body + '|' + (paramNames mkString "|"), "hex")

    StaticScript(
      prefixedId,
      scriptType,
      paramValues,
      ShareableScript(
        digest,
        "xf_" + digest, // digest must be JavaScript-safe (e.g. a hex string)
        body,
        paramNames
      )
    )
  }
}

case class ScriptInvocation(
  script              : StaticScript,
  targetEffectiveId   : String,
  observerEffectiveId : String,
  paramValues         : List[String]
)