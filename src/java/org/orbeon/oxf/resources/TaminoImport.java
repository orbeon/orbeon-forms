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

import com.softwareag.tamino.db.api.accessor.TAccessLocation;
import com.softwareag.tamino.db.api.accessor.TXMLObjectAccessor;
import com.softwareag.tamino.db.api.connection.TConnection;
import com.softwareag.tamino.db.api.connection.TConnectionCloseException;
import com.softwareag.tamino.db.api.connection.TConnectionFactory;
import com.softwareag.tamino.db.api.objectModel.TXMLObject;
import com.softwareag.tamino.db.api.objectModel.dom.TDOMObjectModel;
import org.apache.commons.cli.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.FileReader;


public class TaminoImport {

    public static final String DEFAULT_URL = "http://localhost/tamino/oxf";
    public static final String DEFAULT_USERNAME = "jmercay";
    public static final String DEFAULT_PASSWORD = "gulliver";

    private String resourceDir;
    private String url;
    private String username;
    private String password;


    private void start() {
        TConnection connection = null;
        try {
            connection = TConnectionFactory.getInstance().newConnection(url, username, password);
            TXMLObjectAccessor xmlObjectAccessor = connection.newXMLObjectAccessor(
                    TAccessLocation.newInstance( "ino:etc" ),
                    TDOMObjectModel.getInstance() );

            File rootDir = new File(resourceDir);
            if (!rootDir.exists() || !rootDir.isDirectory())
                System.out.println(resourceDir + " doesn't exist or is not a directory");
            else
                importDir(xmlObjectAccessor, rootDir);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(connection != null)
                try {
                    connection.close();
                } catch (TConnectionCloseException e) {
                    e.printStackTrace();
                }
        }

    }

    private void importDir(TXMLObjectAccessor accessor, File root) {
        try {
            File[] files = root.listFiles();
            if (files == null)
                throw new RuntimeException("Can't list directory: " + root.getCanonicalPath());
            else {
                for (int i = 0; i < files.length; i++) {
                    File current = files[i];
                    if (current.isDirectory()) {
                        if (!current.getName().equals("CVS")) {
                            importDir(accessor, current);
                        }
                    } else
                        importFile(accessor, current);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void importFile(TXMLObjectAccessor accessor, File file) {
        try {
            if(!isXMLFile(file))
                return;
            String fileName = file.getCanonicalPath();
            System.out.println("Importing file: " + fileName);

            TXMLObject xmlObject = TXMLObject.newInstance( TDOMObjectModel.getInstance() );
            xmlObject.readFrom(new FileReader(file));
            xmlObject.setDocname(fileName);
            xmlObject.setId(fileName);
            xmlObject.setSystemId(fileName);

            accessor.insert(xmlObject);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    private boolean isXMLFile(File file) {
         try {
             XMLUtils.newSAXParser(XMLUtils.ParserConfiguration.PLAIN).parse(file, new DefaultHandler());
             return true;
         } catch (Exception e) {
             return false;
         }
     }

    private void parseArgs(String[] args) {
        Options options = new Options();
        options.addOption("r", "root", true, "Resource Root");
        options.addOption("u", "url", true, "Cache URL");
        options.addOption("l", "login", true, "Cache Login");
        options.addOption("p", "password", true, "Cache Password");
        try {
            CommandLineParser parser = new PosixParser();
            CommandLine cmd = parser.parse(options, args, true);

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
        TaminoImport ci = new TaminoImport();
        ci.parseArgs(args);
        ci.start();
    }

}
