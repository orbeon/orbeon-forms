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
<xhtml:html xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xsl:version="2.0">

    <xhtml:head><xhtml:title>XForms Submit Controls</xhtml:title></xhtml:head>
    <xhtml:body>
        <xforms:group ref="/form">
            <table border="0" cellpadding="10" cellspacing="0">
                <tr>
                    <td align="right">Button:</td>
                    <td>
                        <xforms:submit>
                            <xforms:label>Submit</xforms:label>
                            <xforms:setvalue ref="clicked">button</xforms:setvalue>
                            <xforms:setvalue ref="taste">vanilla</xforms:setvalue>
                        </xforms:submit>
                        (set <i>clicked</i> to "button" and <i>taste</i> to "vanilla")
                    </td>
                </tr>
                <tr>
                    <td align="right">Link:</td>
                    <td>
                        <xforms:submit xxforms:appearance="link">
                            <xforms:label>Submit</xforms:label>
                            <xforms:setvalue ref="clicked">link</xforms:setvalue>
                            <xforms:setvalue ref="taste">strawberry</xforms:setvalue>
                        </xforms:submit>
                        (set <i>clicked</i> to "link" and <i>taste</i> to "strawberry")
                    </td>
                </tr>
                <tr>
                    <td align="right">Image:</td>
                    <td>
                        <xforms:submit xxforms:appearance="image">
                            <xxforms:img src="images/submit.gif"/>
                            <xforms:setvalue ref="clicked">image</xforms:setvalue>
                            <xforms:setvalue ref="taste">lemon</xforms:setvalue>
                        </xforms:submit>
                        (set <i>clicked</i> to "image" and <i>taste</i> to "lemon")
                    </td>
                </tr>
                <tr>
                    <td align="right">Message From Binding Attribute:</td>
                    <td>
                        <xforms:submit>
                            <xforms:label>Alert!</xforms:label>
                            <xforms:message ref="taste">This is an alert!</xforms:message>
                        </xforms:submit>
                        &#160;
                        <xforms:submit xxforms:appearance="link">
                            <xforms:label>Alert!</xforms:label>
                            <xforms:message ref="taste">This is an alert!</xforms:message>
                        </xforms:submit>
                        &#160;
                        <xforms:submit xxforms:appearance="image">
                            <xxforms:img src="images/submit.gif"/>
                            <xforms:label>Alert!</xforms:label>
                            <xforms:message ref="taste">This is an alert!</xforms:message>
                        </xforms:submit>
                        &#160;
                        (display an alert window with the selected taste)
                    </td>
                </tr>
                <tr>
                    <td align="right">Message From Inline Text:</td>
                    <td>
                        <xforms:submit>
                            <xforms:label>Alert!</xforms:label>
                            <xforms:message>This is an inline alert!</xforms:message>
                        </xforms:submit>
                        &#160;
                        <xforms:submit xxforms:appearance="link">
                            <xforms:label>Alert!</xforms:label>
                            <xforms:message>This is an inline alert!</xforms:message>
                        </xforms:submit>
                        &#160;
                        <xforms:submit xxforms:appearance="image">
                            <xxforms:img src="images/submit.gif"/>
                            <xforms:label>Alert!</xforms:label>
                            <xforms:message>This is an inline alert!</xforms:message>
                        </xforms:submit>
                        &#160;
                        (display an alert window with the content of the xforms:message)
                    </td>
                </tr>
                <tr>
                    <td align="right">Message From Linking Attribute:</td>
                    <td>
                        <xforms:submit>
                            <xforms:label>Alert!</xforms:label>
                            <xforms:message src="oxf:/examples/xforms/xforms-submit/message.txt"/>
                        </xforms:submit>
                        &#160;
                        <xforms:submit xxforms:appearance="link">
                            <xforms:label>Alert!</xforms:label>
                            <xforms:message src="oxf:/examples/xforms/xforms-submit/message.txt"/>
                        </xforms:submit>
                        &#160;
                        <xforms:submit xxforms:appearance="image">
                            <xxforms:img src="images/submit.gif"/>
                            <xforms:label>Alert!</xforms:label>
                            <xforms:message src="oxf:/examples/xforms/xforms-submit/message.txt"/>
                        </xforms:submit>
                        &#160;
                        (display an alert window with the content an URI)
                    </td>
                </tr>
                <tr>
                    <td align="right">XForms instance:</td>
                    <td>
                        <f:xml-source>
                            <xsl:copy-of select="/*"/>
                        </f:xml-source>
                    </td>
                </tr>
            </table>
        </xforms:group>
    </xhtml:body>
</xhtml:html>
