<!--
    Copyright (C) 2004 Orbeon, Inc.
  
    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.
  
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.
  
    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<xhtml:html xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xmlns:xforms="http://www.w3.org/2002/xforms"
            xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
            xmlns:f="http://orbeon.org/oxf/xml/formatting"
            xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xsl:version="2.0">
    <xhtml:head>
        <xhtml:title>Report</xhtml:title>
    </xhtml:head>
    <xhtml:body>
        <xforms:group>
            <xhtml:p>
                Run the report now, and view it in one of the formats below:
                <xhtml:ul>
                    <xhtml:li>
                        <xforms:submit xxforms:appearance="link">
                            <xforms:label>XML in hierarchical format</xforms:label>
                            <xforms:setvalue ref="/action">report-xml-hierarchy</xforms:setvalue>
                        </xforms:submit>:

                        this is the output of the SQL processor, called in <a
                        href="/goto-source/reports/xml-hierarchy.xpl"
                        f:url-type="resource">xml-hierarchy.xpl</a> and formatted in <a
                        href="/goto-source/reports/xml-hierarchy.xsl"
                        f:url-type="resource">xml-hierarchy.xsl</a>
                    </xhtml:li>
                    <xhtml:li>
                        <xforms:submit xxforms:appearance="link">
                            <xforms:label>XML in table format</xforms:label>
                            <xforms:setvalue ref="/action">report-xml-table</xforms:setvalue>
                        </xforms:submit>:

                        this is the output of the SQL processor, called in <a
                        href="/goto-source/reports/xml-table.xpl"
                        f:url-type="resource">xml-table.xpl</a> and formatted in <a
                        href="/goto-source/reports/xml-table.xsl"
                        f:url-type="resource">xml-table.xsl</a>
                    </xhtml:li>
                    <xhtml:li>
                        <xforms:submit xxforms:appearance="link">
                            <xforms:label>HTML</xforms:label>
                            <xforms:setvalue ref="/action">report-html</xforms:setvalue>
                        </xforms:submit>:

                        the basic table format formatted by <a
                        href="/goto-source/reports/generic-table.xsl"
                        f:url-type="resource">generic-table.xsl</a>
                    </xhtml:li>
                    <xhtml:li>
                        <xforms:submit xxforms:appearance="link">
                            <xforms:label>PDF</xforms:label>
                            <xforms:setvalue ref="/action">report-pdf</xforms:setvalue>
                        </xforms:submit>: the table format transformed in XSL-FO by
                        <a href="/goto-source/reports/pdf.xsl" f:url-type="resource">pdf.xsl</a>
                    </xhtml:li>
                    <xhtml:li>
                        <xforms:submit xxforms:appearance="link">
                            <xforms:label>Excel</xforms:label>
                            <xforms:setvalue ref="/action">report-excel</xforms:setvalue>
                        </xforms:submit>:

                        the table format transformed by the XSL export processor in <a
                        href="/goto-source/reports/excel.xpl" f:url-type="resource">excel.xpl</a>
                    </xhtml:li>
                </xhtml:ul>
            </xhtml:p>
        </xforms:group>
    </xhtml:body>
</xhtml:html>
