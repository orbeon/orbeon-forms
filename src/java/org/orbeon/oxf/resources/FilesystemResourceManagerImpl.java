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
package org.orbeon.oxf.resources;

import org.orbeon.oxf.common.OXFException;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;

/**
 * The Filesystem resource manager is able to load ressources from the filesystem with a direct
 * mapping, unlike the FlatFile resource manager which creates a sandbox.
 */
public class FilesystemResourceManagerImpl extends FlatFileResourceManagerImpl {

    public FilesystemResourceManagerImpl(Map props) throws OXFException {
        super();
    }

    protected File getFile(String key) {
        //System.out.println("Received key " + key);
        try {
            // The key comes from a URL, and therefore needs to be decoded to be used as a file
            key = URLDecoder.decode(key, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);
        }
        // Remove any starting / if there is a drive letter (kind of a hack!)
        // On Windows, we may receive keys of the form "/C:/myfile.xpl"
        // On Unix, we may receive keys of the form "/home/myfile.xpl"
        return new File(key.startsWith("/") && key.length() >= 3 && key.charAt(2) == ':' ? key.substring(1) : key);
    }
}
