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

    <p:processor name="oxf:session-generator">
      <p:input name="config"><key>cart</key></p:input>
      <p:output name="data" id="cart"/>
    </p:processor>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../database/database.xpl"/>
        <p:output name="data" id="database"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="aggregate('root', #cart, #database)"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/petstore/pages/utils.xsl"/>
                <xsl:template match="/">
                    <!-- Compute list of items -->
                    <xsl:variable name="items">
                        <xsl:for-each select="/root/cart/item">
                            <xsl:variable name="id" select="@id"/>
                            <xsl:variable name="item" select="/root/Populate/Catalog/Items/Item[@id = $id]"/>
                            <xsl:variable name="price" select="/root/Populate/Catalog/Items/Item[@id = $id]/ItemDetails/ListPrice"/>
                            <item quantity="{@quantity}" id="{@id}" price="{$price}" total-price="{@quantity * $price}">
                                <xsl:attribute name="name">
                                    <xsl:call-template name="item-name">
                                        <xsl:with-param name="database" select="/root/Populate"/>
                                        <xsl:with-param name="id" select="@id"/>
                                    </xsl:call-template>
                                </xsl:attribute>
                            </item>
                        </xsl:for-each>
                    </xsl:variable>
                    <!-- Compute total -->
                    <cart>
                        <xsl:attribute name="total">
                            <xsl:call-template name="display-currency">
                                <xsl:with-param name="value" select="sum($items/item/@total-price)"/>
                            </xsl:call-template>
                        </xsl:attribute>
                        <xsl:copy-of select="$items/*"/>
                    </cart>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
