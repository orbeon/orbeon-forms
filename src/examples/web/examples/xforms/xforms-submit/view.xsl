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
                            <xforms:message ref="taste"/>
                        </xforms:submit>
                        (sets <i>clicked</i> to "button" and <i>taste</i> to "vanilla"; displays a message with the current value of <i>taste</i>)
                    </td>
                </tr>
                <tr>
                    <td align="right">Link:</td>
                    <td>
                        <xforms:submit xxforms:appearance="link">
                            <xforms:label>Submit</xforms:label>
                            <xforms:setvalue ref="clicked">link</xforms:setvalue>
                            <xforms:setvalue ref="taste">strawberry</xforms:setvalue>
                            <xforms:message>This is an inline alert!</xforms:message>
                        </xforms:submit>
                        (sets <i>clicked</i> to "link" and <i>taste</i> to "strawberry"; displays a message with an inline value)
                    </td>
                </tr>
                <tr>
                    <td align="right">Image:</td>
                    <td>
                        <xforms:submit xxforms:appearance="image">
                            <xxforms:img src="images/submit.gif"/>
                            <xforms:setvalue ref="clicked">image</xforms:setvalue>
                            <xforms:setvalue ref="taste">lemon</xforms:setvalue>
                             <xforms:message src="oxf:/examples/xforms/xforms-submit/message.txt"/>
                        </xforms:submit>
                        (set <i>clicked</i> to "image" and <i>taste</i> to "lemon"; displays a message from an external file)
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
