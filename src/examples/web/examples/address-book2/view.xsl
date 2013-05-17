<!--
    Copyright (C) 2005 Orbeon, Inc.
  
    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.
  
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.
  
    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<html xmlns:xhtml="http://www.w3.org/1999/xhtml"
      xmlns:xforms="http://www.w3.org/2002/xforms"
      xmlns:ev="http://www.w3.org/2001/xml-events"
      xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
      xmlns="http://www.w3.org/1999/xhtml"
      xsl:version="2.0">
    <head>
        <title>Address Book NG</title>
        <xforms:model>
            <xforms:instance id="main">
                <xsl:copy-of select="/friends"/>
            </xforms:instance>
            <xforms:instance id="add-instance">
                <form xmlns="">
                    <first/>
                    <last/>
                    <phone/>
                </form>
            </xforms:instance>
            <xforms:instance id="delete-instance">
                <form xmlns="">
                    <id/>
                </form>
            </xforms:instance>
            <xforms:instance id="refresh-instance">
                <form xmlns=""/>
            </xforms:instance>
            <xforms:instance id="status">
                <status xmlns="">
                    <message>Read records</message>
                </status>
            </xforms:instance>
            <xforms:submission id="add-submission" ref="instance('add-instance')" replace="instance" instance="main" method="post" action="/address-book2/add"/>
            <xforms:submission id="delete-submission" ref="instance('delete-instance')" replace="instance" instance="main" method="post" action="/address-book2/delete"/>
            <xforms:submission id="refresh-submission" ref="instance('refresh-instance')" replace="instance" instance="main" method="post" action="/address-book2/refresh"/>
        </xforms:model>
    </head>
    <body>
        <table class="gridtable">
            <tr>
                <th>First Name</th>
                <th>Last Name</th>
                <th>Phone Number</th>
                <th>Action</th>
            </tr>
            <xforms:repeat nodeset="friend" id="friendsRepeat">
                <tr>
                    <td>
                        <xforms:output ref="first"/>
                    </td>
                    <td>
                        <xforms:output ref="last"/>
                    </td>
                    <td>
                        <xforms:output ref="phone"/>
                    </td>
                    <td>
                        <xforms:trigger>
                            <xforms:label>Remove</xforms:label>
                            <xforms:action ev:event="DOMActivate">
                                <xforms:setvalue ref="instance('delete-instance')/id" value="instance('main')/friend[index('friendsRepeat')]/id"/>
                                <xforms:send submission="delete-submission"/>
                                <xforms:setvalue ref="instance('status')/message">Deleted record</xforms:setvalue>
                            </xforms:action>
                        </xforms:trigger>
                    </td>
                </tr>
            </xforms:repeat>
            <xforms:group ref="instance('add-instance')">
                <tr>
                    <td>
                        <xforms:input ref="first"/>
                    </td>
                    <td>
                        <xforms:input ref="last"/>
                    </td>
                    <td>
                        <xforms:input ref="phone"/>
                    </td>
                    <td>
                        <xforms:trigger>
                            <xforms:label>Add</xforms:label>
                            <xforms:action ev:event="DOMActivate">
                                <xforms:send submission="add-submission"/>
                                <xforms:setvalue ref="instance('status')/message">Added record</xforms:setvalue>
                                <xforms:setvalue ref="first"/>
                                <xforms:setvalue ref="last"/>
                                <xforms:setvalue ref="phone"/>
                            </xforms:action>
                        </xforms:trigger>
                    </td>
                </tr>
            </xforms:group>
        </table>
        <p>
            Status: <xforms:output ref="instance('status')/message"/>
        </p>
        <xforms:trigger>
            <xforms:label>Refresh</xforms:label>
            <xforms:action ev:event="DOMActivate">
                <xforms:send submission="refresh-submission"/>
                <xforms:setvalue ref="instance('status')/message">Read records</xforms:setvalue>
            </xforms:action>
        </xforms:trigger>
    </body>
</html>
