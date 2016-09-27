/**
 *  Copyright (C) 2011 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.script

import java.io.InputStreamReader

import org.mozilla.javascript.Context
import org.orbeon.oxf.util.IOUtils._
import org.orbeon.oxf.util.ScalaUtils._

/**
 * CoffeeScript → JavaScript compiler.
 */
object CoffeeScriptCompiler {

  // Lazy so that if the compiler is never used it is never loaded
  private lazy val compilerScope = {
    val loader = getClass.getClassLoader
    useAndClose(loader.getResourceAsStream("org/orbeon/oxf/xforms/script/coffee-script.js")) { inputStream ⇒
      useAndClose(new InputStreamReader(inputStream, "utf-8")) { reader ⇒
        val cx = Context.enter()
        try {
          // "A scope is a set of JavaScript object" http://goo.gl/H8g5f
          // "initStandardObjects is an expensive method to call and it allocates a fair amount of memory"
          val topLevelScope = cx.initStandardObjects()

          cx.setOptimizationLevel(-1) // see e.g. http://goo.gl/QCxgT
          cx.evaluateReader(topLevelScope, reader, "coffee-script.js", 1, null)

          topLevelScope
        } finally {
          Context.exit()
        }
      }
    }
  }

  def compile(scriptSource: String, filename: String, line: Int) = synchronized {
    // For now play it safe: only one concurrent compilation for this object
    val cx = Context.enter()
    try {
      val newScope = cx.newObject(compilerScope)
      newScope.setParentScope(compilerScope)
      newScope.put("scriptSource", newScope, scriptSource)

      cx.evaluateString(newScope, "CoffeeScript.compile(scriptSource, {})", filename, line, null).asInstanceOf[String]
    } finally {
      Context.exit()
    }
  }
}