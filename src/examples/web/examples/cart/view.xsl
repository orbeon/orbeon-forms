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
<xhtml:html xsl:version="2.0"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xmlns:f="http://orbeon.org/oxf/xml/formatting"
            xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xmlns:xforms="http://www.w3.org/2002/xforms">
    <xhtml:head><xhtml:title>Shopping Cart</xhtml:title></xhtml:head>
    <xhtml:body>
        <xforms:group ref="form">
            <xhtml:p>
                Enter an item:
                <xforms:input ref="text"/>
                <xsl:text>&#160;</xsl:text>
                <xforms:submit>
                    <xforms:label>Add to cart</xforms:label>
                    <xforms:setvalue ref="action">add</xforms:setvalue>
                </xforms:submit>
                <xforms:submit>
                    <xforms:label>Empty cart</xforms:label>
                    <xforms:setvalue ref="action">clear</xforms:setvalue>
                </xforms:submit>
            </xhtml:p>
        </xforms:group>
        <xhtml:ol>
          <xsl:for-each select="/cart/item">
            <li><xsl:value-of select="."/></li>
          </xsl:for-each>
        </xhtml:ol>
    </xhtml:body>
</xhtml:html>
