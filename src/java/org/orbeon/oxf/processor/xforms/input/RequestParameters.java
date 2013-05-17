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

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.xforms.input.action.*;
import org.orbeon.oxf.processor.xforms.input.action.Set;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.XMLConstants;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.*;

public class RequestParameters {

    private static final Map actionClasses = new HashMap();

    static {
        actionClasses.put("insert", Insert.class);
        actionClasses.put("delete", Delete.class);
        actionClasses.put("setvalue", SetValue.class);
        actionClasses.put("setindex", SetIndex.class);
        actionClasses.put("set", Set.class);
    }

    private Map idToValue = new HashMap();
    private Map idToType = new HashMap();
    private List actions = new ArrayList();
    private Document instance;
    private String encryptionKey;

    public RequestParameters(PipelineContext pipelineContext, Document requestDocument) {
        try {
            List parameters = requestDocument.getRootElement().element("parameters").elements("parameter");

            if (XFormsUtils.isHiddenEncryptionEnabled()) {
                encryptionKey = OXFProperties.instance().getPropertySet().getString(XFormsConstants.XFORMS_PASSWORD_PROPERTY);
            }

            // Go through parameters
            for (Iterator i = parameters.iterator(); i.hasNext();) {
                Element parameterElement = (Element) i.next();
                String name = parameterElement.element("name").getStringValue();

                List values = new ArrayList();
                for (Iterator elementIterator = parameterElement.elementIterator("value"); elementIterator.hasNext();)
                    values.add(((Element) elementIterator.next()).getStringValue());

                final String type
                    = Dom4jUtils.qNameToexplodedQName(Dom4jUtils.extractAttributeValueQName(parameterElement.element("value"), XMLConstants.XSI_TYPE_QNAME));

                if ("$instance".equals(name)) {
                    // Un-base64 and uncompress to get XML

                    // There is only one value for $instnace
                    String encodedInstance = (String) values.get(0);
                    instance = XFormsUtils.decodeXML(pipelineContext, encodedInstance);
                } else if (name.startsWith("$upload^")) {

                    // Split encoded name
                    String s = name.substring("$upload^".length());
                    String fileName = s.substring(0, s.indexOf('-'));
                    s = s.substring(s.indexOf('-') + 1);
                    String filenameName = s.substring(0, s.indexOf('-'));
                    s = s.substring(s.indexOf('-') + 1);
                    String mediatypeName = s.substring(0, s.indexOf('-'));
                    s = s.substring(s.indexOf('-') + 1);
                    String sizeName = s;

                    // Store file in instance
                    addValue(pipelineContext, fileName, values, type);

                    // Store other information about file
                    if (filenameName.length() > 0) {
                        String filenameFromRequest = parameterElement.element("filename").getStringValue();
                        List vals = new ArrayList();
                        vals.add(filenameFromRequest);
                        addValue(pipelineContext, filenameName, vals, null);
                    }
                    if (name.startsWith("$upload^") && mediatypeName.length() > 0) {
                        String contentTypeFromRequest = parameterElement.element("content-type").getStringValue();
                        List vals = new ArrayList();
                        vals.add(contentTypeFromRequest);
                        addValue(pipelineContext, mediatypeName, vals, null);
                    }
                    if (name.startsWith("$upload^") && sizeName.length() > 0) {
                        String contentLengthFromRequest = parameterElement.element("content-length").getStringValue();
                        List vals = new ArrayList();
                        vals.add(contentLengthFromRequest);
                        addValue(pipelineContext, sizeName, vals, null);
                    }

                } else if (name.startsWith("$action^") || name.startsWith("$actionImg^")) {

                    // Image submit. If .y: ignore. If .x: remove .x at the end of name.
                    if (name.startsWith("$actionImg^")) {
                        if (name.endsWith(".y")) continue;
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
                            for (int j = 0; j < 2; j++) {
                                int firstDelimiter = actionString.indexOf('&');
                                keyValue[j] = firstDelimiter == -1 ? actionString
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
                        if (actionClass == null)
                            throw new OXFException("Cannot find implementation for action '" + actionName + "'");
                        Action action = (Action) actionClass.newInstance();
                        action.setParameters(actionParameters);
                        actions.add(action);
                    }
                } else if (name.startsWith("$node^")) {
                    addValue(pipelineContext, name, values, type);
                }
            }
        } catch (IOException e) {
            throw new OXFException(e);
        } catch (InstantiationException e) {
            throw new OXFException(e);
        } catch (IllegalAccessException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Adds (id -> value) in idToValue. If there is already a value for this id,
     * concatenates the two values by adding a space.
     * 
     * Also store the value type in idToType if present.
     */
    private void addValue(PipelineContext pipelineContext, String name, List values, String type) {
        String idString = name.substring("$node^".length());
        if (XFormsUtils.isNameEncryptionEnabled())
            idString = SecureUtils.decrypt(pipelineContext, encryptionKey, idString);
        Integer idObject = new Integer(idString);
        String currentValue = (String) idToValue.get(idObject);
        for (Iterator i = values.iterator(); i.hasNext();) {
            String value = (String) i.next();
            currentValue = currentValue == null || "".equals(currentValue) ? value :
                    "".equals(value) ? currentValue : currentValue + ' ' + value;
            idToValue.put(idObject, currentValue);
        }
        if (type != null)
            idToType.put(idObject, type);
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

    public Action[] getActions() {
        return (Action[]) actions.toArray(new Action[actions.size()]);
    }

    public Document getInstance() {
        return instance;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }
}
