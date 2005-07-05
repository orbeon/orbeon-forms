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
<fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format"
         xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
         xmlns:xs="http://www.w3.org/2001/XMLSchema"
         xmlns:xdt="http://www.w3.org/2004/07/xpath-datatypes"
         xmlns:xhtml="http://www.w3.org/1999/xhtml"
         xsl:version="2.0">

    <xsl:variable name="dark" select="'#4c6c8f'"/>
    <xsl:variable name="light-odd" select="'#cfdced'"/>
    <xsl:variable name="light-even" select="'#bfccdd'"/>

    <fo:layout-master-set>
        <fo:simple-page-master margin-right=".5in" margin-left=".5in" margin-bottom=".5in"
            margin-top=".5in" page-width="8.5in" page-height="11in" master-name="page">
            <fo:region-body margin-bottom="0in" margin-top="0in"/>
            <fo:region-after display-align="before" extent=".5in" region-name="footer"/>
        </fo:simple-page-master>
        <fo:page-sequence-master master-name="report">
            <fo:repeatable-page-master-alternatives>
                <fo:conditional-page-master-reference master-reference="page"/>
            </fo:repeatable-page-master-alternatives>
        </fo:page-sequence-master>
    </fo:layout-master-set>
    <fo:page-sequence master-reference="report">
        <fo:title>Report</fo:title>
        <fo:static-content flow-name="footer">
            <fo:block text-align="center" padding-before="6pt" border-top="0.25pt solid"/>
            <fo:block font-size="10pt" text-align="start">Page <fo:page-number/></fo:block>
        </fo:static-content>
        <fo:flow flow-name="xsl-region-body">
            <!-- Title -->
            <fo:block font-size="14pt" padding-after="20pt" text-align="center">
                <xsl:value-of select="/xhtml:html/xhtml:body/xhtml:h1"/>
            </fo:block>

            <!-- Date -->
            <fo:block font-size="12pt" padding-after="40pt" text-align="center">
                <xsl:text>Generated on </xsl:text>
                <xsl:value-of select="format-dateTime(adjust-dateTime-to-timezone(current-dateTime(), xdt:dayTimeDuration('PT0H')), '[MNn] [D], [Y] [H01]:[m01]:[s01] UTC', 'en', (), ())"/>
            </fo:block>

            <!-- Simple table listing the product categories -->
            <fo:table>
                <fo:table-column column-width="1in"/>
                <fo:table-column column-width="1.5in"/>
                <fo:table-column column-width="3.5in"/>
                <fo:table-column column-width="1in"/>
                <fo:table-body>
                    <!-- Column headers -->
                    <fo:table-row background-color="#4c6c8f">
                        <xsl:for-each select="/xhtml:html/xhtml:body/xhtml:table/xhtml:tr/xhtml:th">
                            <fo:table-cell>
                                 <fo:block text-align="center" color="white">
                                     <xsl:value-of select="."/>
                                 </fo:block>
                            </fo:table-cell>
                        </xsl:for-each>
                    </fo:table-row>
                    <!-- Rows -->
                    <xsl:for-each select="/xhtml:html/xhtml:body/xhtml:table/xhtml:tr[position() > 1]">
                        <xsl:variable name="background-color">
                            <xsl:choose>
                                <xsl:when test="position() mod 2 = 0">
                                    <xsl:value-of select="$light-odd"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:value-of select="$light-even"/>
                                </xsl:otherwise>
                            </xsl:choose>
                        </xsl:variable>
                        <fo:table-row background-color="{$background-color}">
                            <xsl:for-each select="xhtml:td">
                                <fo:table-cell number-rows-spanned="{@rowspan}"
                                    padding-before="3pt" padding-after="3pt" padding-start="2pt" padding-end="2pt">
                                     <fo:block text-align="left">
                                         <xsl:value-of select="."/>
                                     </fo:block>
                                </fo:table-cell>
                            </xsl:for-each>
                        </fo:table-row>
                    </xsl:for-each>
                </fo:table-body>
            </fo:table>
        </fo:flow>
    </fo:page-sequence>
</fo:root>
