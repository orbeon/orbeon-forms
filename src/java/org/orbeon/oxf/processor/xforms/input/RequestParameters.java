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
import org.orbeon.oxf.util.Base64;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xml.ContentHandlerAdapter;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

import javax.crypto.Cipher;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;
import java.util.Set;

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
    private Map pathToValue = new HashMap();
    private Map pathToType = new HashMap();
    private Map idToRef = new HashMap();
    private List actions = new ArrayList();
    private Map fileInfos;


    private final boolean encryptNames = OXFProperties.instance().getPropertySet().getBoolean
            (Constants.XFORMS_ENCRYPT_NAMES, false).booleanValue();
    private final boolean encryptHiddenValues = OXFProperties.instance().getPropertySet().getBoolean
            (Constants.XFORMS_ENCRYPT_HIDDEN, false).booleanValue();
    private final Cipher cipher = encryptNames || encryptHiddenValues ? SecureUtils.getDecryptingCipher
            (OXFProperties.instance().getPropertySet().getString(Constants.XFORMS_PASSWORD)) : null;

    private static class FileInfo {
        public FileInfo (String fileRef) {
            this.fileRef = fileRef;
        }
        public String value;
        public String type;
        public String fileRef;
        public String filenameRef;
        public String mediatypeRef;
        public String contentLengthRef;
        public String filename;
        public String mediatype;
        public String contentLength;
        public String originalValue;

        public boolean hasValue() {
            return value != null && !value.trim().equals("");
        }
    }

    private FileInfo getFileInfo(String fileRef) {
        if (fileInfos == null)
            fileInfos = new HashMap();
        FileInfo fileInfo = (FileInfo) fileInfos.get(fileRef);
        if (fileInfo == null) {
            fileInfo = new FileInfo(fileRef);
            fileInfos.put(fileRef, fileInfo);
        }
        return fileInfo;
    }

    /**
     * Adds (name -> value) in pathToValue. If there is already a value for this name,
     * concatenates the two values by adding a space.
     *
     * Also store the value type in pathToType if present.
     */
    private void addValue(String name, String value, String type, boolean nameDecryted) {
        // Decrypt name if necessary
        if (encryptNames && !nameDecryted) {
            try {
                name = new String(cipher.doFinal(Base64.decode(name)));
            } catch (Exception e) {
                throw new OXFException("Cannot decode name '" + name + "'");
            }
        }

        String currentValue = (String) pathToValue.get(name);
        pathToValue.put(name,
                currentValue == null || "".equals(currentValue) ? value :
                "".equals(value) ? currentValue : currentValue + ' ' + value);
        if (type != null)
            pathToType.put(name, type);
    }

    private void setValue(String name, String value, String type) {
        pathToValue.put(name, value);
        if (type != null)
            pathToType.put(name, type);
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
                    handleValue(recordingName, recordingValue.toString(), recordingAttributes);
                }
            }

            private boolean isChildOfParameter() {
                int size = elementNameStack.size();
                return size == 3 && elementNameStack.elementAt(size - 1).equals("parameter")
                    && elementNameStack.elementAt(size - 2).equals("parameters")
                    && elementNameStack.elementAt(size - 3).equals("request");
            }

            private void handleValue(String elementName, String value, Attributes atts) {
                if (elementName.equals("name"))
                    name(value);
                else if (elementName.equals("value"))
                    value(value, atts.getValue(XMLUtils.XSI_NAMESPACE, "type"));
                else if (elementName.equals("filename"))
                    filename(value);
                else if (elementName.equals("content-type"))
                    contentType(value);
                else if (elementName.equals("content-length"))
                    contentLength(value);
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
                    } else if ("$hidden".equals(name)) {
                        if (encryptHiddenValues) {
                            try {
                                value = new String(cipher.doFinal(Base64.decode(value)));
                            } catch (Exception e) {
                                throw new OXFException("Cannot decrypt hidden field value '" + value + "'");
                            }
                        }
                        if (value.length() > 0)
                            nameValues(value, type, encryptHiddenValues);
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
                        FileInfo fileInfo = getFileInfo(fileInfoNames[0]);
                        fileInfo.value = value;
                        fileInfo.type = type;

                        if (fileInfoNames[2].length() > 0)
                            fileInfo.filenameRef = fileInfoNames[2];
                        if (fileInfoNames[4].length() > 0)
                            fileInfo.mediatypeRef = fileInfoNames[4];
                        if (fileInfoNames[6].length() > 0)
                            fileInfo.contentLengthRef = fileInfoNames[6];
                        if (fileInfoNames[1].length() > 0)
                            fileInfo.originalValue = fileInfoNames[1];

                    } else if ("$idRef".equals(name)) {
                        int equalPosition = value.indexOf('=');
                        String id = value.substring(0, equalPosition);
                        String ref = value.substring(equalPosition + 1);
                        List refs = (List) idToRef.get(id);
                        if (refs == null)
                            idToRef.put(id, refs = new ArrayList());
                        refs.add(ref);
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
                    } else if (!name.startsWith("$") && !name.equals("j_username")
                            && !name.equals("j_password")) {

                        if (name.indexOf('^') != -1) {
                            // Special case: we extract the value from the name (for checkbox and submit)
                            nameValues(name, type, false);
                        } else {
                            // Normal case
                            addValue(name, value, type, false);
                        }
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

            private void nameValues(String nameValues, String type, boolean nameDecrypted) {
                try {
                    int position = 0;
                    int separator;
                    while (true) {
                        // Get path
                        separator = nameValues.indexOf('^', position);
                        String decodedPath = nameValues.substring(position, separator);
                        position = separator + 1;

                        // Get value
                        separator = nameValues.indexOf('^', position);
                        String encodedValue = separator == -1 ?  nameValues.substring(position)
                                : nameValues.substring(position, separator);
                        String decodedValue = URLDecoder.decode(encodedValue, NetUtils.DEFAULT_URL_ENCODING);
                        position = separator + 1;
                        addValue(decodedPath, decodedValue, type, nameDecrypted);
                        if (separator == -1) break;
                    }
                } catch (UnsupportedEncodingException e) {
                    throw new OXFException(e);
                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }

            private void filename(String filename) {
                if (name.startsWith("$upload^"))
                    getFileInfo(name.substring("$upload^".length(), name.indexOf("^", "$upload^".length()))).filename = filename;
            }

            private void contentType(String contentType) {
                if (name.startsWith("$upload^"))
                    getFileInfo(name.substring("$upload^".length(), name.indexOf("^", "$upload^".length()))).mediatype = contentType;
            }

            private void contentLength(String contentLength) {
                if (name.startsWith("$upload^"))
                    getFileInfo(name.substring("$upload^".length(), name.indexOf("^", "$upload^".length()))).contentLength = contentLength;
            }

            public void endDocument() {
                // Complete handling of file uploads
                if (fileInfos != null) {
                    for (Iterator i = fileInfos.keySet().iterator(); i.hasNext();) {
                        String key = (String) i.next();
                        FileInfo fileInfo = (FileInfo) fileInfos.get(key);
                        if (fileInfo.hasValue()) {
                            // If a file was in fact uploaded, set all the file attributes
                            setValue(fileInfo.fileRef, fileInfo.value, fileInfo.type);
                            if (fileInfo.filenameRef != null)
                                setValue(fileInfo.filenameRef, fileInfo.filename, null);
                            if (fileInfo.mediatypeRef != null)
                                setValue(fileInfo.mediatypeRef, fileInfo.mediatype, null);
                            if (fileInfo.contentLengthRef != null)
                                setValue(fileInfo.contentLengthRef, fileInfo.contentLength, null);
                        } else {
                            // Set original value and empty attributes
                            setValue(fileInfo.fileRef, fileInfo.originalValue, null);
                            if (fileInfo.filenameRef != null)
                                setValue(fileInfo.filenameRef, "", null);
                            if (fileInfo.mediatypeRef != null)
                                setValue(fileInfo.mediatypeRef, "", null);
                            if (fileInfo.contentLengthRef != null)
                                setValue(fileInfo.contentLengthRef, "", null);
                        }
                    }
                }
            }
        };
    }

    public String[] getPaths() {
        Set paths = pathToValue.keySet();
        return (String[]) paths.toArray(new String[paths.size()]);
    }

    public String getValue(String path) {
        return (String) pathToValue.get(path);
    }

    public String getType(String path) {
        return (String) pathToType.get(path);
    }

    public boolean isSubmitted() {
        return submitted;
    }

    public Action[] getActions() {
        return (Action[]) actions.toArray(new Action[actions.size()]);
    }

    public Map getIdToRef() {
        return idToRef;
    }
}
