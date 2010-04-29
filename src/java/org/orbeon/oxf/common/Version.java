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
package org.orbeon.oxf.common;

import org.apache.log4j.Logger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;

/**
 * Product version information.
 */
public abstract class Version {

    protected static final String VERSION_NUMBER = "@RELEASE@";
    protected static final String EDITION = "@EDITION@";

    protected static final Logger logger = LoggerFactory.createLogger(Version.class);


    private static Version version;

    protected Version() {}
    
    public static Version instance() {
        if (version == null) {
            if (isPE()) {
                // Create a PEVersion instance
                ClassLoader classLoader = null;
                try {
                    classLoader = Thread.currentThread().getContextClassLoader();
                } catch (Exception e) {
                    logger.error("Failed calling getContextClassLoader(), trying Class.forName()");
                }
                try {
                    final String versionClassName = "org.orbeon.oxf.common.PEVersion";
                    Class<?> versionClass;
                    if (classLoader != null) {
                        try {
                            versionClass = classLoader.loadClass(versionClassName);
                        } catch (Exception ex) {
                            versionClass = Class.forName(versionClassName);
                        }
                    } else {
                        versionClass = Class.forName(versionClassName);
                    }
                    version = (Version) versionClass.newInstance();
                } catch (Exception e) {
                    final String message = "Loading " + getVersionString() + " configuration failed. Please make sure a license file is in place.";
                    logger.error(message);
                    throw new OXFException(message);
                }
            } else {
                // Just create a CEVersion instance
                version = new CEVersion();
            }
        }
        return version;
    }

    /**
     * Return the product version number.
     *
     * @return product version number
     */
    public static String getVersionNumber() {
        return VERSION_NUMBER;
    }

    // For backward compatibility only (if called from third-party XSLT)
    public static String getVersion() {
        return getVersionNumber();
    }

    /**
     * Return the product version string.
     *
     * @return product version string
     */
    public static String getVersionString() {
        return "Orbeon Forms " + VERSION_NUMBER + ' ' + EDITION;
    }

    /**
     * Product edition, e.g. "CE" or "PE".
     *
     * @return
     */
    public static final String getEdition() {
        return EDITION;
    }

    public static final boolean isPE() {
        return "PE".equals(EDITION);
    }

    public abstract boolean isPEFeatureEnabled(boolean featureRequested, String featureName);
    public abstract XPathDependencies createUIDependencies(XFormsContainingDocument containingDocument);

    public String toString() {
        return getVersionString();
    }
}
