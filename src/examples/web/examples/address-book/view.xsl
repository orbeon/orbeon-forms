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
<xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xmlns:xforms="http://www.w3.org/2002/xforms"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xsl:version="2.0">
    <xhtml:head>
        <xhtml:title>Address Book</xhtml:title>
    </xhtml:head>
    <xhtml:body>
        <xforms:group ref="/form">
            <xhtml:table class="gridtable">
                <xhtml:tr>
                    <xhtml:th>First Name</xhtml:th>
                    <xhtml:th>Last Name</xhtml:th>
                    <xhtml:th>Phone Number</xhtml:th>
                    <xhtml:th>Action</xhtml:th>
                </xhtml:tr>
                <xsl:for-each select="/root/friends/friend">
                    <xhtml:tr>
                        <xhtml:td>
                            <xsl:value-of select="first"/>
                        </xhtml:td>
                        <xhtml:td>
                            <xsl:value-of select="last"/>
                        </xhtml:td>
                        <xhtml:td>
                            <xsl:value-of select="phone"/>
                        </xhtml:td>
                        <xhtml:td>
                            <xforms:submit>
                                <xforms:label>Remove</xforms:label>
                                <xforms:setvalue ref="action">del-<xsl:value-of select="id"/></xforms:setvalue>
                            </xforms:submit>
                        </xhtml:td>
                    </xhtml:tr>
                </xsl:for-each>
                <xhtml:tr>
                    <xhtml:td>
                        <xforms:input ref="first"/>
                    </xhtml:td>
                    <xhtml:td>
                        <xforms:input ref="last"/>
                    </xhtml:td>
                    <xhtml:td>
                        <xforms:input ref="phone"/>
                    </xhtml:td>
                    <xhtml:td>
                        <xforms:submit>
                            <xforms:label>Add</xforms:label>
                            <xforms:setvalue ref="action">add</xforms:setvalue>
                        </xforms:submit>
                    </xhtml:td>
                </xhtml:tr>
            </xhtml:table>
        </xforms:group>
        <xhtml:p>
            Status: <xsl:value-of select="/root/status"/>
        </xhtml:p>
    </xhtml:body>
</xhtml:html>
