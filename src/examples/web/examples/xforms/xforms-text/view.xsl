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
            xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xsl:version="2.0">
    <xhtml:head><xhtml:title>XForms Text Controls</xhtml:title></xhtml:head>
    <xhtml:body>
        <xforms:group ref="/form">
            <table border="0" cellpadding="10" cellspacing="0">
                <tr>
                    <td align="right">Age:</td>
                    <td>
                        <xforms:input ref="text">
                            <xforms:alert>The age must be a positive number</xforms:alert>
                        </xforms:input>
                    </td>
                </tr>
                <tr>
                    <td align="right">Password:</td>
                    <td>
                        <xforms:secret ref="secret">
                            <xforms:help>
                                Make sure you enter a valid password.
                                <p><i>The password is 42.</i></p>
                            </xforms:help>
                            <xforms:alert>Invalid password</xforms:alert>
                        </xforms:secret>
                    </td>
                </tr>
                <tr>
                    <td align="right">Text area:</td>
                    <td>
                        <xforms:textarea ref="textarea">
                            <xforms:hint>Enter at least 10 characters</xforms:hint>
                            <xforms:alert>Content of text area has less than 10 characters</xforms:alert>
                        </xforms:textarea>
                    </td>
                </tr>
                <tr>
                    <td/>
                    <td>
                        <xforms:submit>
                            <xforms:label>Submit</xforms:label>
                        </xforms:submit>
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
