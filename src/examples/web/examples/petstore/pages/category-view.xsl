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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:i18n="http://www.example.com/i18n">
    <xsl:template match="/">
        <root>
            <title><i18n:text key="category-title"/></title>
            <body>
                <page-title><i18n:text key="category-title"/></page-title>
                <box-table>
                    <xsl:for-each select="/Products/Product">
                        <tr><td>
                            <a href="items-product?form/product-id={@id}"><xsl:value-of select="ProductDetails/Name"/></a>
                            <br/>
                            <xsl:value-of select="ProductDetails/Description"/>
                        </td></tr>
                    </xsl:for-each>
                </box-table>
            </body>
        </root>
    </xsl:template>
</xsl:stylesheet>
