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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:i18n="http://www.example.com/i18n"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:d="http://orbeon.org/oxf/xml/document"
    xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <xsl:template match="/">
        <root>
            <title><i18n:text key="items-title"/></title>
            <body>
                <page-title><xsl:copy-of select="/items/title/node()"/></page-title>
                <xsl:if test="count(/items/item) > 0">
                    <box-table>
                        <xsl:for-each select="/items/item">
                            <tr>
                                <td>
                                    <a href="item?form/item-id={id}">
                                        <xsl:value-of select="name"/>
                                    </a>
                                    <br/>
                                    <xsl:value-of select="description"/>
                                </td>
                                <td align="right">
                                    <i18n:text key="currency"/>
                                    <xsl:value-of select="price"/>
                                    <br/>
                                    <a href="cart-add?form/item-id={id}" d:url-type="action"><i18n:text key="add-to-cart"/></a>
                                </td>
                            </tr>
                        </xsl:for-each>
                    </box-table>
                </xsl:if>
            </body>
        </root>
    </xsl:template>
</xsl:stylesheet>
