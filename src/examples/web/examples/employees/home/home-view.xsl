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
                <li>Authentication (login / logout) and roles</li>
                <li>Simple XForms with validation</li>
                <li>XForms upload</li>
                <li>Reading plain text files</li>
                <li>Using the Java processor</li>
                <li>Comma-Separated Values (CSV) export</li>
                <li>Excel import / export</li>
                <li>Web Service call and implementation</li>
                <li>LDAP access</li>
            </ul>
            <h2>Installation and Configuration</h2>
            <p>
                In order to use the rest of this application, you need to make sure the database is
                correctly configured. To do so, configure your application server to expose a
                J2EE datasource:
            </p>
            <ul>
                <li>Datasource name: <code>jdbc/db</code></li>
                <li>
                    Datasource parameters:
                    <ul>
                        <li><code>driverClassName</code>: <code>org.hsqldb.jdbcDriver</code></li>
                        <li>
                            <code>url</code>:
                            <code>jdbc:hsqldb:http://<i>localhost:8888</i>/<i>orbeon</i>/db</code>,
                            where <i>localhost:8888</i> refers to the host and port of your
                            application server's installation, and where <i>orbeon</i> refers
                            to the context path of your application.
                        </li>
                        <li><code>username</code>: <code>sa</code></li>
                        <li><code>password</code>: <i>blank</i></li>
                    </ul>
                </li>
            </ul>
            <p>
                You also need to configure some users and roles:
            </p>
            <ul>
                <li><code>demo-admin</code> role</li>
                <li><code>demo-user</code> role</li>
                <li><code>admin</code> user with both the <code>demo-admin</code> and <code>demo-user</code> roles</li>
                <li><code>user</code> user with the <code>demo-user</code> roles</li>
            </ul>
            <p>
                You can configure those roles in an LDAP server, or, for example with Apache Tomcat,
                you can use a simpler file-based list of users (<code>tomcat-users.xml</code>).
            </p>
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
