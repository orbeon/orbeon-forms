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
import org.orbeon.oxf.xforms.analysis.DumbUIDependencies;
import org.orbeon.oxf.xforms.analysis.UIDependencies;

import java.util.HashSet;
import java.util.Set;

/**
 * Product version information.
 */
public class Version {

    protected static final String RELEASE_NUMBER = "@RELEASE@";

    protected static final Logger logger = LoggerFactory.createLogger(Version.class);
    private static final Set<String> WARNED_FEATURES = new HashSet<String>();

    private static Version version;
    static {
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
            logger.info("Did not find PE Version, using CE Version");
            version = new Version();
        }
    }

    protected Version() {}
    
    public static Version instance() {
        return version;
    }

    /**
     * Return the product version number.
     *
     * @return product version number
     */
    public static String getVersionNumber() {
        return RELEASE_NUMBER;
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
        return "Orbeon Forms " + RELEASE_NUMBER + ' ' + instance().getCode();
    }

    /**
     * Version code, e.g. "CE" or "PE".
     *
     * @return
     */
    public String getCode() {
        return "CE";
    }

    public boolean isPE() {
        return false;
    }

    public boolean isPEFeatureEnabled(boolean featureRequested, String featureName) {
        if (featureRequested) {
            // Feature is requested but disallowed
            if (!WARNED_FEATURES.contains(featureName)) { // just warn the first time
                logger.warn("Feature is not enabled in this version of the product: " + featureName);
                WARNED_FEATURES.add(featureName);
            }
        }
        return false;
    }

    public UIDependencies createUIDependencies(XFormsContainingDocument containingDocument) {
        return new DumbUIDependencies();
    }

    public String toString() {
        return getVersionString();
    }
}
