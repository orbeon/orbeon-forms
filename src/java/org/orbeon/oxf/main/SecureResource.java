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
package org.orbeon.oxf.main;

import org.apache.commons.cli.*;
import org.orbeon.oxf.resources.SecureResourceManagerImpl;
import org.orbeon.oxf.util.SecureUtils;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SecureResource {

    public static final int ENCRYPT_MODE = 0;
    public static final int DECRYPT_MODE = 1;
    public static final int VIEW_MODE = 3;

    private int mode;
    private String resourceRoot;
    private String archiveName;

    public SecureResource(String[] args) {
        try {
            parseArgs(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private void encrypt() {
        try {
            FileOutputStream archiveFile = new FileOutputStream(archiveName);

            ZipOutputStream zip = new ZipOutputStream(new CipherOutputStream(archiveFile,
                    SecureUtils.getEncryptingCipher(SecureResourceManagerImpl.getPassword(), true)));
            File rr = new File(resourceRoot);
            archiveAndEncrypt(rr, zip);
            zip.finish();
            zip.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void view() {
        try {
            FileInputStream archiveFile = new FileInputStream(archiveName);
            ZipInputStream zip = new ZipInputStream(new CipherInputStream
                    (archiveFile, SecureUtils.getDecryptingCipher(SecureResourceManagerImpl.getPassword(), true)));
            ZipEntry ze;
            while ((ze = zip.getNextEntry()) != null) {
                System.out.println("entry: " + ze.getName());

            }
            zip.close();


        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void archiveAndEncrypt(File root, ZipOutputStream zip) throws Exception {
        if (root.isDirectory()) {
            if (root.getName().equals("CVS"))
                return;
            File[] files = root.listFiles();
            for (int i = 0; i < files.length; i++) {
                archiveAndEncrypt(files[i], zip);
            }
        } else {
            String f = computePath(root.getPath());
            System.out.println("Adding " + f);
            ZipEntry ze = new ZipEntry(f);
            zip.putNextEntry(ze);
            copyFile(root, zip);
            zip.closeEntry();
        }
    }

    private String computePath(String path) {
        return path.replace('\\', '/').substring(resourceRoot.length());
    }

    private void copyFile(File file, OutputStream os) throws Exception {
        byte[] buff = new byte[1024];
        FileInputStream fis = new FileInputStream(file);
        int l;
        while ((l = fis.read(buff)) != -1) {
            os.write(buff, 0, l);
        }
        fis.close();
    }

    private void parseArgs(String[] args) {
        Options options = new Options();

        OptionGroup group = new OptionGroup();
        group.addOption(new Option("e", "encrypt", false, "Encrypt"));
        group.addOption(new Option("d", "decrypt", false, "Decrypt"));
        group.addOption(new Option("v", "view", false, "View"));
        options.addOptionGroup(group);

        Option o = new Option("r", "root", true, "Resource Root");
        o.setRequired(false);
        options.addOption(o);
        options.addOption("a", "archive", true, "Archive Name");
        try {
            CommandLine cmd = new PosixParser().parse(options, args, true);
            if (cmd.hasOption('e')) {
                mode = ENCRYPT_MODE;
            } else if (cmd.hasOption('d')) {
                mode = DECRYPT_MODE;
            } else if (cmd.hasOption('v')) {
                mode = VIEW_MODE;
            }
            resourceRoot = cmd.getOptionValue('r', ".");
            archiveName = cmd.getOptionValue('a');


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

    public int getMode() {
        return mode;
    }

    public static void main(String[] args) throws Exception {

        SecureResource sr = new SecureResource(args);
        switch (sr.getMode()) {
            case SecureResource.ENCRYPT_MODE:
                sr.encrypt();
                break;
            case SecureResource.DECRYPT_MODE:

                break;
            case SecureResource.VIEW_MODE:
                sr.view();
                break;
        }

    }

}
