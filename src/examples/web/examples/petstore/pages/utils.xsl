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

    <!-- Compute the name of an item -->
    <xsl:template name="item-name">
        <xsl:param name="database"/>
        <xsl:param name="id"/>

        <xsl:variable name="item" select="$database/Catalog/Items/Item[@id = $id]"/>
        <xsl:variable name="product" select="$database/Catalog/Products/Product[@id = $item/@product]"/>
        <xsl:for-each select="$item/ItemDetails/Attribute">
            <xsl:value-of select="."/>
            <xsl:text>&#160;</xsl:text>
        </xsl:for-each>
        <xsl:value-of select="$product/ProductDetails/Name"/>
    </xsl:template>

    <xsl:template name="display-currency">
        <xsl:param name="value"/>
        <xsl:param name="default" select="'-'"/>
        <xsl:choose>
            <xsl:when test="string(number($value)) != 'NaN' and $value != 0">
                <xsl:variable name="format" select="'#,##0.00'"/>
                <xsl:value-of select="format-number(round($value * 100) div 100, $format)"/>
            </xsl:when>
            <xsl:otherwise><xsl:value-of select="$default"/></xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
