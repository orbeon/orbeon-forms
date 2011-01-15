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
package org.orbeon.oxf.util;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.taskdefs.MatchingTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.Charset;

public class CoffeeScriptTask extends MatchingTask {

    private File fromDir;
    private File toDir;

    public void setToDir(File value) { toDir = value; }
    public void setFromDir(File value) { fromDir = value; }

    @Override
    public void execute() throws BuildException {

        try {
            if (!fromDir.isDirectory())
                throw new BuildException(fromDir + " is not a valid directory");
            if (!toDir.isDirectory())
                throw new BuildException(toDir + " is not a valid directory");

            DirectoryScanner ds = getDirectoryScanner(fromDir);
            String[] files = ds.getIncludedFiles();

            for (int i = 0; i < files.length; i++) {
                File iFile = new File(fromDir.getAbsolutePath(), files[i]);
                File oFile = new File(toDir.getAbsolutePath(), files[i].substring(0, files[i].length() - 6) + "js");

                if (iFile.lastModified() < oFile.lastModified()) continue;

                // Read CoffeeScript as a string; CoffeeScript is always UTF-8
                Reader coffeeReader = new InputStreamReader(new FileInputStream(iFile), Charset.forName("UTF-8"));
                final String coffeeString = NetUtils.readStreamAsString(coffeeReader);

                // Compile
                log("Compiling " + files[i]);
                String javascriptString = org.orbeon.oxf.xforms.script.CoffeeScriptCompiler.compile(coffeeString, files[i], 0);

                // Write result
                oFile.getParentFile().mkdirs();
                Reader javascriptReader = new StringReader(javascriptString);
                Writer javascriptWriter = new OutputStreamWriter(new FileOutputStream(oFile), Charset.forName("UTF-8"));
                NetUtils.copyStream(javascriptReader, javascriptWriter);
                javascriptWriter.close();
            }
        } catch (IOException e) {
            throw new BuildException(e);
        }
    }
}
