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
<xhtml:html xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xmlns:xforms="http://www.w3.org/2002/xforms"
            xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
            xmlns:f="http://orbeon.org/oxf/xml/formatting"
            xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xsl:version="2.0">
    <xhtml:head>
        <xhtml:title>Email</xhtml:title>
    </xhtml:head>
    <xhtml:body>
        <xforms:group>
            <xhtml:p>
                <xhtml:table class="gridtable">
                    <xhtml:tr>
                        <xhtml:th>SMTP Host</xhtml:th>
                        <xhtml:td colspan="3">
                            <xforms:input ref="message/smtp-host"/>
                        </xhtml:td>
                    </xhtml:tr>
                    <xhtml:tr>
                        <xhtml:th>From Email</xhtml:th>
                        <xhtml:td>
                            <xforms:input ref="message/from/email"/>
                        </xhtml:td>
                        <xhtml:th>From Name</xhtml:th>
                        <xhtml:td>
                            <xforms:input ref="message/from/name"/>
                        </xhtml:td>
                    </xhtml:tr>
                    <xhtml:tr>
                        <xhtml:th>To Email</xhtml:th>
                        <xhtml:td>
                            <xforms:input ref="message/to/email"/>
                        </xhtml:td>
                        <xhtml:th>To Name</xhtml:th>
                        <xhtml:td>
                            <xforms:input ref="message/to/name"/>
                        </xhtml:td>
                    </xhtml:tr>
                    <xhtml:tr>
                        <xhtml:th>Subject</xhtml:th>
                        <xhtml:td colspan="3">
                            <xforms:input ref="message/subject"/>
                        </xhtml:td>
                    </xhtml:tr>
                    <xhtml:tr>
                        <xhtml:th>Body</xhtml:th>
                        <xhtml:td colspan="3">
                            <xforms:textarea ref="message/body/part/html/body" xhtml:style="width: 100%" xhtml:rows="20"/>
                        </xhtml:td>
                    </xhtml:tr>
                </xhtml:table>
            </xhtml:p>
            <xhtml:p>
                <xforms:submit xxforms:appearance="button">
                    <xforms:label>Send an Email!</xforms:label>
                </xforms:submit>
            </xhtml:p>
            <xhtml:p>
                <xsl:value-of select="message"/>
            </xhtml:p>
        </xforms:group>
    </xhtml:body>
</xhtml:html>
