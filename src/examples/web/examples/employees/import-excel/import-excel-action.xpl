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
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:delegation="http://orbeon.org/oxf/xml/delegation"
    xmlns:employee="http://orbeon.org/ops/examples/employee-demo/employee">

    <p:param name="instance" type="input"/>

    <!-- Dereference URI stored in instance and return a binary -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" href="aggregate('config', aggregate('url', #instance#xpointer(string(/form/files/file[1]))), aggregate('content-type', #instance#xpointer('application/octet-stream')))"/>
        <p:output name="data" id="xls-binary"/>
    </p:processor>

    <!-- Convert file to XML -->
    <p:processor name="oxf:from-xls-converter">
        <p:input name="data" href="#xls-binary"/>
        <p:output name="data" id="workbook"/>
    </p:processor>

    <!-- Build WS call -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#workbook"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <delegation:execute service="import" operation="import">
                        <employee:employees>
                            <xsl:for-each select="/workbook/sheet/employees/employee-id">
                                <employee:employee>
                                    <xsl:apply-templates select="."/>
                                    <xsl:apply-templates select="following-sibling::*[6 >= position()]"/>
                                </employee:employee>
                            </xsl:for-each>
                        </employee:employees>
                    </delegation:execute>
                </xsl:template>
                <xsl:template match="*">
                    <xsl:element name="employee:{local-name()}" namespace="http://orbeon.org/ops/examples/employee-demo/employee">
                        <xsl:value-of select="."/>
                    </xsl:element>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="call"/>
    </p:processor>

    <!-- Excecute call -->
    <p:processor name="oxf:delegation">
        <p:input name="interface">
            <config>
                <service id="import" type="webservice" endpoint="http://localhost:8888/oxf/example-resources/employees/import-ws" style="document">
                    <operation nsuri="http://www.openuri.org/" name="import"
                        encodingStyle="http://schemas.xmlsoap.org/soap/encoding/" select="/*"/>
                </service>
            </config>
        </p:input>
        <p:input name="call" href="#call"/>
        <p:output name="data" id="result"/>
    </p:processor>

    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#result"/>
    </p:processor>

</p:config>
