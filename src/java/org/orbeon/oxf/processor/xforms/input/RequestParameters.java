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
package org.orbeon.oxf.processor.xforms.input;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.xforms.Constants;
import org.orbeon.oxf.processor.xforms.input.action.*;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.xml.ContentHandlerAdapter;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.DocumentException;

import javax.crypto.Cipher;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class RequestParameters {

    private static final Map actionClasses = new HashMap();

    static {
        actionClasses.put("insert", Insert.class);
        actionClasses.put("delete", Delete.class);
        actionClasses.put("setvalue", SetValue.class);
        actionClasses.put("setindex", SetIndex.class);
        actionClasses.put("set", org.orbeon.oxf.processor.xforms.input.action.Set.class);
    }

    private boolean submitted = false;
    private Map idToValue = new HashMap();
    private Map idToType = new HashMap();
    private List actions = new ArrayList();
    private Map fileInfos;
    private Document instance;

    private final boolean encryptNames = OXFProperties.instance().getPropertySet().getBoolean
            (Constants.XFORMS_ENCRYPT_NAMES, false).booleanValue();
    private final boolean encryptHiddenValues = OXFProperties.instance().getPropertySet().getBoolean
            (Constants.XFORMS_ENCRYPT_HIDDEN, false).booleanValue();
    private final Cipher cipher = encryptNames || encryptHiddenValues ? SecureUtils.getDecryptingCipher
            (OXFProperties.instance().getPropertySet().getString(Constants.XFORMS_PASSWORD)) : null;

    private static class FileInfo {
        public FileInfo (int fileRef) {
            this.fileId = fileRef;
        }
        public String value;
        public String type;
        public int fileId;
        public Integer filenameId;
        public Integer mediatypeId;
        public Integer contentLengthId;
        public String filename;
        public String mediatype;
        public String contentLength;
        public String originalValue;

        public boolean hasValue() {
            return value != null && !value.trim().equals("");
        }
    }

    private FileInfo getFileInfo(int fileId) {
        if (fileInfos == null)
            fileInfos = new HashMap();
        Integer fileIdObject = new Integer(fileId);
        FileInfo fileInfo = (FileInfo) fileInfos.get(fileIdObject);
        if (fileInfo == null) {
            fileInfo = new FileInfo(fileId);
            fileInfos.put(fileIdObject, fileInfo);
        }
        return fileInfo;
    }

    /**
     * Adds (id -> value) in idToValue. If there is already a value for this id,
     * concatenates the two values by adding a space.
     *
     * Also store the value type in idToType if present.
     */
    private void addValue(int id, String value, String type) {
        Integer idObject = new Integer(id);
        String currentValue = (String) idToValue.get(idObject);
        idToValue.put(idObject,
                currentValue == null || "".equals(currentValue) ? value :
                "".equals(value) ? currentValue : currentValue + ' ' + value);
        if (type != null)
            idToType.put(idObject, type);
    }

    public ContentHandler getContentHandlerForRequest() {
        // NOTE: We do this "by hand" as the apache digester has problems including namespace
        // handling and value whitespace trimming.

        return new ContentHandlerAdapter() {
            private Stack elementNameStack = new Stack();
            private boolean recording = false;
            private String recordingName;
            private StringBuffer recordingValue;
            private Attributes recordingAttributes;


            public void characters(char ch[], int start, int length) {
                if (recording)
                    recordingValue.append(ch, start, length);
            }

            public void startElement(String namespaceURI, String localName, String qName, Attributes atts) {
                if (isChildOfParameter()) {
                    recording = true;
                    recordingName = localName;
                    recordingValue = new StringBuffer();
                    recordingAttributes = new AttributesImpl(atts);
                }
                elementNameStack.add(localName);
            }

            public void endElement(String namespaceURI, String localName, String qName) {
                elementNameStack.pop();
                if (isChildOfParameter()) {
                    String value = recordingValue.toString();
                    if (recordingName.equals("name"))
                        name(value);
                    else if (recordingName.equals("value"))
                        value(value, recordingAttributes.getValue(XMLUtils.XSI_NAMESPACE, "type"));
                    else if (recordingName.equals("filename"))
                        filename(value);
                    else if (recordingName.equals("content-type"))
                        contentType(value);
                    else if (recordingName.equals("content-length"))
                        contentLength(value);
                }
            }

            private boolean isChildOfParameter() {
                int size = elementNameStack.size();
                return size == 3 && elementNameStack.elementAt(size - 1).equals("parameter")
                    && elementNameStack.elementAt(size - 2).equals("parameters")
                    && elementNameStack.elementAt(size - 3).equals("request");
            }

            private String name;

            private void name(String name) {
                // We know that name always come before all the other elements within a parameter,
                // including the value element
                this.name = name;
            }

            /**
             * Handle request parameter
             */
            private void value(String value, String type) {
                try {
                    if ("$submitted".equals(name)) {
                        submitted = true;
                    } else if ("$instance".equals(name)) {

                        // Un-base64, uncompress to get XML as text
                        final String xmlText;
                        {
                            ByteArrayInputStream compressedData = new ByteArrayInputStream(Base64.decode(value));
                            StringBuffer xml = new StringBuffer();
                            byte[] buffer = new byte[1024];
                            GZIPInputStream gzipInputStream = new GZIPInputStream(compressedData);
                            int size;
                            while ((size = gzipInputStream.read(buffer)) != -1)
                                xml.append(new String(buffer, 0, size));
                            xmlText = xml.toString();
                        }

                        // Parse XML and store as instance
                        LocationSAXContentHandler saxContentHandler = new LocationSAXContentHandler();
                        XMLUtils.stringToSAX(xmlText, null, saxContentHandler, false);
                        instance = saxContentHandler.getDocument();

                    } else if (name.startsWith("$upload^")) {
                        // Handle the case of the upload element

                        // Split encoded name
                        String s = name.substring("$upload^".length());
                        String[] fileInfoNames = new String[10];
                        int startIndex = 0;
                        int endIndex = -1;
                        int count = 0;
                        while ((endIndex = s.indexOf('^', startIndex)) != -1) {
                            fileInfoNames[count++] = s.substring(startIndex, endIndex);
                            startIndex = endIndex + 1;
                        }
                        fileInfoNames[count++] = s.substring(startIndex);

                        // Set values on FileInfo element
                        FileInfo fileInfo = getFileInfo(Integer.parseInt(fileInfoNames[0]));
                        fileInfo.value = value;
                        fileInfo.type = type;

                        if (fileInfoNames[2].length() > 0)
                            fileInfo.filenameId = fileInfoNames[2].length() > 0 ? new Integer(fileInfoNames[2]) : null;
                        if (fileInfoNames[4].length() > 0)
                            fileInfo.mediatypeId = fileInfoNames[4].length() > 0 ? new Integer(fileInfoNames[4]) : null;
                        if (fileInfoNames[6].length() > 0)
                            fileInfo.contentLengthId = fileInfoNames[6].length() > 0 ? new Integer(fileInfoNames[6]) : null;
                        if (fileInfoNames[1].length() > 0)
                            fileInfo.originalValue = fileInfoNames[1];

                    } else if (name.startsWith("$action^") || name.startsWith("$actionImg^")) {

                        // Image submit. If .y: ignore. If .x: remove .x at the end of name.
                        if (name.startsWith("$actionImg^")) {
                            if (name.endsWith(".y")) return;
                            name = name.substring(0, name.length() - 2);
                        }

                        // Separate different action, e.g.: $action^action1&action_2
                        StringTokenizer actionsTokenizer = new StringTokenizer(name.substring(name.indexOf('^') + 1), "&");
                        while (actionsTokenizer.hasMoreTokens()) {

                            // Parse an action string, e.g.: name&param1Name&param1Value&param2Name&param2Value
                            String actionString = URLDecoder.decode(actionsTokenizer.nextToken(), NetUtils.DEFAULT_URL_ENCODING);
                            String[] keyValue = new String[2];
                            String actionName = null;
                            Map actionParameters = new HashMap();
                            while (actionString.length() > 0) {
                                for (int i = 0; i < 2; i++) {
                                    int firstDelimiter = actionString.indexOf('&');
                                    keyValue[i] = firstDelimiter == -1 ? actionString
                                            : actionString.substring(0, firstDelimiter);
                                    actionString = firstDelimiter == -1 ? ""
                                            : actionString.substring(firstDelimiter + 1);
                                    if (actionName == null) break;
                                }
                                if (actionName == null) {
                                    actionName = keyValue[0];
                                } else {
                                    // We used to do URLDecoder.decode(keyValue[1], NetUtils.DEFAULT_URL_ENCODING) but this was once too many times
                                    actionParameters.put(keyValue[0], keyValue[1]);
                                }
                            }

                            // Create action object
                            Class actionClass = (Class) actionClasses.get(actionName);
                            if  (actionClass == null)
                                throw new OXFException("Cannot find implementation for action '" + actionName + "'");
                            Action action = (Action) actionClass.newInstance();
                            action.setParameters(actionParameters);
                            actions.add(action);
                        }
                    } else if (name.startsWith("$node^")) {
                        addValue(Integer.parseInt(name.substring("$node^".length())), value, type);
                    }
                } catch (InstantiationException e) {
                    throw new OXFException(e);
                } catch (IllegalAccessException e) {
                    throw new OXFException(e);
                } catch (UnsupportedEncodingException e) {
                    throw new OXFException(e);
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }

            private void filename(String filename) {
                if (name.startsWith("$upload^"))
                    getFileInfo(Integer.parseInt(name.substring("$upload^".length(), name.indexOf("^", "$upload^".length())))).filename = filename;
            }

            private void contentType(String contentType) {
                if (name.startsWith("$upload^"))
                    getFileInfo(Integer.parseInt(name.substring("$upload^".length(), name.indexOf("^", "$upload^".length())))).mediatype = contentType;
            }

            private void contentLength(String contentLength) {
                if (name.startsWith("$upload^"))
                    getFileInfo(Integer.parseInt(name.substring("$upload^".length(), name.indexOf("^", "$upload^".length())))).contentLength = contentLength;
            }

            public void endDocument() {
                // Complete handling of file uploads
                if (fileInfos != null) {
                    for (Iterator i = fileInfos.keySet().iterator(); i.hasNext();) {
                        String key = (String) i.next();
                        FileInfo fileInfo = (FileInfo) fileInfos.get(key);
                        if (fileInfo.hasValue()) {
                            // If a file was in fact uploaded, set all the file attributes
                            addValue(fileInfo.fileId, fileInfo.value, fileInfo.type);
                            if (fileInfo.filenameId != null)
                                addValue(fileInfo.filenameId.intValue(), fileInfo.filename, null);
                            if (fileInfo.mediatypeId != null)
                                addValue(fileInfo.mediatypeId.intValue(), fileInfo.mediatype, null);
                            if (fileInfo.contentLengthId != null)
                                addValue(fileInfo.contentLengthId.intValue(), fileInfo.contentLength, null);
                        } else {
                            // Set original value and empty attributes
                            addValue(fileInfo.fileId, fileInfo.originalValue, null);
                            if (fileInfo.filenameId != null)
                                addValue(fileInfo.filenameId.intValue(), "", null);
                            if (fileInfo.mediatypeId != null)
                                addValue(fileInfo.mediatypeId.intValue(), "", null);
                            if (fileInfo.contentLengthId != null)
                                addValue(fileInfo.contentLengthId.intValue(), "", null);
                        }
                    }
                }
            }
        };
    }

    public int[] getIds() {
        int[] ids = new int[idToValue.size()];
        int count = 0;
        for (Iterator i = idToValue.keySet().iterator(); i.hasNext();) {
            Integer id = (Integer) i.next();
            ids[count++] = id.intValue();
        }
        return ids;
    }

    public String getValue(int id) {
        return (String) idToValue.get(new Integer(id));
    }

    public String getType(int id) {
        return (String) idToType.get(new Integer(id));
    }

    public boolean isSubmitted() {
        return submitted;
    }

    public Action[] getActions() {
        return (Action[]) actions.toArray(new Action[actions.size()]);
    }

    public Document getInstance() {
        return instance;
    }
}
