/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is XML RenderKit for JSF.
 *
 * The Initial Developer of the Original Code is
 * Orbeon, Inc (info@orbeon.com)
 * Portions created by the Initial Developer are Copyright (C) 2002
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 */
package org.orbeon.faces.renderkit.xml;

import javax.faces.component.*;
import javax.faces.context.FacesContext;
import java.util.*;

/**
 * Renderer for a UISelectMany or UISelectOne Menu.
 */
public class SelectmanySelectoneMenuRenderer extends com.sun.faces.renderkit.html_basic.MenuRenderer {

    public SelectmanySelectoneMenuRenderer() {
    }

    protected void getEndTextToRender(FacesContext context, UIComponent component, String currentValue, StringBuffer buffer) {

        // Open element
        buffer.append(XmlRenderKitUtils.getStartElement(getElementName(component)));
        buffer.append(XmlRenderKitUtils.getAttribute("class", getStyleString(component)));
        buffer.append(XmlRenderKitUtils.getAttribute("id", component.getClientId(context)));

        Object layoutAttribute = component.getAttribute("layout");
        if (layoutAttribute != null)
            buffer.append(XmlRenderKitUtils.getAttribute("layout", layoutAttribute.toString()));

        // Render HTML attributes
        buffer.append(XmlRenderKitUtils.getHtmlAttributes(context, component));
        buffer.append(">");

        // Render options
        renderOptions(context, component, buffer);

        // Close element
        buffer.append(XmlRenderKitUtils.getEndElement(getElementName(component)));
    }

    protected String getElementName(UIComponent component) {
        return (component instanceof UISelectMany) ? "selectmany_menu" : "selectone_menu";
    }

    protected void renderOptions(FacesContext context, UIComponent component, StringBuffer buffer) {
        Map selectedValues = getSelectedValuesMap(context, component);

        for (Iterator i = getOptions(context, component).iterator(); i.hasNext();) {
            Option option = (Option) i.next();

            // Open element
            buffer.append(XmlRenderKitUtils.getStartElement("option"));
            buffer.append(XmlRenderKitUtils.getAttribute("value", option.getValue()));

            buffer.append(XmlRenderKitUtils.getAttribute("selected",
                    (selectedValues.get(option.getValue()) != null) ? "true" : "false"));

            // Render HTML attributes
            buffer.append(XmlRenderKitUtils.getHtmlAttributes(context, option.getComponent()));
            buffer.append(">");

            // Label
            String label = option.getLabel();
            if (label != null) {
                buffer.append(XmlRenderKitUtils.getStartElement("label"));
                buffer.append(">");
                buffer.append(label);
                buffer.append(XmlRenderKitUtils.getEndElement("label"));
            }
            // Close element
            buffer.append(XmlRenderKitUtils.getEndElement("option"));
        }
    }

    private static class Option {
        private String value;
        private String label;
        private String description;
        private UIComponent component;

        public Option(UIComponent component, SelectItem selectItem) {
            this.component = component;
            this.value = selectItem.getValue().toString();
            this.label = selectItem.getLabel();
            this.description = selectItem.getDescription();
        }

        public Option(UIComponent component, String value, String label, String description) {
            this.component = component;
            this.value = value;
            this.label = label;
            this.description = description;
        }

        public UIComponent getComponent() {
            return component;
        }

        public String getDescription() {
            return description;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Find all SelectItem children of the given component, and return a list of
     * options.
     */
    public static List getOptions(FacesContext context, UIComponent component) {
        List result = new ArrayList();
        for (Iterator children = component.getChildren(); children.hasNext();) {
            UIComponent child = (UIComponent) children.next();
            if (child instanceof UISelectItem) {
                // UISelectItem (spec section 4.1.9)
                UISelectItem selectItem = (UISelectItem) child;
                // The value can be null or a SelectItem
                SelectItem value = (SelectItem) selectItem.currentValue(context);
                result.add((value == null) ?
                        new Option(child, selectItem.getItemValue(), selectItem.getItemLabel(), selectItem.getItemDescription()) :
                        new Option(child, value));
            } else if (child instanceof UISelectItems) {
                // UISelectItems (spec section 4.1.10)
                UISelectItems selectItems = (UISelectItems) child;
                // The value can be a SelectItem, an Array of SelectItem, a Collection or a Map
                Object value = selectItems.currentValue(context);
                if (value instanceof SelectItem) {
                    result.add(new Option(child, (SelectItem) child));
                } else if (value instanceof SelectItem[]) {
                    SelectItem items[] = (SelectItem[]) value;
                    for (int i = 0; i < items.length; i++)
                        result.add(new Option(child, items[i]));
                } else if (value instanceof Collection) {
                    Collection items = (Collection) value;
                    for (Iterator elements = items.iterator(); elements.hasNext();) {
                        SelectItem item = (SelectItem) elements.next();
                        result.add(new Option(child, item));
                    }
                } else if (value instanceof Map) {
                    Map map = (Map) value;
                    for (Iterator keySet = map.keySet().iterator(); keySet.hasNext();) {
                        Object entry = keySet.next();
                        String selectItemLabel = entry.toString();
                        String selectItemValue = map.get(selectItemLabel).toString();
                        if (selectItemValue != null)
                            result.add(new Option(child, new SelectItem(selectItemValue, selectItemLabel, null)));
                    }
                } else {
                    throw new IllegalArgumentException();
                }
            }
        }
        return result;
    }

    /**
     * Return a map of currently selected values for the component.
     */
    protected Map getSelectedValuesMap(FacesContext context, UIComponent component) {
        Map map = new HashMap();
        UIInput select = (UIInput) component;
        if (select instanceof UISelectMany) {
            Object[] values = (Object[]) select.currentValue(context);
            if (values != null) {
                for (int i = 0; i < values.length; i++) {
                    String value = values[i].toString();
                    map.put(value, value);
                }
            }
        } else {
            Object value = select.currentValue(context);
            if (value != null) {
                String stringValue = value.toString();
                map.put(stringValue, stringValue);
            }
        }
        return map;
    }
}
