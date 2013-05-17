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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param name="letter" type="input"/>
    <p:param name="countries" type="output"/>

    <p:processor name="oxf:xslt">
        <p:input name="data"><dummy/></p:input>
        <p:input name="letter" href="#letter"/>
        <p:input name="countries" href="/init-database/countries.xml"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" exclude-result-prefixes="xhtml oxf xs p">
                <xsl:template match="/">
                    <xsl:variable name="countries" as="element()*"
                        select="doc('input:countries')/countries/country
                            [starts-with(name, doc('input:letter'))]"/>
                    <xsl:choose>
                        <xsl:when test="count($countries) = 0">
                            <p>No country starts with the selected letter</p>
                        </xsl:when>
                        <xsl:otherwise>
                            <ul>
                                <xsl:for-each select="$countries">
                                    <li><xsl:value-of select="name"/></li>
                                </xsl:for-each>
                            </ul>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="countries"/>
    </p:processor>

</p:config>
