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
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="output" name="data"/>


    <p:processor name="oxf:pipeline">
        <p:input name="config" href="locale.xpl"/>
        <p:output name="locale" id="locale"/>
    </p:processor>


    <p:processor name="oxf:xslt">
        <p:input name="data" href="aggregate('root', #locale, database.xml)"/>
        <p:input name="config">
            <xsl:stylesheet version="1.0">
                <xsl:template match="/">
                    <xsl:apply-templates select="/root/Populate"/>
                </xsl:template>
                <xsl:template match="*">
                    <!-- Only keep entries for current locale -->
                    <xsl:if test="@locale = /root/locale or count(@locale) = 0">
                        <xsl:element name="{name(.)}">
                            <xsl:copy-of select="@*"/>
                            <xsl:apply-templates/>
                        </xsl:element>
                    </xsl:if>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>
</p:config>
