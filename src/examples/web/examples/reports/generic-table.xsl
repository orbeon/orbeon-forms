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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="/">
        <xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml">
            <xhtml:head>
                <xhtml:title>Report</xhtml:title>
            </xhtml:head>
            <xhtml:body>
                <xhtml:h1>Pet Store Inventory Report</xhtml:h1>
                <xhtml:table class="gridtable">
                    <xhtml:tr>
                        <xhtml:th>Category</xhtml:th>
                        <xhtml:th>Product</xhtml:th>
                        <xhtml:th>Item</xhtml:th>
                        <xhtml:th>List Price</xhtml:th>
                    </xhtml:tr>
                    <xsl:for-each select="/categories/category">
                        <xsl:variable name="category" select="."/>
                        <xsl:variable name="category-position" select="position()"/>
                        <xsl:variable name="category-item-count" select="count(product/item)"/>
                        <xsl:for-each select="product">
                            <xsl:variable name="product" select="."/>
                            <xsl:variable name="product-position" select="position()"/>
                            <xsl:variable name="product-item-count" select="count(item)"/>
                            <xsl:for-each select="item">
                                <xsl:variable name="item-position" select="position()"/>
                                <xhtml:tr>
                                    <xsl:if test="$product-position = 1 and $item-position = 1">
                                        <xhtml:td rowspan="{$category-item-count}"><xsl:value-of select="$category/name"/></xhtml:td>
                                    </xsl:if>
                                    <xsl:if test="$item-position = 1">
                                        <xhtml:td rowspan="{$product-item-count}"><xsl:value-of select="$product/name"/></xhtml:td>
                                    </xsl:if>
                                    <xhtml:td rowspan="1"><xsl:value-of select="name"/></xhtml:td>
                                    <xhtml:td rowspan="1">$ <xsl:value-of select="listprice"/></xhtml:td>
                                </xhtml:tr>
                            </xsl:for-each>
                        </xsl:for-each>
                    </xsl:for-each>
                </xhtml:table>
            </xhtml:body>
        </xhtml:html>
    </xsl:template>
</xsl:stylesheet>
