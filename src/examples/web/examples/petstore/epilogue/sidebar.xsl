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
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:i18n="http://www.example.com/i18n"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <xsl:template match="/">
        <table border="0" width="100%" cellpadding="1" cellspacing="0">
            <tr>
                <td bgcolor="#336666" class="petstore_title" align="center">
                    <font color="#FFFFFF"><i18n:text key="pets"/></font>
                </td>
            </tr>
            <tr>
                <td bgcolor="#336666">
                    <table border="0" width="100%" cellpadding="5" cellspacing="1">
                        <tr>
                            <td bgcolor="#FFFFFF" class="petstore">
                                <xsl:for-each select="/Categories/Category">
                                    <xforms:submit xxforms:appearance="link">
                                        <xforms:caption><xsl:value-of select="CategoryDetails/Name"/></xforms:caption>
                                        <xforms:setvalue ref="/petstore/category"><xsl:value-of select="id"/></xforms:setvalue>
                                    </xforms:submit>
                                    <br/>
                                </xsl:for-each>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
    </xsl:template>
</xsl:stylesheet>
