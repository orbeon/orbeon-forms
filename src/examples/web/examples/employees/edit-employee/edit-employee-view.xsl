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
 <html xsl:version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
       xmlns:xforms="http://www.w3.org/2002/xforms"
       xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
       xmlns:xi="http://www.w3.org/2003/XInclude"
       xmlns:xs="http://www.w3.org/2001/XMLSchema"
       xmlns:employee="http://orbeon.org/ops/examples/employee-demo/employee"
       xmlns="http://www.w3.org/1999/xhtml">
    <head>
        <title>Edit Employee</title>
        <tabs xmlns="http://orbeon.org/oxf/xml/formatting">
            <tab label="Home" selected="false" href="/employees"/>
            <tab label="Employees" selected="false" href="/employees/list-employees"/>
            <tab label="Reports" selected="false"/>
        </tabs>
    </head>
    <body>
        <div id="maincontent">

            <xsl:if test="/form/message != ''">
                <table>
                    <tr>
                        <td>
                            <xsl:choose>
                                <xsl:when test="/form/message = 'save-success'">
                                    <span style="color: green">Document saved!</span>
                                </xsl:when>
                                <xsl:when test="/form/message = 'save-failure'">
                                    <span style="color: red">Document not saved! Please correct errors first.</span>
                                </xsl:when>
                            </xsl:choose>
                        </td>
                    </tr>
                </table>
                <p/>
            </xsl:if>

            <xforms:group ref="/form" xxforms:show-errors="{/form/show-errors = 'true'}">

                <table title="Employee Detail">
                    <tr>
                        <th valign="middle">Last Name</th>
                        <td>
                            <xforms:input ref="/*/document/employee:employee/employee:lastname">
                                <xforms:hint>Last Name</xforms:hint>
                                <xforms:help>Please enter a mandatory employee last name</xforms:help>
                                <xforms:alert src="orbeon:xforms:schema:errors" />
                            </xforms:input>
                        </td>
                    </tr>
                    <tr>
                        <th valign="middle">First Name</th>
                        <td>
                            <xforms:input ref="/*/document/employee:employee/employee:firstname">
                                <xforms:hint>First Name</xforms:hint>
                                <xforms:help>Please enter a mandatory employee first name</xforms:help>
                                <xforms:alert src="orbeon:xforms:schema:errors" />
                            </xforms:input>
                        </td>
                    </tr>
                    <tr>
                        <th valign="middle">Title</th>
                        <td>
                            <xforms:input ref="/*/document/employee:employee/employee:title">
                                <xforms:hint>Title</xforms:hint>
                                <xforms:help>Please enter an optional employee title</xforms:help>
                            </xforms:input>
                        </td>
                    </tr>
                    <tr>
                        <th valign="middle">Phone</th>
                        <td>
                            <xforms:input ref="/*/document/employee:employee/employee:phone">
                                <xforms:hint>Phone Number</xforms:hint>
                                <xforms:help>Please enter an optional employee phone number</xforms:help>
                            </xforms:input>
                        </td>
                    </tr>
                    <tr>
                        <th valign="middle">Age</th>
                        <td>
                            <xforms:input ref="/*/document/employee:employee/employee:age">
                                <xforms:hint>Age</xforms:hint>
                                <xforms:help>Please enter an optional employee age</xforms:help>
                            </xforms:input>
                        </td>
                    </tr>
                </table>

                <p/>

                <xforms:submit>
                    <xforms:label>Save</xforms:label>
                    <xforms:setvalue ref="action">save</xforms:setvalue>
                </xforms:submit>

                <xforms:submit>
                    <xforms:label>Back</xforms:label>
                    <xforms:setvalue ref="action">back</xforms:setvalue>
                </xforms:submit>

            </xforms:group>
        </div>
    </body>
</html>
