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
    xmlns:local="http://orbeon.org/oxf/xml/local">

    <xsl:variable name="form-instance" select="/*/form" as="element()"/>
    <xsl:variable name="query-result" select="/*/employees" as="element()"/>
    <xsl:variable name="request-security" select="/*/request-security" as="element()"/>

    <xsl:variable name="row-count" select="$query-result/employee-count" as="xs:nonNegativeInteger"/>
    <xsl:variable name="current-page-number" select="$form-instance/page-number" as="xs:positiveInteger"/>
    <xsl:variable name="page-size" select="$form-instance/page-size" as="xs:positiveInteger"/>
    <xsl:variable name="last-page-number" select="($row-count + $page-size - 1) idiv $page-size" as="xs:integer"/>

    <xsl:template match="/">
 
         <html xsl:version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
              xmlns:xforms="http://www.w3.org/2002/xforms"
              xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
              xmlns:xi="http://www.w3.org/2003/XInclude"
              xmlns="http://www.w3.org/1999/xhtml">
            <head>
                <title>List Employees</title>
                <tabs xmlns="http://orbeon.org/oxf/xml/formatting">
                    <tab label="Home" selected="false" href="/employees"/>
                    <tab label="Employees" selected="true"/>
                    <tab label="Reports" selected="false"/>
                </tabs>
            </head>
            <body>
                <div id="maincontent">

                    <p>
                        Results <xsl:value-of select="($current-page-number - 1) * $form-instance/page-size + 1"/>
                        - <xsl:value-of select="($current-page-number - 1) * $form-instance/page-size + $form-instance/page-size"/>
                        of <xsl:value-of select="$query-result/employee-count"/>.
                    </p>
                    <xsl:if test="$query-result/employee-count = 0">
                        <p>
                            If this list is empty, log in as <code>admin</code> and select the
                            "Import Data" button.
                        </p>
                    </xsl:if>

                    <xforms:group ref="/form">

                        <table title="Employees List" class="gridtable">
                            <tr>
                                <th colspan="5">Employee Information</th>
                                <th colspan="3">Actions</th>
                            </tr>
                            <tr>
                                <xsl:copy-of select="local:sort-links('Id', 'employee_id')"/>
                                <xsl:copy-of select="local:sort-links('Last Name', 'lastname')"/>
                                <xsl:copy-of select="local:sort-links('First Name', 'firstname')"/>
                                <xsl:copy-of select="local:sort-links('Title', 'title')"/>
                                <xsl:copy-of select="local:sort-links('Age', 'age')"/>
                                <th>Edit</th>
                                <th>Reports</th>
                                <th>Delete</th>
                            </tr>
                            <xsl:for-each select="$query-result/employee">
                                <tr>
                                    <td align="right"><xsl:value-of select="employee-id"/></td>
                                    <td><xsl:value-of select="lastname"/></td>
                                    <td><xsl:value-of select="firstname"/></td>
                                    <td><xsl:value-of select="title"/></td>
                                    <td align="center"><xsl:value-of select="age"/></td>
                                    <td>
                                        <xforms:submit xxforms:appearance="link">
                                            <xforms:label>Edit</xforms:label>
                                            <xforms:setvalue ref="action">edit-employee</xforms:setvalue>
                                            <xforms:setvalue ref="action-employee-id"><xsl:value-of select="employee-id"/></xforms:setvalue>
                                        </xforms:submit>
                                    </td>
                                    <td align="center">
                                        <xforms:submit xxforms:appearance="link">
                                            <xforms:label>View</xforms:label>
                                            <xforms:setvalue ref="action">show-reports</xforms:setvalue>
                                            <xforms:setvalue ref="action-employee-id"><xsl:value-of select="employee-id"/></xforms:setvalue>
                                        </xforms:submit>
                                    </td>
                                    <td align="center">
                                        <xforms:submit xxforms:appearance="image">
                                            <xforms:label>Delete</xforms:label>
                                            <xxforms:img src="/images/remove.png"/>
                                            <xforms:setvalue ref="action">delete-employee</xforms:setvalue>
                                            <xforms:setvalue ref="action-employee-id"><xsl:value-of select="employee-id"/></xforms:setvalue>
                                        </xforms:submit>
                                    </td>
                                </tr>
                            </xsl:for-each>
                        </table>

                        <p/>

                        <xsl:variable name="page-list" select="(max((1, $current-page-number - 10)) to min(($last-page-number, $current-page-number + 9)))" as="xs:integer*"/>

                        <table border="true" class="gridtable">
                            <tr>
                                <td>
                                    <xsl:choose>
                                        <xsl:when test="$current-page-number > 1">
                                            <xforms:submit xxforms:appearance="link">
                                                <xforms:label>First</xforms:label>
                                                <xforms:setvalue ref="page-number" value="1"/>
                                                <xforms:setvalue ref="action"/>
                                            </xforms:submit>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:text>First</xsl:text>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </td>

                                <td>
                                    <xsl:choose>
                                        <xsl:when test="$current-page-number > 1">
                                            <xforms:submit xxforms:appearance="link">
                                                <xforms:label>Prev</xforms:label>
                                                <xforms:setvalue ref="page-number" value="/*/page-number - 1"/>
                                                <xforms:setvalue ref="action"/>
                                            </xforms:submit>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:text>Prev</xsl:text>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </td>

                                <xsl:for-each select="$page-list">
                                    <td style="width: 1em; text-align: center">
                                        <xsl:choose>
                                            <xsl:when test=". = $current-page-number">
                                                <xsl:value-of select="."/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xforms:submit xxforms:appearance="link">
                                                    <xforms:label><xsl:value-of select="."/></xforms:label>
                                                    <xforms:setvalue ref="page-number"><xsl:value-of select="."/></xforms:setvalue>
                                                    <xforms:setvalue ref="action"/>
                                                </xforms:submit>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </td>
                                </xsl:for-each>

                                <td>
                                    <xsl:choose>
                                        <xsl:when test="$current-page-number &lt; $last-page-number">
                                            <xforms:submit xxforms:appearance="link">
                                                <xforms:label>Next</xforms:label>
                                                <xforms:setvalue ref="page-number" value="/*/page-number + 1"/>
                                                <xforms:setvalue ref="action"/>
                                            </xforms:submit>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:text>Next</xsl:text>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </td>

                                <td>
                                    <xsl:choose>
                                        <xsl:when test="$current-page-number &lt; $last-page-number">
                                            <xforms:submit xxforms:appearance="link">
                                                <xforms:label>Last</xforms:label>
                                                <xforms:setvalue ref="page-number" value="{$last-page-number}"/>
                                                <xforms:setvalue ref="action"/>
                                            </xforms:submit>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:text>Last</xsl:text>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </td>

                                <td>
                                    <xforms:input ref="page-size" xhtml:style="width: 2em; font-size: smaller"/>
                                    <xforms:submit xhtml:style="font-size: smaller">
                                        <xforms:label>Update</xforms:label>
                                    </xforms:submit>
                                </td>
                            </tr>
                        </table>

                        <p/>

                        <table>
                            <tr>
                                <td>
                                    <xforms:submit>
                                        <xforms:help>
                                            The <i>Add Employee</i> function allow adding a new employee to the
                                            database.
                                        </xforms:help>
                                        <xforms:label>Add Employee</xforms:label>
                                        <xforms:setvalue ref="action">add-employee</xforms:setvalue>
                                    </xforms:submit>
                                </td>
                                <td>
                                    <xforms:submit>
                                        <xforms:help>
                                            The <i>Export to CSV</i> function illustrates the use of the Text
                                            serializer to generate a Comma-Separated Values file.
                                        </xforms:help>
                                        <xforms:label>Export to CSV</xforms:label>
                                        <xforms:setvalue ref="action">export-csv</xforms:setvalue>
                                    </xforms:submit>
                                </td>
                                <td>
                                    <xforms:submit>
                                        <xforms:help>
                                            The <i>Export to Excel</i> function reads an Excel template, and
                                            updates it with data retrieved from the database. It then sends the
                                            file to the web browser.
                                        </xforms:help>
                                        <xforms:label>Export to Excel</xforms:label>
                                        <xforms:setvalue ref="action">export-excel</xforms:setvalue>
                                    </xforms:submit>
                                </td>
                                <td>
                                    <!-- Show how the content of the page can depend on the current role -->
                                    <xforms:submit>
                                        <xsl:if test="not($request-security/role = 'demo-admin')">
                                            <xsl:attribute name="xhtml:style">color: red; font-weight: bolder</xsl:attribute>
                                        </xsl:if>
                                        <xforms:help>
                                            The <i>Import Data</i> function reads a text file, parses it, and
                                            then iterates on the extracted collection of data to insert it into
                                            the database.
                                        </xforms:help>
                                        <xforms:label>Import Data</xforms:label>
                                        <xforms:setvalue ref="action">import</xforms:setvalue>
                                    </xforms:submit>
                                </td>
                                <td>
                                    <xforms:submit>
                                        <xsl:if test="not($request-security/role = 'demo-admin')">
                                            <xsl:attribute name="xhtml:style">color: red; font-weight: bolder</xsl:attribute>
                                        </xsl:if>
                                        <xforms:help>
                                            The <i>Import from Excel</i> function uses XForms to upload a
                                            binary Excel file. Then it extracts data from the Excel file, and
                                            sends the data to a Web Service. On the receiving side, the Web
                                            Service calls the data access layer to update the database.
                                        </xforms:help>
                                        <xforms:label>Import from Excel</xforms:label>
                                        <xforms:setvalue ref="action">excel-import</xforms:setvalue>
                                    </xforms:submit>
                                </td>
                            </tr>
                        </table>
                    </xforms:group>
                </div>
            </body>
        </html>
    </xsl:template>

    <xsl:function name="local:sort-links" as="element()">
        <xsl:param name="title"/>
        <xsl:param name="column"/>
        <th xmlns="http://www.w3.org/1999/xhtml">
            <xsl:value-of select="$title"/>
            <xsl:text>&#160;</xsl:text>
            <xforms:submit xxforms:appearance="image">
                <xsl:choose>
                    <xsl:when test="$form-instance/sort-column = $column and $form-instance/sort-order = 'ascending'">
                        <xxforms:img src="/images/sort-up.gif"/>
                        <xforms:setvalue ref="sort-column"><xsl:value-of select="$column"/></xforms:setvalue>
                        <xforms:setvalue ref="sort-order">descending</xforms:setvalue>
                    </xsl:when>
                    <xsl:when test="$form-instance/sort-column = $column and $form-instance/sort-order = 'descending'">
                        <xxforms:img src="/images/sort-down.gif"/>
                        <xforms:setvalue ref="sort-column"><xsl:value-of select="$column"/></xforms:setvalue>
                        <xforms:setvalue ref="sort-order">ascending</xforms:setvalue>
                    </xsl:when>
                    <xsl:otherwise>
                        <xxforms:img src="/images/sort-natural.gif"/>
                        <xforms:setvalue ref="sort-column"><xsl:value-of select="$column"/></xforms:setvalue>
                        <xforms:setvalue ref="sort-order">ascending</xforms:setvalue>
                    </xsl:otherwise>
                </xsl:choose>
            </xforms:submit>
        </th>
    </xsl:function>
</xsl:stylesheet>
