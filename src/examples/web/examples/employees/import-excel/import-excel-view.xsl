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
<html xmlns:f="http://orbeon.org/oxf/xml/formatting"
            xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xmlns:xforms="http://www.w3.org/2002/xforms"
            xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xsl:version="2.0" xmlns="http://www.w3.org/1999/xhtml">

    <head>
        <title>Excel Import</title>
        <tabs xmlns="http://orbeon.org/oxf/xml/formatting">
            <tab label="Home" selected="false" href="/employees/"/>
            <tab label="Employees" selected="false"  href="/employees/list-employees"/>
            <tab label="Reports" selected="false"/>
        </tabs>
    </head>
    <body>
        <div id="maincontent">
            <xforms:group ref="/form">
                <p>
                    <xforms:upload ref="files/file[1]"/>
                    <xforms:submit>
                        <xforms:label>Submit</xforms:label>
                        <xforms:setvalue ref="action">import</xforms:setvalue>
                    </xforms:submit>
                </p>
            </xforms:group>
            <p>
                Uploading an Excel file through this form will send the data in the XML file to the
                Web service defined by <a href="import.wsdl">this WSDL file</a>.
            </p>
        </div>
    </body>
</html>
