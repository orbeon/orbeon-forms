/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.util;

import org.apache.commons.lang.StringUtils;
import org.orbeon.oxf.common.OXFException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;

public class SystemUtils {

    public static File getTemporaryDirectory() {
        return new File(System.getProperty("java.io.tmpdir")).getAbsoluteFile();
    }

    public static String getJarPath(Class clazz) {
        String resourceName = StringUtils.replace(clazz.getName(), ".", "/") + ".class";
        try {
            URL url = clazz.getClassLoader().getResource(resourceName);
            if (url == null)
                throw new IllegalArgumentException("Invalid resource name: " + resourceName);
            if (url.getProtocol().equals("jar")) {
                if (url.getProtocol().equals("jar")) {
                    // The current class is in a JAR file
                    String file = url.getFile();

                    int end = file.length() - ("!/".length() + resourceName.length());
                    final String fileSlash = "file:/";
                    final int fileSlashLen = fileSlash.length();
                    if (end > fileSlashLen && file.regionMatches(true, 0, fileSlash, 0, fileSlashLen)) {
                        file = file.substring(fileSlashLen, end);

                        file = URLDecoder.decode(file, "utf-8");

                        File jarDirectory = new File(file).getParentFile();
                        if (jarDirectory.isDirectory())
                            return jarDirectory.getCanonicalPath();
                    }
                }
            }
            return null;
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }
}
