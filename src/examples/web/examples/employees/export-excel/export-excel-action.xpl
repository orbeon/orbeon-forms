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
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/find-all-employees.xpl"/>
        <p:output name="data" id="employees"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <workbook>
                        <sheet name="Employees">
                            <employees>
                                <xsl:apply-templates select="/employees/*[20 > position()]"/>
                            </employees>
                        </sheet>
                    </workbook>
                </xsl:template>
                <xsl:template match="*">
                    <xsl:element name="{local-name()}">
                        <xsl:apply-templates/>
                    </xsl:element>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:input name="data" href="#employees"/>
        <p:output name="data" id="workbook"/>
    </p:processor>

    <p:processor name="oxf:xls-serializer">
        <p:input name="config">
            <config template="oxf:/examples/employees/export-excel/employees.xls" filename="employees.xls">
                <repeat-row row-num="3" for-each="employees/employee"/>
            </config>
        </p:input>
        <p:input name="data" href="#workbook"/>
    </p:processor>

</p:config>
