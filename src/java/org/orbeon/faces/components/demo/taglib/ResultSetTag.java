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
/*
 * The original source code by Sun Microsystems has been modifed by Orbeon,
 * Inc.
 */
package org.orbeon.faces.components.demo.taglib;

import javax.faces.component.UIComponent;
import javax.faces.webapp.FacesBodyTag;

public class ResultSetTag extends FacesBodyTag {

    private String rowsPerPage = null;

    public String getRowsPerPage() {
        return rowsPerPage;
    }

    public void setRowsPerPage(String newRowsPerPage) {
        rowsPerPage = newRowsPerPage;
    }

    private String navFacetOrientation = null;

    public String getNavFacetOrientation() {
        return navFacetOrientation;
    }

    public void setNavFacetOrientation(String newNavFacetOrientation) {
        navFacetOrientation = newNavFacetOrientation;
    }

    private String scrollerControlsLocation = null;

    public String getScrollerControlsLocation() {
        return scrollerControlsLocation;
    }

    public void setScrollerControlsLocation(String newScrollerControlsLocation) {
        scrollerControlsLocation = newScrollerControlsLocation;
    }


    private String columnClasses = null;

    public void setColumnClasses(String columnClasses) {
        this.columnClasses = columnClasses;
    }


    private String footerClass = null;

    public void setFooterClass(String footerClass) {
        this.footerClass = footerClass;
    }


    private String headerClass = null;

    public void setHeaderClass(String headerClass) {
        this.headerClass = headerClass;
    }


    private String panelClass = null;

    public void setPanelClass(String panelClass) {
        this.panelClass = panelClass;
    }


    private String rowClasses = null;

    public void setRowClasses(String rowClasses) {
        this.rowClasses = rowClasses;
    }


    public String getComponentType() {
        return ("Panel");
    }


    public String getRendererType() {
        return ("ResultSet");
    }


    public void release() {
        super.release();
        this.columnClasses = null;
        this.footerClass = null;
        this.headerClass = null;
        this.panelClass = null;
        this.rowClasses = null;
    }


    protected void overrideProperties(UIComponent component) {
        super.overrideProperties(component);
        if ((columnClasses != null) &&
                (component.getAttribute("columnClasses") == null)) {
            component.setAttribute("columnClasses", columnClasses);
        }
        if ((footerClass != null) &&
                (component.getAttribute("footerClass") == null)) {
            component.setAttribute("footerClass", footerClass);
        }
        if ((headerClass != null) &&
                (component.getAttribute("headerClass") == null)) {
            component.setAttribute("headerClass", headerClass);
        }
        if ((panelClass != null) &&
                (component.getAttribute("panelClass") == null)) {
            component.setAttribute("panelClass", panelClass);
        }
        if ((rowClasses != null) &&
                (component.getAttribute("rowClasses") == null)) {
            component.setAttribute("rowClasses", rowClasses);
        }
        if ((rowsPerPage != null) &&
                (component.getAttribute("rowsPerPage") == null)) {
            try {
                component.setAttribute("rowsPerPage",
                        Integer.valueOf(rowsPerPage));
            } catch (NumberFormatException e) {
            }
        }
        if ((navFacetOrientation != null) &&
                (component.getAttribute("navFacetOrientation") == null)) {
            component.setAttribute("navFacetOrientation", navFacetOrientation);
        }
        if ((scrollerControlsLocation != null) &&
                (component.getAttribute("scrollerControlsLocation") == null)) {
            component.setAttribute("scrollerControlsLocation",
                    scrollerControlsLocation);
        }
    }
}
