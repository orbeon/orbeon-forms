/*
 * Copyright 2002, 2003 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistribution in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials
 *   provided with the distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT OF OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THIS SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS
 * BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */

package org.orbeon.faces.example.cardemo;

import org.orbeon.faces.components.demo.components.UIMap;
import org.mozilla.util.Assert;

import javax.faces.FactoryFinder;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.event.ActionListener;
import javax.faces.event.PhaseId;
import javax.faces.tree.TreeFactory;
import java.util.Hashtable;
import java.util.Locale;

/**
 * The listener interface for handling the ActionEvent generated
 * by the map component.
 */
public class ImageMapEventHandler implements ActionListener {

    Hashtable localeTable = new Hashtable();

    public ImageMapEventHandler() {

        localeTable.put("NAmericas", Locale.ENGLISH);
        localeTable.put("SAmericas", new Locale("es", "es"));
        localeTable.put("Germany", Locale.GERMAN);
        localeTable.put("France", Locale.FRENCH);

    }

    // This listener will handle events after the phase specified
    // as the return value;

    public PhaseId getPhaseId() {
        return PhaseId.ANY_PHASE;
    }

    // Processes the event queued on the specified component.
    public void processAction(ActionEvent event) {

        UIMap map = (UIMap) event.getSource();
        String value = (String) map.getAttribute("currentArea");
        Locale curLocale = (Locale) localeTable.get(value);
        if (curLocale != null) {
            FacesContext context = FacesContext.getCurrentInstance();
            context.setLocale(curLocale);

            String treeId = "/jsf/web/cardemo/storefront.jsp";
            TreeFactory treeFactory = (TreeFactory)
                    FactoryFinder.getFactory(FactoryFinder.TREE_FACTORY);
            Assert.assert_it(null != treeFactory);
            context.setTree(treeFactory.getTree(context, treeId));
        }
    }
}
