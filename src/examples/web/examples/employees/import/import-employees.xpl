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
          xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <!-- Read plain text file -->
    <p:processor name="oxf:url-generator">
        <p:input name="config">
            <config>
                <url>oxf:/examples/employees/import/people.txt</url>
                <content-type>text/plain</content-type>
            </config>
        </p:input>
        <p:output name="data" id="file"/>
    </p:processor>

    <p:processor name="oxf:java">
        <p:input name="config">
            <config sourcepath="oxf:/examples/employees/import" class="ParseLines"/>
        </p:input>
        <p:input name="data" href="#file"/>
        <p:output name="data" id="lines"/>
    </p:processor>

    <!-- Parse lines and generate structured list of employees -->
    <p:processor name="oxf:xslt-2.0">
        <p:input name="data" href="#lines"/>
        <p:input name="config">
            <employees xsl:version="2.0">
                <xsl:for-each select="/*/line[contains(., ',') and not(starts-with(normalize-space(.), '#'))]">
                    <xsl:variable name="comma-tokens" select="tokenize(normalize-space(.), ',')" as="xs:string*"/>
                    <xsl:variable name="tokens" select="tokenize(normalize-space($comma-tokens[1]), '\s+')" as="xs:string*"/>
                    <xsl:if test="count($tokens) >= 4">
                        <employee xmlns="http://orbeon.org/ops/examples/employee-demo/employee">
                            <employee-id><xsl:value-of select="position()"/></employee-id>
                            <firstname><xsl:value-of select="$tokens[3]"/></firstname>
                            <lastname><xsl:value-of select="$tokens[last()]"/></lastname>
                            <phone>(555) 123 <xsl:value-of select="format-number(position(), '0000')"/></phone>
                            <title><xsl:value-of select="substring(normalize-space($comma-tokens[last()]), 0, 50)"/></title>
                            <age><xsl:value-of select="if ($comma-tokens[2] castable as xs:integer) then normalize-space($comma-tokens[2]) else ''"/></age>
                            <manager-id></manager-id>
                        </employee>
                    </xsl:if>
                </xsl:for-each>
            </employees>
        </p:input>
        <p:output name="data" id="employees"/>
    </p:processor>

    <!-- Delete all existing employees -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/delete-all-employees.xpl"/>
    </p:processor>

    <!-- For each employee, create it in the database -->
    <p:for-each href="#employees" select="/*/employee:employee" xmlns:employee="http://orbeon.org/ops/examples/employee-demo/employee">
        <p:processor name="oxf:pipeline">
            <p:input name="config" href="../data-access/create-employee.xpl"/>
            <p:input name="employee" href="current()"/>
        </p:processor>
    </p:for-each>

    <!-- Create simple hierarchy -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../data-access/initialization/create-hierarchy.xpl"/>
    </p:processor>

</p:config>
