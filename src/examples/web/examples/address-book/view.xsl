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
<html xmlns:xhtml="http://www.w3.org/1999/xhtml"
      xmlns:xforms="http://www.w3.org/2002/xforms"
      xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
      xmlns="http://www.w3.org/1999/xhtml"
      xsl:version="2.0">
    <head>
        <title>Address Book</title>
    </head>
    <body>
        <xforms:group ref="/form">
            <table class="gridtable">
                <tr>
                    <th>First Name</th>
                    <th>Last Name</th>
                    <th>Phone Number</th>
                    <th>Action</th>
                </tr>
                <xsl:for-each select="/friends/friend">
                    <tr>
                        <td>
                            <xsl:value-of select="first"/>
                        </td>
                        <td>
                            <xsl:value-of select="last"/>
                        </td>
                        <td>
                            <xsl:value-of select="phone"/>
                        </td>
                        <td>
                            <xforms:submit>
                                <xforms:label>Remove</xforms:label>
                                <xforms:setvalue ref="action">delete</xforms:setvalue>
                                <xforms:setvalue ref="id"><xsl:value-of select="id"/></xforms:setvalue>
                            </xforms:submit>
                        </td>
                    </tr>
                </xsl:for-each>
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
                        <xforms:submit>
                            <xforms:label>Add</xforms:label>
                            <xforms:setvalue ref="action">add</xforms:setvalue>
                        </xforms:submit>
                    </td>
                </tr>
            </table>
        </xforms:group>
        <p>
            <xsl:variable name="instance" select="doc('input:instance')/form"/>
            Status: <xsl:value-of select="if ($instance/action = 'add') then 'Inserted record'
                else if ($instance/action = 'delete') then 'Deleted record'
                else 'Read records'"/>
        </p>
        <!--
        <p>
            <a href="/address-book/test1">Test 1</a>
        </p>
        <p>
            <a href="/address-book/test2">Test 2</a>
        </p>
        <p>
            <a href="/address-book/test3">Test 3</a>
        </p>
        <p>
            <a href="/address-book/test4">Test 4</a>
        </p>
        <p>
            <a href="/address-book/test5">Test 5</a>
        </p>
        <p>
            <a href="/address-book/test6">Test 6</a>
        </p>
        -->
    </body>
</html>
