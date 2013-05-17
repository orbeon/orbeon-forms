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
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <p:choose href="#instance">
        <p:when test="/form/sort-column != ''">
            <!-- Sort data -->
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#instance"/>
                <p:input name="config">
                    <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                        <xsl:template match="/">
                            <countries>
                                <xsl:variable name="sort-column" select="/form/sort-column"/>
                                <xsl:for-each select="document('data.xml')/countries/country">
                                    <xsl:sort select="*[name() = $sort-column]" order="{/form/sort-order}"
                                        data-type="{/form/sort-data-type}"/>
                                    <xsl:copy-of select="."/>
                                </xsl:for-each>
                            </countries>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:input>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- No sorting needed -->
            <p:processor name="oxf:identity">
                <p:input name="data" href="data.xml"/>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
