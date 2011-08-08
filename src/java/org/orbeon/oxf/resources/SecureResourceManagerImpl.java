/**
 * Copyright (C) 2009 Orbeon, Inc.
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
import org.orbeon.oxf.util.SecureUtils;

import javax.crypto.CipherInputStream;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The secure resource manager is able to load ressources from a secure
 * archive on the filesystem.
 */
public class SecureResourceManagerImpl extends ResourceManagerBase {

    private static Logger logger = LoggerFactory.createLogger(SecureResourceManagerImpl.class);

    /**
     * Private password
     */
    private static final String password = new String(new char[]{
        (char) 55, (char) 58, (char) 124, (char) 42, (char) 120,
        (char) 126, (char) 76, (char) 81, (char) 32, (char) 67,
        (char) 92, (char) 46, (char) 99, (char) 59, (char) 101,
        (char) 36, (char) 108, (char) 109, (char) 70, (char) 40
    });

    protected Map resources;

    public SecureResourceManagerImpl(Map props) throws OXFException {
        super(props);
        String file = (String) props.get(SecureResourceManagerFactory.ARCHIVE_FILE);
        if (file == null)
            throw new OXFException("Property " + SecureResourceManagerFactory.ARCHIVE_FILE + " is null");
        File archiveFile = new File(file);
        if (archiveFile.canRead())
            init(archiveFile);
        else
            throw new OXFException("Archive file " + file + " does not exist or can not be read");
    }

    private void init(File archive) {
        try {
            resources = new HashMap();
            FileInputStream archiveFile = new FileInputStream(archive);
            ZipInputStream zip = new ZipInputStream(new CipherInputStream(archiveFile, 
                    SecureUtils.getDecryptingCipher(password, true)));

            ZipEntry ze;
            while ((ze = zip.getNextEntry()) != null) {
                byte[] buff = new byte[1024];
                int l;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                while ((l = zip.read(buff)) != -1)
                    bos.write(buff, 0, l);
                bos.close();

                resources.put(ze.getName(), new Value(new ByteArrayInputStream(bos.toByteArray()), bos.size()));
            }
            zip.close();
        } catch (Exception e) {
            throw new OXFException(e);
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

        Value v = ((Value) resources.get(key));
        if (v != null)
            return v.getInputStream();
        else
            throw new ResourceNotFoundException(key);
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
    public long lastModifiedImpl(String key, boolean doNotThrowResourceNotFound) {
        if (resources.get(key) != null)
            return 0;
        else {
            if (doNotThrowResourceNotFound) return -1;
            else throw new ResourceNotFoundException(key);
        }
    }

    /**
     * Returns the length of the file denoted by this abstract pathname.
     * @return The length, in bytes, of the file denoted by this abstract pathname, or 0L if the file does not exist
     */
    public int length(String key) {
        Value v = ((Value) resources.get(key));
        if (v != null)
            return v.getSize();
        else
            throw new ResourceNotFoundException(key);
    }

    /**
     * Indicates if the resource manager implementation suports write operations
     * @return true if write operations are allowed
     */
    public boolean canWrite(String key) {
        return false;
    }

    /**
     * Allows writing to the resource
     * @param key A Resource Manager key
     * @return an output stream
     */
    public OutputStream getOutputStream(String key) {
        throw new OXFException("Write Operation not supported");
    }

    /**
     * Allow writing to the resource
     * @param key A Resource Manager key
     * @return  a writer
     */
    public Writer getWriter(String key) {
        throw new OXFException("Write Operation not supported");
    }

    public String getRealPath(String key) {
        return null;
    }


    public static String getPassword() {
        return password;
    }

    private static class Value {
        private InputStream bis;
        private int size;

        public Value(InputStream bis, int size) {
            this.bis = bis;
            this.size = size;
        }

        public InputStream getInputStream() {
            return bis;
        }

        public int getSize() {
            return size;
        }
    }
}
