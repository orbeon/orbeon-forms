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
package org.orbeon.oxf.webapp;

import org.orbeon.oxf.pipeline.api.WebAppExternalContext;

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 *
 */
public class OrbeonClassLoader extends URLClassLoader {

    // Suffix for all delegate classes
    public static final String DELEGATE_CLASS_SUFFIX = "Delegate";

    private static final String CLASSLOADER_ENABLE_PROPERTY = "oxf.classloader.enable";
    private static final String CLASSLOADER_IGNORE_PACKAGES_PROPERTY = "oxf.classloader.ignore-packages";

    // Class Loader instance
    private static ClassLoader classLoader;

    // Packages to ignore
    private String[] ignorePackages;

    /**
     * Return the Class Loader to run all web application components.
     */
    public static ClassLoader getClassLoader(WebAppExternalContext webAppExternalContext) {
        if (classLoader == null) {
            synchronized (OrbeonClassLoader.class) {
                if (classLoader == null) {

                    if ("true".equals(webAppExternalContext.getInitAttributesMap().get(CLASSLOADER_ENABLE_PROPERTY))) {
                        // Allowed to use OXF Class Loader
                        boolean withinOXFClassLoader = false;
                        ClassLoader cl = OrbeonClassLoader.class.getClassLoader();
                        do {
                            if (cl.getClass().getName().equals(OrbeonClassLoader.class.getName())) {
                                withinOXFClassLoader = true;
                                break;
                            }
                            cl = cl.getParent();
                        } while (cl != null);

                        // Do this only once
                        if (!withinOXFClassLoader) {
                            // Return OXFClassLoader
                            URL[] urls = buildClassPath(webAppExternalContext);
                            String[] ignorePackages = buildIgnorePackages(webAppExternalContext);
                            classLoader = new OrbeonClassLoader(urls, OrbeonClassLoader.class.getClassLoader(), ignorePackages);
                        } else {
                            // Otherwise return Class Loader that loaded the OXFClassLoader class
                            classLoader = OrbeonClassLoader.class.getClassLoader();
                        }
                    } else {
                        // Return Class Loader that loaded the OXFClassLoader class
                        classLoader = OrbeonClassLoader.class.getClassLoader();
                    }
                }
            }
        }
        return classLoader;
    }

    private static String[] buildIgnorePackages(WebAppExternalContext webAppExternalContext) {
        String ignorePackagesProperty = (String) webAppExternalContext.getInitAttributesMap().get(CLASSLOADER_IGNORE_PACKAGES_PROPERTY);

        if (ignorePackagesProperty == null)
            return null;

        List<String> packages = new ArrayList<String>();
        StringTokenizer st = new StringTokenizer(ignorePackagesProperty);
        while (st.hasMoreTokens()) {
            String p = st.nextToken().trim();
            if (!p.endsWith("."))
                p += ".";
            packages.add(p);
        }

        if (packages.size() == 0)
            return null;

        String[] result = new String[packages.size()];
        packages.toArray(result);

        return result;
    }

    /**
     * Constructor to use internally, or for command-line and embedded mode. For web applications,
     * use getClassLoader() instead.
     *
     * @param urls            classpath
     * @param ignorePackages  names of packages to ingore
     * @param parent          parent classloader
     */
    public OrbeonClassLoader(URL[] urls, ClassLoader parent, String[] ignorePackages) {
        super(urls, parent);
        this.ignorePackages = ignorePackages;
    }

    protected synchronized Class loadClass(String name, boolean resolve) throws ClassNotFoundException {

        // Check packagse to ignore
        if (ignorePackages != null) {
            for (int i = 0; i < ignorePackages.length; i++) {
                if (name.startsWith(ignorePackages[i]))
                    return super.loadClass(name, resolve);
            }
        }

        // Reverse the order of ClassLoader.loadClass()
        Class c = findLoadedClass(name);
        if (c == null) {
            try {
                c = findClass(name);
            } catch (ClassNotFoundException e) {
                ClassLoader parent = getParent();
                if (parent != null) {
                    c = parent.loadClass(name);
                } else {
                    throw e;
                }
            }
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }

    public URL getResource(String name) {
        // Reverse the order of ClassLoader.getResource()
        URL url = findResource(name);
        if (url == null) {
            ClassLoader parent = getParent();
            if (parent != null)
                url = parent.getResource(name);
        }
        return url;
    }

    private static URL[] buildClassPath(WebAppExternalContext webAppExternalContext) {

        List<URL> files = new ArrayList<URL>();

        // Get WEB-INF/lib directory
        String webInfLibPath = webAppExternalContext.getRealPath("WEB-INF/lib");
        File webInfLibDir = new File(webInfLibPath);

        // Find jars and zips in path
        if (webInfLibDir.isDirectory()) {
            File[] jars = webInfLibDir.listFiles(new FileFilter() {
                public boolean accept(File pathname) {
                    String absolutePath = pathname.getAbsolutePath();
                    return absolutePath.endsWith(".jar") || absolutePath.endsWith(".zip");
                }
            });

            // Add them to string buffer
            if (jars != null) {
                for (int i = 0; i < jars.length; i++) {
                    try {
                        files.add(jars[i].toURI().toURL());
                    } catch (MalformedURLException e) {
                        webAppExternalContext.log("Could not convert file to URL: " + jars[i].toString());
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        // Get WEB-INF/classes directory
        String webInfClassesPath = webAppExternalContext.getRealPath("WEB-INF/classes");
        File webInfClassesDir = new File(webInfClassesPath);

        if (webInfClassesDir.isDirectory()) {
            try {
                files.add(webInfClassesDir.toURI().toURL());
            } catch (MalformedURLException e) {
                webAppExternalContext.log("Could not convert file to URL: " + webInfClassesDir.toString());
                throw new RuntimeException(e);
            }
        }

        // Return result
        URL[] result = new URL[files.size()];
        files.toArray(result);

        return result;
    }
}
