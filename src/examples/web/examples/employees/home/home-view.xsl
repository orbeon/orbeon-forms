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
      xmlns:f="http://orbeon.org/oxf/xml/formatting"
      xmlns:xi="http://www.w3.org/2003/XInclude"
      xmlns="http://www.w3.org/1999/xhtml">

    <head>
        <title>Employees Example Home</title>
        <tabs xmlns="http://orbeon.org/oxf/xml/formatting">
            <tab label="Home" selected="true"/>
            <tab label="Employees" selected="false" href="/employees/list-employees"/>
            <tab label="Reports" selected="false"/>
        </tabs>
    </head>
    <body>
        <div id="maincontent">
            <p>
                Welcome to the Employees example application!
            </p>
            <h2>What is it?</h2>
            <p>
                This is a sample application for <a
                href="http://www.orbeon.com/software/presentation-server">Orbeon Presentation
                Server</a>. It illustrates the following functionality:
            </p>
            <ul>
                <li>CRUD operations, paging and sorting with a SQL backend</li>
                <li>XForms controls and validation with XML Schema</li>
                <li>XForms upload control</li>
                <li>Reading plain text files</li>
                <li>Using the Java processor</li>
                <li>Exporting Comma-Separated Values (CSV) files</li>
                <li>Exporting and importing Excel files</li>
                <li>Calling and implementing Web Service</li>
                <li>Authentication (login / logout) and roles</li>
                <li>LDAP access</li>
            </ul>
            <h2>Installation and Configuration</h2>
            <p>
                The example should work out of the box except for web service functionality.
            </p>
            <p>
                <xsl:choose>
                    <xsl:when test="not(doc('../ws-config.xml')/*/host)">
                        <b>Currently, Web Service functionality is not configured.</b> If you
                        choose to use it, please configure the file <code>ws-config.xml</code>.
                    </xsl:when>
                    <xsl:otherwise>
                        You appear to have configured Web Service functionality by configuring the
                        file <code>ws-config.xml</code>.
                    </xsl:otherwise>
                </xsl:choose>
            </p>
            <p>
                You may also optionally configure the following users and roles:
            </p>
            <ul>
                <li><code>demo-admin</code> role</li>
                <li><code>demo-user</code> role</li>
                <li><code>admin</code> user with both the <code>demo-admin</code> and <code>demo-user</code> roles</li>
                <li><code>user</code> user with the <code>demo-user</code> roles</li>
            </ul>
            <p>
                You can configure those roles in an LDAP server, or, for example with Apache Tomcat,
                you can use a simpler file-based list of users (<code>tomcat-users.xml</code>). You
                should then add the following to your <code>web.xml</code>:
            </p>
            <f:box>
                <f:xml-source>
                    <security-constraint>
                        <web-resource-collection>
                            <web-resource-name>Application Pages</web-resource-name>
                            <url-pattern>/list-employees</url-pattern>
                            <url-pattern>/edit-employee</url-pattern>
                            <url-pattern>/show-reports</url-pattern>
                            <url-pattern>/csv-export</url-pattern>
                            <url-pattern>/excel-export</url-pattern>
                        </web-resource-collection>
                        <auth-constraint>
                            <role-name>demo-user</role-name>
                        </auth-constraint>
                    </security-constraint>
                    <login-config>
                        <auth-method>FORM</auth-method>
                        <form-login-config>
                            <form-login-page>/login</form-login-page>
                            <form-error-page>/login-error</form-error-page>
                        </form-login-config>
                    </login-config>
                    <security-role>
                        <role-name>demo-user</role-name>
                    </security-role>
                </f:xml-source>
            </f:box>
            <p>
                <xsl:choose>
                    <xsl:when test="not(doc('../ldap-config.xml')/*/host)">
                        <b>Currently, enhanced LDAP functionality is not configured.</b> If you
                        choose to use LDAP, you can, in addition, enable the enhanced LDAP
                        functionality by configuring the file <code>ldap-config.xml</code>.
                    </xsl:when>
                    <xsl:otherwise>
                        You appear to have configured enhanced LDAP functionality by configuring
                        the file <code>ldap-config.xml</code>.
                    </xsl:otherwise>
                </xsl:choose>
            </p>
            <h2>Using the Application</h2>
            <p>
                Follow the "Employees" tab to enter the application.
            </p>
        </div>
    </body>
</html>
