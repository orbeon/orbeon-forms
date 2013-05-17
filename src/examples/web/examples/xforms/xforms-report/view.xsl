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
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:dyn="http://exslt.org/dynamic"
    xmlns:str="http://exslt.org/strings"
    xmlns:f="http://orbeon.org/oxf/xml/formatting" 
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <xsl:variable name="instance" as="element()" select="doc('input:instance')/form"/>
    <xsl:variable name="display-column" as="xs:string*" select="tokenize(doc('input:instance')/form/display-column, ' ')"/>

    <xsl:template match="/">
        <xhtml:html>
            <xhtml:head><xhtml:title>XForms Sortable Table</xhtml:title></xhtml:head>
            <xhtml:body>
                <xforms:group ref="/form">
                    <xhtml:p>
                        <table border="0" cellpadding="10" cellspacing="0">
                            <tr><td>
                                <!-- Column selection -->
                                <xhtml:p>
                                    Select columns to display:
                                    <br/><br/>
                                    <xforms:select appearance="full" ref="display-column">
                                        <xforms:choices>
                                            <xforms:item>
                                                <xforms:label>Name</xforms:label>
                                                <xforms:value>name</xforms:value>
                                            </xforms:item>
                                            <br/>
                                            <xforms:item>
                                                <xforms:label>Capital</xforms:label>
                                                <xforms:value>capital</xforms:value>
                                            </xforms:item>
                                            <br/>
                                            <xforms:item>
                                                <xforms:label>Population</xforms:label>
                                                <xforms:value>population</xforms:value>
                                            </xforms:item>
                                            <br/>
                                            <xforms:item>
                                                <xforms:label>GDP</xforms:label>
                                                <xforms:value>gdp</xforms:value>
                                            </xforms:item>
                                        </xforms:choices>
                                    </xforms:select>
                                    <xhtml:p>
                                        <xforms:submit>
                                            <xforms:label>Submit</xforms:label>
                                        </xforms:submit>
                                    </xhtml:p>
                                </xhtml:p>
                            </td><td width="10"></td><td>
                                <!-- Data table -->
                                <xhtml:table class="gridtable" width="">
                                    <xsl:call-template name="sort-links">
                                        <xsl:with-param name="name">Name</xsl:with-param>
                                        <xsl:with-param name="column">name</xsl:with-param>
                                        <xsl:with-param name="data-type">text</xsl:with-param>
                                    </xsl:call-template>
                                    <xsl:call-template name="sort-links">
                                        <xsl:with-param name="name">Capital</xsl:with-param>
                                        <xsl:with-param name="column">capital</xsl:with-param>
                                        <xsl:with-param name="data-type">text</xsl:with-param>
                                    </xsl:call-template>
                                    <xsl:call-template name="sort-links">
                                        <xsl:with-param name="name">Population</xsl:with-param>
                                        <xsl:with-param name="column">population</xsl:with-param>
                                        <xsl:with-param name="data-type">number</xsl:with-param>
                                    </xsl:call-template>
                                    <xsl:call-template name="sort-links">
                                        <xsl:with-param name="name">GDP (billion $)</xsl:with-param>
                                        <xsl:with-param name="column">gdp</xsl:with-param>
                                        <xsl:with-param name="data-type">number</xsl:with-param>
                                    </xsl:call-template>
                                    <xsl:for-each select="/countries/country">
                                        <xhtml:tr>
                                            <xsl:if test="$display-column[string(.) = 'name']">
                                                <xhtml:td><xsl:value-of select="name"/></xhtml:td>
                                            </xsl:if>
                                            <xsl:if test="$display-column[string(.) = 'capital']">
                                                <xhtml:td><xsl:value-of select="capital"/></xhtml:td>
                                            </xsl:if>
                                            <xsl:if test="$display-column[string(.) = 'population']">
                                                <xhtml:td align="right"><xsl:value-of select="population"/></xhtml:td>
                                            </xsl:if>
                                            <xsl:if test="$display-column[string(.) = 'gdp']">
                                                <xhtml:td align="right"><xsl:value-of select="gdp"/></xhtml:td>
                                            </xsl:if>
                                        </xhtml:tr>
                                    </xsl:for-each>
                                </xhtml:table>
                            </td></tr>
                        </table>
                    </xhtml:p>
                    <xhtml:p>
                        <table border="0" cellpadding="10" cellspacing="0">
                            <tr>
                                <td class="bodytd">
                                    <xhtml:p>XForms instance:</xhtml:p>
                                    <xhtml:p>
                                        <f:xml-source>
                                            <xsl:copy-of select="doc('input:instance')/form"/>
                                        </f:xml-source>
                                    </xhtml:p>
                                </td>
                            </tr>
                        </table>
                    </xhtml:p>
                </xforms:group>
            </xhtml:body>
        </xhtml:html>
    </xsl:template>

    <xsl:template name="sort-links">
        <xsl:param name="name"/>
        <xsl:param name="column"/>
        <xsl:param name="data-type"/>
        <xsl:if test="$display-column[string(.) = $column]">
            <xhtml:th>
                <xsl:value-of select="$name"/>
                <xsl:text>&#160;</xsl:text>
                <xforms:submit xxforms:appearance="image">
                    <xsl:choose>
                        <xsl:when test="$instance/sort-column = $column and $instance/sort-order = 'ascending'">
                            <xxforms:img src="/images/sort-up.gif"/>
                            <xforms:setvalue ref="sort-column"><xsl:value-of select="$column"/></xforms:setvalue>
                            <xforms:setvalue ref="sort-order">descending</xforms:setvalue>
                        </xsl:when>
                        <xsl:when test="$instance/sort-column = $column and $instance/sort-order = 'descending'">
                            <xxforms:img src="/images/sort-down.gif"/>
                            <xforms:setvalue ref="sort-column"/>
                            <xforms:setvalue ref="sort-order"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xxforms:img src="/images/sort-natural.gif"/>
                            <xforms:setvalue ref="sort-column"><xsl:value-of select="$column"/></xforms:setvalue>
                            <xforms:setvalue ref="sort-order">ascending</xforms:setvalue>
                        </xsl:otherwise>
                    </xsl:choose>
                    <xforms:setvalue ref="sort-data-type"><xsl:value-of select="$data-type"/></xforms:setvalue>
                </xforms:submit>
            </xhtml:th>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>
