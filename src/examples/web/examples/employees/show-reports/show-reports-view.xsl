<!--
    Copyright 2004 Orbeon, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:local="http://orbeon.org/oxf/xml/local"
    xmlns:employee="http://orbeon.org/ops/examples/employee-demo/employee">

    <xsl:variable name="employee" select="/*/*[1]/*" as="element()"/>
    <xsl:variable name="reports" select="/*/*[2]/*" as="element()*"/>

    <xsl:template match="/">

         <html xsl:version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
              xmlns:xforms="http://www.w3.org/2002/xforms"
              xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
              xmlns:xi="http://www.w3.org/2003/XInclude"
              xmlns="http://www.w3.org/1999/xhtml">
            <head>
                <title>Show Reports</title>
                <tabs xmlns="http://orbeon.org/oxf/xml/formatting">
                    <tab label="Home" selected="false" href="/employees/"/>
                    <tab label="Employees" selected="false" href="/employees/list-employees"/>
                    <tab label="Reports" selected="false"/>
                </tabs>
            </head>
            <body>
                <div id="maincontent">

                    <xforms:group ref="/form">

                        <xsl:variable name="name" select="concat($employee/employee:firstname, ' ', $employee/employee:lastname)"/>

                        <table>
                            <tr>
                                <td style="background: white">
                                    <xsl:copy-of select="local:display-box($employee, false())"/>
                                </td>
                            </tr>
                        </table>

                        <p/>

                        <xsl:if test="$reports">

                            <h2>Reports Hierarchy for <xsl:value-of select="$name"/></h2>

                            <table>
                                <tr>
                                    <xsl:for-each select="$reports[employee:manager-id = $employee/employee:employee-id]">
                                        <xsl:variable name="current-employee-id" select="employee:employee-id"/>
                                        <xsl:variable name="reports-count" select="count($reports[employee:manager-id = $current-employee-id])"/>
                                        <td style="background: white" colspan="{$reports-count}">
                                            <xsl:copy-of select="local:display-box(., true())"/>
                                        </td>
                                    </xsl:for-each>
                                </tr>
                                <tr>
                                    <xsl:for-each select="$reports[employee:manager-id = $employee/employee:employee-id]">
                                        <xsl:variable name="current-employee-id" select="employee:employee-id"/>
                                        <xsl:variable name="reports-count" select="count($reports[employee:manager-id = $current-employee-id])"/>
                                        <xsl:choose>
                                            <xsl:when test="$reports-count = 0">
                                                <td>
                                                    <i>No reports</i>
                                                </td>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:for-each select="$reports[employee:manager-id = $current-employee-id]">
                                                    <td style="background: white">
                                                        <xsl:copy-of select="local:display-box(., true())"/>
                                                    </td>
                                                </xsl:for-each>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xsl:for-each>
                                </tr>
                            </table>
                        </xsl:if>

                        <p/>

                        <xforms:submit>
                            <xforms:label>Back</xforms:label>
                            <xforms:setvalue ref="action">back</xforms:setvalue>
                        </xforms:submit>

                    </xforms:group>
                </div>
            </body>
        </html>
    </xsl:template>

    <xsl:function name="local:display-box" as="element()*">
        <xsl:param name="employee" as="element()"/>
        <xsl:param name="link" as="xs:boolean"/>
        <table xmlns="http://www.w3.org/1999/xhtml" width="100%" class="gridtable">
            <tr>
                <th>
                    <xsl:choose>
                        <xsl:when test="$link">
                            <xforms:submit xxforms:appearance="link">
                                <xforms:label><xsl:value-of select="$employee/employee:title"/></xforms:label>
                                <xforms:setvalue ref="action">show-reports</xforms:setvalue>
                                <xforms:setvalue ref="employee-id"><xsl:value-of select="$employee/employee:employee-id"/></xforms:setvalue>
                            </xforms:submit>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="$employee/employee:title"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </th>
            </tr>
            <tr>
                <td style="background: white">
                    Name: <xsl:value-of select="concat($employee/employee:firstname, ' ', $employee/employee:lastname)"/>
                    <br/>
                    Phone: <xsl:value-of select="$employee/employee:phone"/>
                    <br/>
                    Age: <xsl:value-of select="$employee/employee:age"/>
                    <br/>
                    <xforms:submit xxforms:appearance="link">
                        <xforms:label>Edit</xforms:label>
                        <xforms:setvalue ref="action">edit-employee</xforms:setvalue>
                        <xforms:setvalue ref="action-employee-id"><xsl:value-of select="$employee/employee:employee-id"/></xforms:setvalue>
                    </xforms:submit>
                </td>
            </tr>
        </table>
    </xsl:function>

</xsl:stylesheet>
