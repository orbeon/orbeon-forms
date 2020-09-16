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
package org.orbeon.oxf.processor.generator;

import com.drew.imaging.jpeg.JpegSegmentData;
import com.drew.imaging.jpeg.JpegSegmentReader;
import com.drew.imaging.jpeg.JpegSegmentType;
import com.drew.lang.StreamReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifReader;
import com.drew.metadata.iptc.IptcReader;
import org.orbeon.datatypes.LocationData;
import org.orbeon.dom.Document;
import org.orbeon.dom.Node;
import org.orbeon.oxf.common.Defaults;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.http.Headers;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.util.DateUtils;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.NumberUtils;
import org.orbeon.oxf.util.StringUtils;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.xml.XMLReceiverHelper;
import org.orbeon.oxf.xml.XPathUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This processor scans a filesystem hierarchy and retrieves information about each file. The
 * implementation is based on a modified version of the ant directory scanner.
 *
 * Ideas for improvements:
 *
 * o componentize so that file identification and enhanced file information (such as images) can be
 *   plugged
 *
 * o you could imagine, for XML files, to extract content with XPath expressions
 */
public class DirectoryScannerProcessor extends ProcessorImpl {

    public static final String DIRECTORY_GENERATOR_NAMESPACE_URI = "http://www.orbeon.org/oxf/directory-generator";

    private static final String DIRECTORY_ELEMENT = "directory";
    private static final String FILE_ELEMENT = "file";

    private static final String EXIF_ELEMENT = "exif-info";
    private static final String IPTC_ELEMENT = "iptc-info";
    private static final String TAG_ELEMENT = "param";

    private static final boolean DEFAULT_CASE_SENSITIVE = true;
    private static final boolean DEFAULT_DEFAULT_EXCLUDES = false;

    private static final boolean DEFAULT_BASIC_INFO = false;
    private static final boolean DEFAULT_EXIF_INFO = false;
    private static final boolean DEFAULT_IPTC_INFO = false;


    public DirectoryScannerProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, DIRECTORY_GENERATOR_NAMESPACE_URI));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    @Override
    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorOutputImpl(DirectoryScannerProcessor.this, name) {
            public void readImpl(PipelineContext context, XMLReceiver xmlReceiver) {

                // Read config
                final Config config = readCacheInputAsObject(context, getInputByName(INPUT_CONFIG), new CacheableInputReader<Config>() {
                    public Config read(PipelineContext context, ProcessorInput input) {

                        final Document configNode = readInputAsOrbeonDom(context, input);
                        final Config config = new Config();

                        final String baseDirectoryURLString = StringUtils.trimAllToEmpty(XPathUtils.selectStringValueNormalize(configNode, "/config/base-directory"));

                        // Use location data if present so that relative URLs can be supported
                        final LocationData locationData = getLocationData();

                        final String realPath = NetUtils.getRealPath(baseDirectoryURLString, locationData);
                        config.setBaseDirectory(realPath);

                        for (Iterator i = XPathUtils.selectNodeIterator(configNode, "/config/include"); i.hasNext();) {
                            final Node node = (Node) i.next();
                            final String value = XPathUtils.selectStringValueNormalize(node, ".");
                            if (value != null)
                                config.addInclude(value);
                        }
                        for (Iterator i = XPathUtils.selectNodeIterator(configNode, "/config/exclude"); i.hasNext();) {
                            final Node node = (Node) i.next();
                            final String value = XPathUtils.selectStringValueNormalize(node, ".");
                            if (value != null)
                                config.addExclude(value);
                        }
                        final boolean caseSensitive = ProcessorUtils.selectBooleanValue(configNode, "/config/case-sensitive", DEFAULT_CASE_SENSITIVE);
                        config.setCaseSensitive(caseSensitive);
                        final boolean defaultExcludes = ProcessorUtils.selectBooleanValue(configNode, "/config/default-excludes", DEFAULT_DEFAULT_EXCLUDES);
                        config.setDefaultExcludes(defaultExcludes);

                        final boolean basicInfo = ProcessorUtils.selectBooleanValue(configNode, "/config/image-metadata/basic-info", DEFAULT_BASIC_INFO);
                        config.setBasicInfo(basicInfo);
                        final boolean exifInfo = ProcessorUtils.selectBooleanValue(configNode, "/config/image-metadata/exif-info", DEFAULT_EXIF_INFO);
                        config.setExifInfo(exifInfo);
                        final boolean iptcInfo = ProcessorUtils.selectBooleanValue(configNode, "/config/image-metadata/iptc-info", DEFAULT_IPTC_INFO);
                        config.setIptcInfo(iptcInfo);

                        // TODO: sorting
                        // TODO: use-ant-patterns (default and only currently supported), follow-symlinks
                        // TODO: more generalized content-type detection
                        // TODO: WebDAV support, and/or integration with resource manager
                        // TODO: option to list excluded and not-included?

                        return config;
                    }
                });

                // Create and configure directory scanner
                final DirectoryScanner ds = new DirectoryScanner();

                if (config.isDefaultExcludes())
                    ds.addDefaultExcludes();
                if (config.getIncludes() != null)
                    ds.setIncludes(config.getIncludes());
                if (config.getExcludes() != null)
                    ds.setExcludes(config.getExcludes());
                ds.setBasedir(config.getBaseDirectory());
                ds.setCaseSensitive(config.isCaseSensitive());

                // Set the event listener
                final XMLReceiverHelper helper = new XMLReceiverHelper(xmlReceiver);
                ds.setEventListener(new DirectoryScanner.EventListener() {

                    private List<String> pathNames = new ArrayList<String>();
                    private List<String> paths = new ArrayList<String>();
                    private int pathLevel = 0;

                    private void enterDirectory(String path, String name, boolean included) {
                        if (name.equals(""))
                            return;
                        try {
                            if (included) {
                                outputPath();
                                pathLevel++;
                                helper.startElement(DIRECTORY_ELEMENT, new String[] {"name", name, "path", path + name, "included", "true"});
                            }
                            pathNames.add(name);
                            paths.add(path);
                        } catch (Exception e) {
                            throw new OXFException(e);
                        }
                    }

                    private void exitDirectory(String name) {
                        if (name.equals(""))
                            return;
                        try {
                            if (pathNames.size() <= pathLevel) {
                                helper.endElement();
                                pathLevel--;
                            }
                            pathNames.remove(pathNames.size() - 1);
                            paths.remove(paths.size() - 1);
                        } catch (Exception e) {
                            throw new OXFException(e);
                        }
                    }

                    private void outputPath() {
                        try {
                            for (int i = pathLevel; i < pathNames.size(); i++) {
                                String name = pathNames.get(i);
                                String path = paths.get(i);
                                helper.startElement(DIRECTORY_ELEMENT, new String[] {"name", name, "path", path + name});
                            }
                            pathLevel = pathNames.size();
                        } catch (Exception e) {
                            throw new OXFException(e);
                        }
                    }

                    public void deselectedFile(String path, String name) {
                    }

                    public void excludedFile(String path, String name) {
                    }

                    public void includedFile(String path, String name) {
                        outputPath();
                        try {
                            String filePath = path + name;
                            File file = new File(config.getBaseDirectory(), filePath);
                            long lastModified = file.lastModified();
                            String lastModifiedDate = DateUtils.formatIsoDateTimeUtc(lastModified);
                            long fileSize = file.length();

                            helper.startElement(FILE_ELEMENT, new String[]{"last-modified-ms", Long.toString(lastModified),
                                                                           "last-modified-date", lastModifiedDate,
                                                                           "size", Long.toString(fileSize),
                                                                           "path", filePath,
                                                                           "name", name});

                            if (config.isImageMetadata()) {
                                outputImageMetadata(helper, config, file);
                            }

                            helper.endElement();
                        } catch (Exception e) {
                            throw new OXFException(e);
                        }
                    }

                    public void notIncludedFile(String path, String name) {
                    }

                    public void endDeselectedDir(String path, String name) {
                        exitDirectory(name);
                    }

                    public void endExcludedDir(String path, String name) {
                        exitDirectory(name);
                    }

                    public void endIncludedDir(String path, String name) {
                        exitDirectory(name);
                    }

                    public void endNotIncludedDir(String path, String name) {
                        exitDirectory(name);
                    }

                    public void startDeselectedDir(String path, String name) {
                        enterDirectory(path, name, false);
                    }

                    public void startExcludedDir(String path, String name) {
                        enterDirectory(path, name, false);
                    }

                    public void startIncludedDir(String path, String name) {
                        enterDirectory(path, name, true);
                    }

                    public void startNotIncludedDir(String path, String name) {
                        enterDirectory(path, name, false);
                    }
                });

                // Output elements
                try {
                    final String baseDirectoryString = config.getBaseDirectory();
                    final File baseDirectoryFile = new File(baseDirectoryString);
                    if (!baseDirectoryFile.isDirectory())
                        throw new OXFException("base-directory element does not point to an existing directory: " + baseDirectoryString);
                    final String baseDirectoryName = baseDirectoryFile.getCanonicalFile().getName();

                    helper.startDocument();
                    helper.startElement(DIRECTORY_ELEMENT, new String[] { "name", baseDirectoryName, "path", baseDirectoryString });

                    // Do the scan
                    ds.scan();

//                    String[] directories = ds.getIncludedDirectories();
//                    String[] files = ds.getIncludedFiles();
//
//                    for (int i = 0; i < directories.length; i++)
//                        directories[i] = directories[i] + File.separator;
//
//                    String[] all = new String[directories.length + files.length];
//                    Arrays.sort(all);
//
//                    for (int i = 0; i < all.length; i++) {
//                        String current = all[i];
//                        boolean isDirectory = current.endsWith(File.separator);
//
//                    }

                    helper.endElement();
                    helper.endDocument();
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }

        };
        addOutput(name, output);
        return output;
    }

    private static void outputMetadata(XMLReceiverHelper helper, Metadata metadata, String elementName) throws MetadataException {
        if (metadata.getDirectoryCount() > 0) {
            for (Directory directory: metadata.getDirectories()) {

                helper.startElement(elementName, new String[]{"name", directory.getName()});

                for (Tag tag: directory.getTags()) {
                    helper.startElement(TAG_ELEMENT);
                    helper.element("id", tag.getTagType());
                    helper.element("name", tag.getTagName());
                    helper.element("value", tag.getDescription());
                    helper.endElement();
                }
                // TODO: Should do something with this?
                if (directory.hasErrors()) {
                    for (String error: directory.getErrors()) {
                        //System.out.println("ERROR: " + k.next());
                    }
                }

                helper.endElement();
            }
        }
    }

    private static class Config {
        private List<String> excludes;
        private List<String> includes;
        private String baseDirectory;
        private boolean caseSensitive;
        private boolean defaultExcludes;
        private boolean basicInfo;
        private boolean exifInfo;
        private boolean iptcInfo;

        public void addInclude(String pattern) {
            if (includes == null)
                includes = new ArrayList<String>();
            includes.add(pattern);
        }

        public void addExclude(String pattern) {
            if (excludes == null)
                excludes = new ArrayList<String>();
            excludes.add(pattern);
        }

        public void setBaseDirectory(String baseDirectory) {
            this.baseDirectory = baseDirectory;
        }

        public void setCaseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
        }

        public void setDefaultExcludes(boolean defaultExcludes) {
            this.defaultExcludes = defaultExcludes;
        }

        public String getBaseDirectory() {
            return baseDirectory;
        }

        public boolean isCaseSensitive() {
            return caseSensitive;
        }

        public boolean isDefaultExcludes() {
            return defaultExcludes;
        }

        public String[] getExcludes() {
            if (excludes == null)
                return null;
            String[] result = new String[excludes.size()];
            excludes.toArray(result);
            return result;
        }

        public String[] getIncludes() {
            if (includes == null)
                return null;
            String[] result = new String[includes.size()];
            includes.toArray(result);
            return result;
        }

        public boolean isImageMetadata() {
            return isBasicInfo() || isExifInfo() || isIptcInfo();
        }

        public boolean isExifInfo() {
            return exifInfo;
        }

        public void setExifInfo(boolean exifInfo) {
            this.exifInfo = exifInfo;
        }

        public boolean isIptcInfo() {
            return iptcInfo;
        }

        public void setIptcInfo(boolean iptcInfo) {
            this.iptcInfo = iptcInfo;
        }

        public boolean isBasicInfo() {
            return basicInfo;
        }


        public void setBasicInfo(boolean basicInfo) {
            this.basicInfo = basicInfo;
        }
    }

    private static void outputImageMetadata(XMLReceiverHelper helper, Config config, File file) throws Exception {
        helper.startElement("image-metadata");
        InputStream is = new BufferedInputStream(new FileInputStream(file));
        try {
            String contentType = URLConnection.guessContentTypeFromStream(is);
            if (contentType != null && contentType.startsWith("image/")) {

                if (contentType.equals("image/jpeg")) {

                    JpegSegmentData segmentData = JpegSegmentReader.readSegments(new StreamReader(is), null);

                    // Basic info: content-type, size and comment
                    if (config.isBasicInfo()) {

                        helper.startElement("basic-info");
                        helper.element(Headers.ContentTypeLower(), contentType);

                        byte[] startOfFrameSegment = segmentData.getSegment(JpegSegmentType.SOF0);
                        if (startOfFrameSegment != null) {
                            // Big-endian, unsigned encoding
                            int width = NumberUtils.readShortBigEndian(startOfFrameSegment, 3) & 0xffff;
                            int height = NumberUtils.readShortBigEndian(startOfFrameSegment, 1) & 0xffff;
                            helper.element("width", width);
                            helper.element("height", height);
                        }
                        byte[] commentSegment = segmentData.getSegment(JpegSegmentType.COM);
                        if (commentSegment != null)
                            helper.element("comment", new String(commentSegment), Defaults.DefaultEncodingForServletCompatibility()); // probably just ASCII

                        helper.endElement();
                    }
                    // Exif info
                    if (config.isExifInfo()) {
                        byte[] exifSegment = segmentData.getSegment(JpegSegmentType.APP1);
                        if (exifSegment != null) {
                            Metadata metadata = new Metadata();
                            new ExifReader().extract(exifSegment, metadata, JpegSegmentType.APP1);
                            outputMetadata(helper, metadata, EXIF_ELEMENT);
                        }
                    }
                    // IPTC info
                    if (config.isIptcInfo()) {
                        byte[] iptcSegment = segmentData.getSegment(JpegSegmentType.APPD);
                        if (iptcSegment != null) {
                            Metadata metadata = new Metadata();
                            new IptcReader().extract(iptcSegment, metadata, JpegSegmentType.APPD);
                            outputMetadata(helper, metadata, IPTC_ELEMENT);
                        }
                    }

                } else if (contentType.equals("image/gif")) {

                    // Basic info: content-type and size
                    if (config.isBasicInfo()) {
                        helper.startElement("basic-info");
                        helper.element(Headers.ContentTypeLower(), contentType);

                        byte[] bytes = new byte[10];
                        int count = is.read(bytes);

                        if (count == bytes.length) {
                            // Little-endian, unsigned encoding
                            int width = NumberUtils.readShortLittleEndian(bytes, 6) & 0xffff;
                            int height = NumberUtils.readShortLittleEndian(bytes, 8) & 0xffff;
                            helper.element("width", width);
                            helper.element("height", height);
                        }
                        helper.endElement();
                    }
                } else if (contentType.equals("image/png")) {
                    // Basic info: content-type and size
                    if (config.isBasicInfo()) {
                        helper.startElement("basic-info");
                        helper.element(Headers.ContentTypeLower(), contentType);

                        // See http://www.libpng.org/pub/png/spec/1.2/

                        byte[] bytes = new byte[8 + 4 + 4 + 13];// header + chunk length + chunk type + IHDR content
                        int count = is.read(bytes);

                        if (count == bytes.length) {
                            if (bytes[12] == 'I' && bytes[13] == 'H' && bytes[14] == 'D' && bytes[15] == 'R') {
//                                int chunkLength = NumberUtils.readIntBigEndian(bytes, 8);
                                int width = NumberUtils.readIntBigEndian(bytes, 16);
                                int height = NumberUtils.readIntBigEndian(bytes, 20);
//                                int bitDepth = bytes[24];
//                                int colorType = bytes[25];
//                                int compressionMethod = bytes[26];
//                                int filterMethod = bytes[27];
//                                int interlaceMethod = bytes[28];

                                helper.element("width", width);
                                helper.element("height", height);
                            }
                        }

                        helper.endElement();
                    }
                } else {
                    // Basic info: just content-type
                    if (config.isBasicInfo()) {
                        helper.startElement("basic-info");
                        helper.element(Headers.ContentTypeLower(), contentType);
                        helper.endElement();
                    }
                }
            }
        } finally {
            if (is != null)
                is.close();
        }
        helper.endElement();
    }
}
