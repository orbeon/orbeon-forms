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
    <xhtml:head><xhtml:title>Google Spell Checker</xhtml:title></xhtml:head>
    <xhtml:body>
        <xforms:group ref="form">
            <xsl:choose>
                <xsl:when test="/null">
                    <table border="0" cellpadding="10" cellspacing="0">
                        <tr>
                            <td valign="top">Enter the text to spellcheck:</td>
                            <td colspan="2">
                                <xforms:textarea ref="text" xhtml:cols="60" xhtml:rows="6"/>
                            </td>
                        </tr>
                        <tr>
                            <td/>
                            <td align="right">
                                <xforms:submit xxforms:appearance="button">
                                    <xforms:label>Spell check</xforms:label>
                                    <xforms:setvalue ref="action">check</xforms:setvalue>
                                </xforms:submit>
                            </td>
                            <td>
                                <a href="http://www.google.com/">
                                    <img src="/images/powered_by_google_135x35.gif" border="0"/>
                                </a>
                            </td>
                        </tr>
                    </table>
                </xsl:when>
                <xsl:when test="/error">
                    <xhtml:p>
                        <font color="red"><xsl:value-of select="."/></font>
                    </xhtml:p>
                </xsl:when>
                <xsl:otherwise>
                    <table border="0" cellpadding="10" cellspacing="0">
                        <tr>
                            <td>Corrected text:</td>
                            <td class="bodytd">
                                <xsl:for-each select="/correction/word">
                                    <xsl:if test="position() > 1">
                                        <xsl:text> </xsl:text>
                                    </xsl:if>
                                    <xsl:choose>
                                        <xsl:when test="@alternate">
                                            <span style="background-color: #FFCCCC"><xsl:value-of select="."/></span>
                                            <xsl:text> (</xsl:text>
                                            <i>suggestion: </i>
                                            <span style="background-color: #99FF99"><xsl:value-of select="@alternate"/></span>
                                            <xsl:text>)</xsl:text>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:value-of select="."/>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </xsl:for-each>
                            </td>
                        </tr>
                    </table>
                </xsl:otherwise>
            </xsl:choose>
        </xforms:group>
    </xhtml:body>
</xhtml:html>
