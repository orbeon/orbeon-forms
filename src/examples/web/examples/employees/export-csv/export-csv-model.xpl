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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:employee="http://orbeon.org/ops/examples/employee-demo/employee">

    <!-- Read all employees from the database -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/find-all-employees.xpl"/>
        <p:output name="data" id="employees"/>
    </p:processor>

    <!-- Format the data as text -->
    <p:processor name="oxf:xslt-2.0">
        <p:input name="data" href="#employees"/>
        <p:input name="config">
            <text xsl:version="2.0">
                <xsl:for-each select="/*/employee:employee">
                    <!-- TODO: Escape commas in text if needed -->
                    <xsl:value-of select="employee:employee-id"/>
                    <xsl:text>,</xsl:text>
                    <xsl:value-of select="employee:lastname"/>
                    <xsl:text>,</xsl:text>
                    <xsl:value-of select="employee:firstname"/>
                    <xsl:text>,</xsl:text>
                    <xsl:value-of select="employee:title"/>
                    <xsl:text>,</xsl:text>
                    <xsl:value-of select="employee:phone"/>
                    <xsl:text>,</xsl:text>
                    <xsl:value-of select="employee:age"/>
                    <xsl:text>,</xsl:text>
                    <xsl:value-of select="employee:manager-id"/>
                    <xsl:text>&#x0a;</xsl:text>
                </xsl:for-each>
            </text>
        </p:input>
        <p:output name="data" id="employees-csv"/>
    </p:processor>

    <!-- Serialize the data as text -->
    <p:processor name="oxf:text-serializer">
        <p:input name="data" href="#employees-csv"/>
        <p:input name="config">
            <config>
                <!-- Choose your encoding here -->
                <encoding>iso-8859-1</encoding>
                <!-- Generate special header so that web browsers ask to download and save the file -->
                <header>
                    <name>Content-Disposition</name>
                    <value>attachment; filename=employees.csv;</value>
                </header>
            </config>
        </p:input>
    </p:processor>

</p:config>
