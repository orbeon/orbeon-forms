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


import com.intersys.objects.CacheDatabase;
import com.intersys.objects.Database;
import org.apache.commons.cli.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.IOException;

public class CacheImport {

    public static final String DEFAULT_URL = "jdbc:Cache://localhost:1972/OXF";
    public static final String DEFAULT_USERNAME = "_SYSTEM";
    public static final String DEFAULT_PASSWORD = "sys";

    private String resourceDir;
    private String url;
    private String username;
    private String password;


    private void start() {
        try {
            Database db = CacheDatabase.getDatabase(url, username, password);
            File rootDir = new File(resourceDir);
            if (!rootDir.exists() || !rootDir.isDirectory())
                System.out.println(resourceDir + " doesn't exist or is not a directory");
            else
                importDir(db, rootDir);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void importDir(Database db, File root) {
        try {
            File[] files = root.listFiles();
            if (files == null)
                throw new RuntimeException("Can't list directory: " + root.getCanonicalPath());
            else {
                for (int i = 0; i < files.length; i++) {
                    File current = files[i];
                    if (current.isDirectory()) {
                        if (!current.getName().equals("CVS"))
                            importDir(db, current);
                    } else
                        importFile(db, current);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void importFile(Database db, File file) {
        try {
            if (!isXMLFile(file))
                return;

            eXtc.DOMAPI.openDOM(db);

            String fileName = file.getCanonicalPath();
            String key = fileName.substring(new File(resourceDir).getCanonicalPath().length()).replace('\\', '/');
            System.out.print("Importing file: " + fileName + " with key " + key + " ... ");

            eXtc.Document doc = eXtc.DOMAPI.getDocumentNode(db, key);
            if (doc != null) {
                if (doc.getcreationDate().getTime() >= file.lastModified()) {
                    System.out.println("skipping.");
                    return;
                }
                System.out.print(" Removing document first ...");
                eXtc.DOMAPI.removeDocument(db, key, "0", "0");
            }

            eXtc.XMLAPI.parseXMLFile(db, fileName, "0", "1", key, "0");
            System.out.println("Done");
            eXtc.DOMAPI.closeDOM(db);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private boolean isXMLFile(File file) {
        try {
            XMLUtils.newSAXParser().parse(file, new DefaultHandler());
            return true;
        } catch (Exception e) {
            return false;
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
        CacheImport ci = new CacheImport();
        ci.parseArgs(args);
        ci.start();
    }

}
