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

import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.LoggerFactory;

import java.io.*;
import java.net.URLDecoder;
import java.util.Map;

/**
 * The FlatFile resource manager is able to load resources from a sandbox in a filesystem.
 */
public class FlatFileResourceManagerImpl extends ResourceManagerBase {

    private static Logger logger = LoggerFactory.createLogger(FlatFileResourceManagerImpl.class);

    protected File rootDirectory;

    public FlatFileResourceManagerImpl(Map props) throws OXFException {
        super(props);
        String root = (String) props.get(FlatFileResourceManagerFactory.ROOT_DIR_PROPERTY);
        if (root == null)
            throw new OXFException("Property " + FlatFileResourceManagerFactory.ROOT_DIR_PROPERTY + " is null");
        rootDirectory = new File(root);
        if (!rootDirectory.isDirectory())
            throw new OXFException("Root directory " + root + " does not refer to a valid directory");
    }

    protected FlatFileResourceManagerImpl(Map props, boolean useRootDir) throws OXFException {
        super(props);
        if (useRootDir) {
            String root = (String) props.get(FlatFileResourceManagerFactory.ROOT_DIR_PROPERTY);
            if (root == null)
                throw new OXFException("Property " + FlatFileResourceManagerFactory.ROOT_DIR_PROPERTY + " is null");
            rootDirectory = new File(root);
            if (!rootDirectory.isDirectory())
                throw new OXFException("Root directory " + root + " does not refer to a valid directory");
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
     * Gets the last modified timestamp for the specofoed resource
     * @param key A Resource Manager key
     * @return a timestamp
     */
    protected long lastModifiedImpl(String key) {
        File file = getFile(key);
        if (file.canRead())
            return file.lastModified();
        else
            throw new ResourceNotFoundException("Cannot read from file " + key);
    }

    /**
     * Returns the length of the file denoted by this abstract pathname.
     * @return The length, in bytes, of the file denoted by this abstract pathname, or 0L if the file does not exist
     */
    public int length(String key) {
        return new Long(getFile(key).length()).intValue();
    }

    /**
     * Indicates if the resource manager implementation suports write operations
     * @return true if write operations are allowed
     */
    public boolean canWrite() {
        return true;
    }

    /**
     * Allows writing to the resource
     * @param key A Resource Manager key
     * @return an output stream
     */
    public OutputStream getOutputStream(String key) {
        try {
            invalidateLastModifiedCache(key);
            File file = getFile(key);
            // Delete file if it exists
            if (file.exists() && file.canWrite())
                file.delete();
            // Create file
            if (!file.createNewFile())
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
            invalidateLastModifiedCache(key);
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
        return new File(rootDirectory, key);
    }

    public String getRealPath(String key) {
        return getFile(key).getAbsolutePath();
    }
}
