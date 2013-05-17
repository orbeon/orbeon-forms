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
package org.orbeon.faces.components.demo.renderkit;

import javax.faces.component.UIComponent;
import javax.faces.component.UIForm;
import javax.faces.component.UIInput;
import javax.faces.component.UIOutput;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;

/**
 * <p>This class is intended for use with a ResultSetRenderer.</p>
 */
class ResultSetControls extends UIInput {

    public static final String TYPE = "demo.renderer.ResultSetControls";

    public static final int ACTION_NEXT = -1;
    public static final int ACTION_PREVIOUS = -2;
    public static final int ACTION_NUMBER = -3;
    public static final String CURRENT_PAGE_ATTR =
            "ResultSetRenderer.currentPage";
    public static final String ROWS_PER_PAGE_ATTR =
            "rowsPerPage";

    public static final String FORM_NUMBER_ATTR = "com.sun.faces.FormNumber";

    /**
     * The component attribute that tells where to put the user supplied
     * markup in relation to the "jump to the Nth page of results"
     * widget.
     */

    public static final String FACET_MARKUP_ORIENTATION_ATTR =
            "navFacetOrientation";

    protected UIComponent yourPanel = null;
    protected UIComponent yourData = null;
    protected ResultSetRenderer yourListRenderer = null;

    public ResultSetControls(UIComponent newPanel, UIComponent newData,
                             ResultSetRenderer newListRenderer) {
        super();
        // ParameterCheck.nonNull();
        yourPanel = newPanel;
        yourData = newData;
        yourListRenderer = newListRenderer;
    }

    public String getComponentType() {

        return (TYPE);

    }

    public boolean isValid() {
        return true;
    }

    public void decode(FacesContext context)
            throws IOException {
        String
                clientId = yourPanel.getClientId(context),
                curPage = null,
                action = null;
        int
                actionInt = 0,
                currentPage = 0;
        Map requestParameterMap = (Map) context.getExternalContext().
                getRequestParameterMap();
        action = (String) requestParameterMap.get(clientId + "_action");
        if (null != action) {
            // Assert that we have a currentPage.
            curPage = (String) requestParameterMap.get(clientId + "_curPage");
            currentPage = Integer.valueOf(curPage).intValue();

            // Assert that action's length is 1.

            switch (actionInt = Integer.valueOf(action).intValue()) {
                case ACTION_NEXT:
                    currentPage++;
                    break;
                case ACTION_PREVIOUS:
                    currentPage--;
                    // Assert 1 < currentPage
                    break;
                default:
                    currentPage = actionInt;
                    break;
            }
            yourPanel.setAttribute(CURRENT_PAGE_ATTR,
                    new Integer(currentPage));
        }
    }

    public void encodeBegin(FacesContext context) throws IOException {
        return;
    }

    public void encodeEnd(FacesContext context) throws IOException {
        ResponseWriter writer = context.getResponseWriter();

        // PENDING(edburns): render the facets
        // PENDING(edburns): make the strings localizable
        // PENDING(edburns): make this CSSable
        int currentPage = getCurrentPage();
        int totalPages = getTotalPages(context);
        String clientId = yourPanel.getClientId(context);
        writer.write("<table border=\"0\" cellpadding=\"0\" align=\"center\">");
        writer.write("<tr align=\"center\" valign=\"top\">");
        writer.write("<td><font size=\"-1\">Result&nbsp;Page:&nbsp;</font></td>");

        // write the Previous link if necessary
        writer.write("<td>");
        writeNavWidgetMarkup(context, clientId, ACTION_PREVIOUS,
                (1 < currentPage));
        // last arg is true iff we're not the first page
        writer.write("</td>");


        //
        // render the page navigation links
        //
        int
                i = 0,
                first = 1,
                last = totalPages;

        if (10 < currentPage) {
            first = currentPage - 10;
        }
        if ((currentPage + 9) < totalPages) {
            last = currentPage + 9;
        }
        for (i = first; i <= last; i++) {
            writer.write("<td>");
            writeNavWidgetMarkup(context, clientId, i, (i != currentPage));
            writer.write("</td>");
        }

        // write the Next link if necessary
        writer.write("<td>");
        writeNavWidgetMarkup(context, clientId, ACTION_NEXT,
                (currentPage < totalPages));
        writer.write("</td>");
        writer.write("</tr>");
        writer.write(getHiddenFields(clientId));
        writer.write("</table>");

    }

    public boolean getRendersChildren() {
        return true;
    }

    /**

     * Write the markup to render a navigation widget.  Override this to
     * replace the default navigation widget of hyperlink with something
     * else.

     */

    protected void writeNavWidgetMarkup(FacesContext context,
                                        String clientId,
                                        int navActionType,
                                        boolean enabled) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        String
                facetOrientation = ResultSetRenderer.NORTH,
                facetName = null,
                linkText = null,
                localLinkText = null;
        UIComponent facet = null;
        boolean
                isCurrentPage = false,
                isPageNumber = false;

        // Assign values for local variables based on the navActionType
        switch (navActionType) {
            case ACTION_NEXT:
                facetName = "next";
                linkText = "Next";
                break;
            case ACTION_PREVIOUS:
                facetName = "previous";
                linkText = "Previous";
                break;
            default:
                facetName = "number";
                linkText = "" + navActionType;
                isPageNumber = true;
                // heuristic: if navActionType is number, and we are not
                // enabled, this must be the current page.
                if (!enabled) {
                    facetName = "current";
                    isCurrentPage = true;
                }
                break;
        }

        // leverage any navigation facets we have
        writer.write("\n&nbsp;");

        if (enabled) {
            writer.write("<a " + getAnchorAttrs(context, clientId,
                    navActionType) + ">");
        }

        // render the facet pertaining to this widget type in the NORTH
        // and WEST cases.
        if (null != (facet = yourPanel.getFacet(facetName))) {
            // If we're rendering a "go to the Nth page" link
            if (isPageNumber) {
                // See if the user specified an orientation
                String facetO;
                if (null != (facetO = (String)
                        yourPanel.getAttribute(FACET_MARKUP_ORIENTATION_ATTR))) {
                    facetOrientation = facetO;
                    // verify that the orientation is valid
                    if (!(facetOrientation.equalsIgnoreCase(ResultSetRenderer.NORTH) || facetOrientation.equalsIgnoreCase(ResultSetRenderer.SOUTH) || facetOrientation.equalsIgnoreCase(ResultSetRenderer.EAST) || facetOrientation.equalsIgnoreCase(ResultSetRenderer.WEST))) {
                        facetOrientation = ResultSetRenderer.NORTH;
                    }
                }
            }

            // output the facet as specified in facetOrientation
            if (facetOrientation == ResultSetRenderer.NORTH ||
                    facetOrientation == ResultSetRenderer.EAST) {
                facet.encodeBegin(context);
                if (facet.getRendersChildren()) {
                    facet.encodeChildren(context);
                }
                facet.encodeEnd(context);
            }
            // The difference between NORTH and EAST is that NORTH
            // requires a <br>.
            if (facetOrientation == ResultSetRenderer.NORTH) {
                writer.write("<br>");
            }
        }

        // output the link text
        try {
            if (null != (localLinkText =
                    yourListRenderer.getKeyAndLookupInBundle(context,
                            yourPanel,
                            linkText))) {
                linkText = localLinkText;
            }
        } catch (MissingResourceException e) {
        }

        // if we have a facet, only output the link text if
        // navActionType is number
        if (null != facet) {
            if (navActionType != ACTION_NEXT &&
                    navActionType != ACTION_PREVIOUS) {
                writer.write(linkText);
            }
        } else {
            writer.write(linkText);
        }

        // output the facet in the EAST and SOUTH cases
        if (null != facet) {
            if (facetOrientation == ResultSetRenderer.SOUTH) {
                writer.write("<br>");
            }
            // The difference between SOUTH and WEST is that SOUTH
            // requires a <br>.
            if (facetOrientation == ResultSetRenderer.SOUTH ||
                    facetOrientation == ResultSetRenderer.WEST) {
                facet.encodeBegin(context);
                if (facet.getRendersChildren()) {
                    facet.encodeChildren(context);
                }
                facet.encodeEnd(context);
            }
        }

        if (enabled) {
            writer.write("</a>");
        }

    }

    /**

     * <p>build and return the string consisting of the attibutes for a
     * result set navigation link anchor.</p>

     * @param context the FacesContext
     * @param clientId the clientId of the enclosing UIComponent
     * @param action the value for the rhs of the =

     * @return a String suitable for setting as the value of a navigation
     * href.

     */

    private String getAnchorAttrs(FacesContext context,
                                  String clientId,
                                  int action) {
        int formNumber = getPanelFormNumber(context, getPanelForm(context));
        String result =
                "href=\"#\" " +
                "onmousedown=\"" +
                "document.forms[" + formNumber + "]." + clientId + "_action.value='" + action + "'; " +
                "document.forms[" + formNumber + "]." + clientId + "_curPage.value='" + getCurrentPage() + "'; " +
                "document.forms[" + formNumber + "].submit()\"";

        return result;
    }

    private String getHiddenFields(String clientId) {
        String result =
                "<input type=\"hidden\" name=\"" + clientId + "_action\"/>\n" +
                "<input type=\"hidden\" name=\"" + clientId + "_curPage\"/>";

        return result;
    }

    // PENDING(edburns): avoid doing this each time called.  Perhaps
    // store in our own attr?

    protected UIForm getPanelForm(FacesContext context) {
        UIComponent parent = yourPanel.getParent();
        while (parent != null) {
            if (parent instanceof UIForm) {
                break;
            }
            parent = parent.getParent();
        }
        return (UIForm) parent;
    }

    protected int getPanelFormNumber(FacesContext context, UIForm form) {
        // If we don't have a form, return 0
        if (null == form) {
            return 0;
        }
        Integer formsInt = (Integer)
                form.getAttribute(FORM_NUMBER_ATTR);

        return formsInt.intValue();
    }

    protected int getTotalPages(FacesContext context) {
        int
                rowsPerPage = getRowsPerPage(),
                totalRows = 0,
                result = 0;
        Object value = ((UIOutput) yourData).currentValue(context);
        if (value instanceof List) {
            totalRows = ((List) value).size();
        }
        result = totalRows / rowsPerPage;
        if (0 != (totalRows % rowsPerPage)) {
            result++;
        }
        return result;
    }

    //
    // Methods called from enclosing UIPanel;
    //

    int getRowsPerPage() {
        int result = 10;
        // Set from JSP or programmatically.
        Integer currentPage = (Integer) yourPanel.getAttribute(ROWS_PER_PAGE_ATTR);
        if (null != currentPage) {
            result = currentPage.intValue();
        }
        return result;
    }

    int getCurrentPage() {
        int result = 1;
        // Set in decode()
        Integer currentPage = (Integer) yourPanel.getAttribute(CURRENT_PAGE_ATTR);
        if (null != currentPage) {
            result = currentPage.intValue();
        }
        return result;
    }

}
