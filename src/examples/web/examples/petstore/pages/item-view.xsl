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
            <title>Item</title>
            <body>
                <page-title>
                    <xsl:value-of select="/detail/name"/>
                </page-title>
                <p><table cellpadding="2" cellspacing="0" border="0" width="100%">
                        <tr>
                            <td>
                                <img src="images/{/detail/image}"/>
                            </td>
                            <td class="petstore" width="100%">
                                <b><i18n:text key="list-price"/></b>
                                <xsl:text>&#160;</xsl:text>
                                <i18n:text key="currency"/>
                                <xsl:value-of select="/detail/price"/>
                                <br/><br/>
                                <xsl:value-of select="/detail/description"/>
                                <br/><br/>
                                <a href="cart-add?form/item-id={/detail/id}" d:url-type="action"><i18n:text key="add-to-cart"/></a>
                            </td>
                        </tr>
                </table></p>
            </body>
        </root>
    </xsl:template>
</xsl:stylesheet>
