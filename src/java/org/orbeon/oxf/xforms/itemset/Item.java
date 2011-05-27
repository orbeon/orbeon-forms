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
package org.orbeon.oxf.xforms.itemset;

import org.apache.commons.lang.StringUtils;
import org.dom4j.QName;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.controls.XFormsCaseControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Represents an item (xforms:item, xforms:choice, or item in itemset).
 */
public class Item implements ItemContainer {

    private final boolean isEncryptValue; // whether this item is part of an open selection control
    private final Map<QName, String> attributes;
    private final Label label;
    private final String value;

    private int level;

    private ItemContainer parent;
    private List<Item> children;

    public Item(boolean isMultiple, boolean isEncryptValue, Map<QName, String> attributes, Label label, String value) {

        // NOTE: As of 2010-08-18, label can be null in these cases:
        //
        // o xforms:choice with (see XFormsUtils.getElementValue())
        //   o single-node binding that doesn't point to an acceptable item
        //   o value attribute but the evaluation context is empty
        //   o exception when dereferencing an @src attribute
        // o xforms|input:xxforms-type(xs:boolean)

        // Value is encrypted if requested, except with single selection if the value
        this.isEncryptValue = isEncryptValue && (isMultiple || StringUtils.isNotEmpty(value));

        this.attributes = attributes;
        this.label = label;
        this.value = value;
    }

    void setLevel(int level) {
        this.level = level;
    }

    public void pruneNonRelevantChildren() {
        if (hasChildren()) {
            // Depth-first search
            for (Iterator<Item> i = children.iterator(); i.hasNext();) {
                final Item item = i.next();
                item.pruneNonRelevantChildren();
                if (!item.hasChildren() && item.getValue() == null) {
                    // Leaf item with null value must be pruned
                    i.remove();
                }
            }
        }
    }

    public void addChildItem(Item childItem) {
        if (children == null) {
            children = new ArrayList<Item>();
        }
        childItem.setLevel(level + 1);
        children.add(childItem);
        childItem.parent = this;
    }

    void setParent(ItemContainer parent) {
        this.parent = parent;
    }

    public ItemContainer getParent() {
        return parent;
    }

    public List<Item> getChildren() {
        return children;
    }

    public boolean hasChildren() {
        return children != null && children.size() > 0;
    }

    public Map<QName, String> getAttributes() {
        return attributes;
    }

    public Label getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public String getExternalValue() {
        return value == null ? "" : isEncryptValue ? XFormsItemUtils.encryptValue(value) : value;
    }

    public String getExternalJSValue() {
        return value == null ? "" : isEncryptValue ? XFormsItemUtils.encryptValue(value) : XFormsUtils.escapeJavaScript(value);
    }

    public String getExternalJSLabel(final LocationData locationData) {
        return label == null? "" : XFormsUtils.escapeJavaScript(label.isHTML() ? XFormsCaseControl.getEscapedHTMLValue(locationData, label.getLabel()) : XMLUtils.escapeXMLMinimal(label.getLabel())); //TODO check if we need to rewrite URLs?
    }

    /**
     * Return the level of the item in the items hierarchy. The top-level is 0.
     *
     * @return  level
     */
    protected int getLevel() {
        return level;
    }

    /**
     * Whether this item is a top-level item.
     *
     * @return  true iif item is top-level
     */
    public boolean isTopLevel() {
        return level == 0;
    }

    /**
     * Visit the item and its descendants.
     *
     * @param contentHandler        optional ContentHandler object, or null if not used
     * @param listener              TreeListener to call back
     */
    void visit(ContentHandler contentHandler, ItemsetListener listener) throws SAXException {
        if (hasChildren()) {
            listener.startLevel(contentHandler, this);
            boolean first = true;
            for (final Item item : children) {
                listener.startItem(contentHandler, item, first);
                item.visit(contentHandler, listener);
                listener.endItem(contentHandler, item);
                first = false;
            }
            listener.endLevel(contentHandler);
        }
    }

    void addToList(List<Item> result) {
        result.add(this);
        if (children != null) {
            for (final Item item: children) {
                item.addToList(result);
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof Item))
            return false;

        final Item other = (Item) obj;

        // Compare value
        if (!XFormsUtils.compareStrings(value, other.value))
            return false;

        // Compare label
        if (!XFormsUtils.compareStrings(label, other.label))
            return false;

        // Compare attributes
        if (!XFormsUtils.compareMaps(attributes, other.attributes))
            return false;

        // Compare children
        if (!XFormsUtils.compareCollections(children, other.children))
            return false;

        return true;
    }

    @Override
    public String toString() {

        final StringBuilder sb = new StringBuilder();

        sb.append(getLevel());
        sb.append(" ");
        for (int i = 0; i < getLevel(); i++)
            sb.append("  ");
        sb.append(getLabel());
        sb.append(" => ");
        sb.append(getValue());

        return sb.toString();
    }

    public static class Label {

        private final String label;
        private final boolean isHTML;

        public Label(String label, boolean isHTML) {
            this.label = label;
            this.isHTML = isHTML;
        }

        public String getLabel() {
            return label;
        }

        public boolean isHTML() {
            return isHTML;
        }

        public void streamAsHTML(ContentHandlerHelper ch, LocationData locationData) {
            if (isHTML) {
                XFormsUtils.streamHTMLFragment(ch.getXmlReceiver(), label, locationData, "");
            } else {
                ch.text(StringUtils.defaultString(label));
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (isHTML ? 1231 : 1237);
            result = prime * result + ((label == null) ? 0 : label.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;

            final Label other = (Label) obj;
            return isHTML == other.isHTML && XFormsUtils.compareStrings(label, other.label);
        }
    }
}
