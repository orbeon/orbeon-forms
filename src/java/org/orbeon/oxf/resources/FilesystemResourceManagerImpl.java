/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.resources;

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.LoggerFactory;

import java.io.*;
import java.net.URLDecoder;
import java.util.Map;

/**
 * The Filesystem resource manager is able to load resources from the filesystem with a direct
 * mapping, or, if the property is specified, within a sandbox.
 */
public class FilesystemResourceManagerImpl extends ResourceManagerBase {

    private static Logger logger = LoggerFactory.createLogger(FilesystemResourceManagerImpl.class);

    private boolean hasSandbox;
    protected File rootDirectory;

    public FilesystemResourceManagerImpl(Map props) throws OXFException {
        super(props);

        // Try to get sandbox directory
        String sandbox = (String) props.get(FilesystemResourceManagerFactory.SANDBOX_DIRECTORY_PROPERTY);
        if (sandbox == null) // also try backward-compatibility property
            sandbox = (String) props.get(FlatFileResourceManagerFactory.ROOT_DIR_PROPERTY);

        if (sandbox != null) {
            // A sandbox directory was found
            rootDirectory = new File(sandbox);
            if (!rootDirectory.isDirectory())
                throw new OXFException("Root directory " + sandbox + " does not refer to a valid directory");
            hasSandbox = true;
        }
    }

    /**
     * Returns a binary input stream for the specified key. The key could point
     * to any document type (text or binary).
     * @param key A Resource Manager key
     * @return a input stream
     */
    public InputStream getContentAsStream(String key) {
        if (logger.isDebugEnabled())
            logger.debug("getContentAsStream(" + key + ")");

        try {
            File file = getFile(key);
            if (file.canRead()) {
                return new FileInputStream(file);
            } else
                throw new ResourceNotFoundException("Cannot read from file " + key);
        } catch (FileNotFoundException e) {
            throw new ResourceNotFoundException("Cannot read from file " + key);
        }
    }

    /**
     * Returns a character reader from the resource manager for the specified
     * key. The key could point to any text document.
     * @param key A Resource Manager key
     * @return a character reader
     */
    public Reader getContentAsReader(String key) {
        return new InputStreamReader(getContentAsStream(key));
    }

    /**
     * Gets the last modified timestamp for the specified resource
     * @param key A Resource Manager key
     * @param doNotThrowResourceNotFound
     * @return a timestamp
     */
    protected long lastModifiedImpl(String key, boolean doNotThrowResourceNotFound) {
        File file = getFile(key);
        if (file.canRead())
            return file.lastModified();
        else {
            if (doNotThrowResourceNotFound) return -1;
            else throw new ResourceNotFoundException("Cannot read from file " + key);
        }
    }

    /**
     * Returns the length of the file denoted by this abstract pathname.
     * @return The length, in bytes, of the file denoted by this abstract pathname, or 0L if the file does not exist
     */
    public int length(String key) {
        return new Long(getFile(key).length()).intValue();
    }

    /**
     * Indicates if the resource manager implementation supports write operations
     * @return true if write operations are allowed
     */
    public boolean canWrite(String key) {
        return getFile(key).getParentFile().canWrite();
    }

    /**
     * Allows writing to the resource
     * @param key A Resource Manager key
     * @return an output stream
     */
    public OutputStream getOutputStream(String key) {
        try {
            File file = getFile(key);
            // Delete file if it exists
            if (file.exists() && file.canWrite())
                file.delete();
            // Create file
            file.createNewFile();

            if(file.canWrite())
                return new FileOutputStream(file);
            else
                throw new OXFException("Can't write to file: "+file);
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Allow writing to the resource
     * @param key A Resource Manager key
     * @return  a writer
     */
    public Writer getWriter(String key) {
        try {
            File file = getFile(key);

            // Delete file if it exists
            if (file.exists() && file.canWrite())
                file.delete();
            // Create file
            if (!file.createNewFile())
                throw new OXFException("Can't create file: " + file);

            if(file.canWrite())
                return new FileWriter(file);
            else
                throw new OXFException("Can't write to file: "+file);

        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    protected File getFile(String key) {
        try {
            // The key comes from a URL, and therefore needs to be decoded to be used as a file
            key = URLDecoder.decode(key, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new OXFException(e);
        }

        if (hasSandbox) {
            return new File(rootDirectory, key);
        } else {
            // Remove any starting / if there is a drive letter (kind of a hack!)
            // On Windows, we may receive keys of the form "/C:/myfile.xpl"
            // On Unix, we may receive keys of the form "/home/myfile.xpl"
            return new File(key.startsWith("/") && key.length() >= 3 && key.charAt(2) == ':' ? key.substring(1) : key);
        }
    }

    public String getRealPath(String key) {
        return getFile(key).getAbsolutePath();
        // Need an option for this as some callers call this for non-existing files
//        final File file = getFile(key);
//        if (!file.canRead())
//            throw new ResourceNotFoundException("Cannot read from file " + key);
//        else
//            return file.getAbsolutePath();
    }
}
