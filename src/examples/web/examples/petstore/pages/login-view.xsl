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
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:i18n="http://www.example.com/i18n"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <xsl:template match="/">
        <root>
            <title>Sign On</title>
            <body>
                <page-title><i18n:text key="sign-in"/></page-title>
                <xforms:group ref="form">
                <box-table>
                    <xsl:if test="/root/failure">
                        <tr>
                            <td class="petstore_form" colspan="2"><font color="red">
                                <i18n:text key="login-failed"/>
                            </font></td>
                        </tr>
                    </xsl:if>
                    <tr>
                        <td class="petstore_form" align="right">
                            <b><i18n:text key="username"/></b>
                        </td>
                        <td class="petstore_form"><xforms:input ref="login" class="login"/></td>
                    </tr>
                    <tr>
                        <td class="petstore_form" align="right"><b><i18n:text key="password"/></b></td>
                        <td class="petstore_form"><xforms:secret ref="password" class="login"/></td>
                    </tr>
                    <tr>
                        <td align="center" colspan="2">
                            <xforms:submit id="login">
                                <xforms:label>sign-in</xforms:label>
                                <xforms:setvalue ref="action">login</xforms:setvalue>
                                <xforms:setvalue ref="path-info"><xsl:value-of select="/root/form/path-info"/></xforms:setvalue>
                            </xforms:submit>
                        </td>
                    </tr>
                </box-table>
                </xforms:group>
            </body>
        </root>
    </xsl:template>
</xsl:stylesheet>
