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
package org.orbeon.oxf.util

import org.apache.tools.ant.BuildException
import org.apache.tools.ant.taskdefs.MatchingTask
import java.nio.charset.Charset
import org.orbeon.oxf.xforms.script.CoffeeScriptCompiler
import reflect.BeanProperty
import ScalaUtils._
import java.io._

class CoffeeScriptTask extends MatchingTask {
    
    @BeanProperty var fromDir: File = _
    @BeanProperty var toDir: File = _

    override def execute() {

        Seq(fromDir, toDir) find (!_.isDirectory) foreach (s => throw new BuildException(s + " is not a valid directory"))

        try {
            for (fileName <- getDirectoryScanner(fromDir).getIncludedFiles) {

                val iFile = new File(fromDir.getAbsolutePath, fileName)
                val oFile = new File(toDir.getAbsolutePath, fileName.split('.').init :+ "js" mkString ".")

                if (oFile.lastModified < iFile.lastModified) {

                    // Read CoffeeScript as a string; CoffeeScript is always UTF-8
                    val coffeeReader = new InputStreamReader(new FileInputStream(iFile), Charset.forName("UTF-8"))
                    val coffeeString = NetUtils.readStreamAsString(coffeeReader)

                    // Compile
                    log("Compiling " + fileName)
                    val jsString = CoffeeScriptCompiler.compile(coffeeString, fileName, 0)

                    // Write result
                    oFile.getParentFile.mkdirs()
                    try {
                        copyReader(new StringReader(jsString), new OutputStreamWriter(new FileOutputStream(oFile), Charset.forName("UTF-8")))
                    } catch {
                        case e =>
                            runQuietly(oFile.delete()) // remove output file if something happened while producing the file
                            throw e
                    }
                }
            }
        } catch {
            case e => throw new BuildException(e);
        }
    }
}