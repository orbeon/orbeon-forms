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
    xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:d="http://orbeon.org/oxf/xml/document">

    <xsl:template match="/">
        <form action="items-search">
            <table bgcolor="#FFFFFF" width="100%" cellpadding="0" cellspacing="0" border="0">
                <tr>
                    <td align="left" valign="middle">
                        <a href="./">
                            <img src="/examples/petstore/images/banner_logo_oxf.png" alt="Java Pet Store Demo logo" border="0"/>
                        </a>
                    </td>
                    <td class="petstore" align="right" valign="middle">
                        <p style="margin-bottom: 0px">
                            <input class="petstore_listing" type="text" name="form/keywords" size="8"/>
                            <input class="petstore_listing" type="submit" value="search"/>
                        </p>
                        <p style="margin-top: 5px; margin-bottom: 0px">
                            <a href="set-locale?form/locale=en_US" d:url-type="action">
                                <img src="/examples/petstore/images/us_flag.gif" border="0"/>
                            </a>
                            &#160;
                            <a href="set-locale?form/locale=ja_JP" d:url-type="action">
                                <img src="/examples/petstore/images/ja_flag.gif" border="0"/>
                            </a>
                        </p>
                        <p style="margin-top: 0px">
                            <a href="cart-display"><i18n:text key="cart"/></a> |
                            <xsl:choose>
                                <xsl:when test="/logged = 'true'">
                                    <a href="logout" d:url-type="action"><i18n:text key="sign-off"/></a>
                                </xsl:when>
                                <xsl:otherwise>
                                    <a href="login"><i18n:text key="sign-in"/></a>
                                </xsl:otherwise>
                            </xsl:choose>
                            <xsl:text>&#160;&#160;&#160;&#160;</xsl:text>
                            <a href="set-target?form/target=web" d:url-type="action"><i18n:text key="web"/></a> |
                            <a href="set-target?form/target=pda" d:url-type="action"><i18n:text key="pda"/></a>
                        </p>
                    </td>
                </tr>
                <tr>
                    <td colspan="2">
                        <hr noshade="noshade" size="1"/>
                    </td>
                </tr>
            </table>
        </form>
    </xsl:template>
</xsl:stylesheet>
