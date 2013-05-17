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

import org.apache.commons.cli.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.XMLUtils;
import org.w3c.dom.Document;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.modules.XMLResource;

import java.io.File;


public class XMLDBImport {

    public static final String DEFAULT_URL = "xmldb:tamino:/localhost/tamino/oxf/ino:etc";
    public static final String DEFAULT_USERNAME = "jmercay";
    public static final String DEFAULT_PASSWORD = "gulliver";

    private String resourceDir;
    private String url;
    private String username;
    private String password;


    private void start() {
        try {
            Database db = (Database) Class.forName("com.softwareag.tamino.xmldb.api.base.TDatabase").newInstance();
            DatabaseManager.registerDatabase(db);
            Collection rootCollection = DatabaseManager.getCollection(url, username, password);
            File rootDir = new File(resourceDir);
            if (!rootDir.exists() || !rootDir.isDirectory())
                System.out.println(resourceDir + " doesn't exist or is not a directory");
            else
                importDir(rootCollection, rootDir);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void importDir(Collection collection, File root) {
        try {
            File[] files = root.listFiles();
            if (files == null)
                throw new RuntimeException("Can't list directory: " + root.getCanonicalPath());
            else {
                for (int i = 0; i < files.length; i++) {
                    File current = files[i];
                    if (current.isDirectory()) {
                        if (!current.getName().equals("CVS")) {
//                            Collection c = collection.getChildCollection(current.getName());
//                            if (c == null) {
//                                CollectionManagementService service = new TCollectionManagementService(collection);
//                                c = service.createCollection(current.getName());
//                            }
                            importDir(collection, current);
                        }
                    } else
                        importFile(collection, current);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void importFile(Collection collection, File file) {
        try {
            String fileName = file.getCanonicalPath();
            System.out.println("Importing file: " + fileName + " in collection: " + collection.getName());
            Document doc;
            try {
                doc = XMLUtils.fileToDOM(file);
            } catch (OXFException e) {
                System.out.println("skipped");
                return;
            }
            System.out.println(" XML ");
            XMLResource resource = (XMLResource) collection.createResource(fileName, XMLResource.RESOURCE_TYPE);

            resource.setContentAsDOM(doc);
            collection.storeResource(resource);


//                System.out.print(" Binary ");
//                BinaryResource resource =
//                        (BinaryResource) collection.createResource(fileName, BinaryResource.RESOURCE_TYPE);
//
//                byte[] bytes = new byte[new Long(file.length()).intValue()];
//                InputStream is = new FileInputStream(file);
//                is.read(bytes);
//                resource.setContent(bytes);
//                collection.storeResource(resource);

            System.out.println(" ... Done");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }


    private void parseArgs(String[] args) {
        Options options = new Options();
        options.addOption('r', "root", true, "Resource Root", false);
        options.addOption('u', "url", true, "Cache URL", false);
        options.addOption('l', "login", true, "Cache Login", false);
        options.addOption('p', "password", true, "Cache Password", false);
        try {
            CommandLine cmd = options.parse(args, true);

            resourceDir = cmd.getOptionValue('r', ".");
            url = cmd.getOptionValue('u', DEFAULT_URL);
            username = cmd.getOptionValue('l', DEFAULT_USERNAME);
            password = cmd.getOptionValue('p', DEFAULT_PASSWORD);

        } catch (MissingArgumentException e) {
            new HelpFormatter().printHelp("Missing argument", options);
            System.exit(1);
        } catch (UnrecognizedOptionException e) {
            new HelpFormatter().printHelp("Unrecognized option", options);
            System.exit(1);
        } catch (MissingOptionException e) {
            new HelpFormatter().printHelp("Missing option", options);
            System.exit(1);
        } catch (Exception e) {
            new HelpFormatter().printHelp("Unknown error", options);
            System.exit(1);
        }

    }

    public static void main(String[] args) {
        XMLDBImport ci = new XMLDBImport();
        ci.parseArgs(args);
        ci.start();
    }

}
