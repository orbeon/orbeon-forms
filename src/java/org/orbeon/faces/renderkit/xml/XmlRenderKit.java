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

import javax.faces.render.RenderKit;
import javax.faces.render.Renderer;
import java.util.HashMap;
import java.util.Map;

public class XmlRenderKit extends RenderKit {

    public static final String XML_RENDERKIT_ID = "org.orbeon.faces.renderkit.xml";

    private Map renderersByRendererType = new HashMap();

    /**
     * Initialize all the renderers.
     */
    public XmlRenderKit() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        for (int i = 0; i < config.length; i += 2) {
            String rendererType = config[i + 0];
            String rendererClassName = config[i + 1];

            try {
                Class rendererClass = (classLoader == null) ? Class.forName(rendererClassName) : classLoader.loadClass(rendererClassName);
                Object rendererInstance = rendererClass.newInstance();
                renderersByRendererType.put(rendererType, rendererInstance);
            } catch (Exception e) {
                throw new RuntimeException("Cannot instanciate class: " + rendererClassName);
            }
        }
    }

    private static String[] config = {
        "Button",
        "org.orbeon.faces.renderkit.xml.CommandButtonRenderer",

        "Hyperlink",
        "org.orbeon.faces.renderkit.xml.CommandHyperlinkRenderer",

        "Form",
        "org.orbeon.faces.renderkit.xml.FormRenderer",

        "Image",
        "org.orbeon.faces.renderkit.xml.GraphicImageRenderer",

        "Hidden",
        "org.orbeon.faces.renderkit.xml.InputHiddenRenderer",

        "Date",
        "org.orbeon.faces.renderkit.xml.InputOutputDateRenderer",

        "DateTime",
        "org.orbeon.faces.renderkit.xml.InputOutputDateTimeRenderer",

        "Number",
        "org.orbeon.faces.renderkit.xml.InputOutputNumberRenderer",

        "Text",
        "org.orbeon.faces.renderkit.xml.InputOutputTextRenderer",

        "Time",
        "org.orbeon.faces.renderkit.xml.InputOutputTimeRenderer",

        "Secret",
        "org.orbeon.faces.renderkit.xml.InputSecretRenderer",

        "Textarea",
        "org.orbeon.faces.renderkit.xml.InputTextareaRenderer",

        "Errors",
        "org.orbeon.faces.renderkit.xml.OutputErrorsRenderer",

        "Label",
        "org.orbeon.faces.renderkit.xml.OutputLabelRenderer",

        "Message",
        "org.orbeon.faces.renderkit.xml.OutputMessageRenderer",

        "Data",
        "org.orbeon.faces.renderkit.xml.PanelDataRenderer",

        "Grid",
        "org.orbeon.faces.renderkit.xml.PanelGridRenderer",

        "Group",
        "org.orbeon.faces.renderkit.xml.PanelGroupRenderer",

        "List",
        "org.orbeon.faces.renderkit.xml.PanelListRenderer",

        "Checkbox",
        "org.orbeon.faces.renderkit.xml.SelectbooleanCheckboxRenderer",

        "SelectManyCheckbox",
        "org.orbeon.faces.renderkit.xml.SelectmanyCheckboxRenderer",

        "Listbox",
        "org.orbeon.faces.renderkit.xml.SelectmanySelectoneListboxRenderer",

        "Menu",
        "org.orbeon.faces.renderkit.xml.SelectmanySelectoneMenuRenderer",

        "Radio",
        "org.orbeon.faces.renderkit.xml.SelectoneRadioRenderer",

        "Area",
        "org.orbeon.faces.renderkit.xml.DemoMapAreaRenderer"
    };

    public void addRenderer(String rendererType, Renderer renderer) {
        if (rendererType == null || renderer == null)
            throw new NullPointerException();

        if (renderersByRendererType.get(rendererType) != null) {
            throw new IllegalArgumentException();
        } else {
            renderersByRendererType.put(rendererType, renderer);
        }
    }

    public Renderer getRenderer(String rendererType) {
        if (rendererType == null)
            throw new NullPointerException();
        return (Renderer) renderersByRendererType.get(rendererType);
    }
}
