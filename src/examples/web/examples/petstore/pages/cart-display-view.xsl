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
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:d="http://orbeon.org/oxf/xml/document"
    xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml">
    <xsl:template match="/">
        <root>
            <title><i18n:text key="cart"/></title>
            <body>
                <page-title>
                    <xsl:choose>
                        <xsl:when test="count(/cart/item) = 0"><i18n:text key="cart-empty"/></xsl:when>
                        <xsl:otherwise><i18n:text key="your-cart"/></xsl:otherwise>
                    </xsl:choose>
                </page-title>
                <xsl:if test="count(/cart/item) > 0">
                    <xforms:group ref="form">
                        <box-table>
                            <!-- List products in cart -->
                            <xsl:for-each select="/cart/item">
                                <tr>
                                    <!-- Item name -->
                                    <td width="50%" class="petstore_listing">
                                        <a href="item?form/item-id={@id}">
                                            <xsl:value-of select="@name"/>
                                        </a>
                                    </td>
                                    <!-- Remove link -->
                                    <td class="petstore_listing">
                                        <a href="cart-remove?form/item-id={@id}" d:url-type="action"><i18n:text key="remove"/></a>
                                    </td>
                                    <!-- Quantity text field -->
                                    <td class="petstore_listing" align="right">
                                        <xforms:input ref="item[@id = '{@id}']" class="quantity"/>
                                    </td>
                                    <!-- Price for item -->
                                    <td class="petstore_listing" align="right">
                                        <xsl:text> @ </xsl:text><i18n:text key="currency"/>
                                        <xsl:value-of select="@price"/>
                                    </td></tr>
                            </xsl:for-each>
                            <!-- Total -->
                            <tr>
                                <td class="petstore_listing" colspan="2">
                                    <xforms:submit>
                                        <xforms:label>update-cart</xforms:label>
                                    </xforms:submit>
                                </td>
                                <td class="petstore_listing" align="right"><b><i18n:text key="subtotal"/></b></td>
                                <td bgcolor="#ccccff" class="petstore_listing" align="right">
                                    <i18n:text key="currency"/>
                                    <xsl:value-of select="/cart/@total"/>
                                </td>
                            </tr>
                        </box-table>
                        <checkout><a href="checkout"><i18n:text key="proceed-checkout"/></a></checkout>
                    </xforms:group>
                </xsl:if>
            </body>
        </root>
    </xsl:template>
</xsl:stylesheet>
