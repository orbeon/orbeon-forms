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

    <xsl:template match="box-table">
        <p><table border="0" width="100%" cellpadding="1" cellspacing="0">
                <tr><td bgcolor="#808080"><table width="100%" cellspacing="0" cellpadding="2" border="0" bgcolor="#FFFFFF">
                    <xsl:for-each select="tr">
                        <tr>
                            <xsl:for-each select="td">
                                <td class="petstore_listing">
                                    <xsl:copy-of select="@*"/>
                                    <xsl:apply-templates/>
                                </td>
                            </xsl:for-each>
                        </tr>
                    </xsl:for-each>
                </table></td></tr>
        </table></p>
    </xsl:template>

    <xsl:template match="home">
        <!--
        <map name="petmap">
            <area href="/petstore/category?category-id[1]=BIRDS"
                alt="Birds"
                coords="72,2,280,250"/>
            <area href="/petstore/category?category-id[1]=FISH"
                alt="Fish"
                coords="2,180,72,250"/>
            <area href="/petstore/category?category-id[1]=DOGS"
                alt="Dogs"
                coords="60,250,130,320"/>
            <area href="/petstore/category?category-id[1]=REPTILES"
                alt="Reptiles"
                coords="140,270,210,340"/>
            <area href="/petstore/category?category-id[1]=CATS"
                alt="Cats"
                coords="225,240,295,310"/>
            <area href="/petstore/category?category-id[1]=BIRDS"
                alt="Birds"
                coords="280,180,350,250"/>
        </map>

        -->
        <img src="/examples/petstore/images/splash.gif"
            alt="Pet Selection Map"
            usemap="#petmap"
            width="350"
            height="355"
            border="0"/>
    </xsl:template>

    <xsl:template match="page-title">
        <p class="petstore_title"><xsl:copy-of select="node()"/></p>
    </xsl:template>

    <xsl:template match="checkout">
        <p class="petstore_listing" align="right"><xsl:copy-of select="node()"/></p>
    </xsl:template>

    <xsl:template match="input[@class = 'quantity']">
        <input size="3" maxlength="3">
            <xsl:copy-of select="@*[name() != 'quantity']"/>
            <xsl:apply-templates/>
        </input>
    </xsl:template>

    <xsl:template match="input[@type = 'submit']">
        <input>
            <xsl:attribute name="value" namespace="http://www.example.com/i18n">
                <xsl:value-of select="@value"/>
            </xsl:attribute>
            <xsl:copy-of select="@*[name() != 'value']"/>
            <xsl:apply-templates/>
        </input>
    </xsl:template>

    <xsl:template match="@*|node()" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>