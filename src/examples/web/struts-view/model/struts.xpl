<!--
    Copyright (C) 2004 Orbeon, Inc.
  
    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.
  
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.
  
    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/request-path</include>
            </config>
        </p:input>
        <p:output name="data" id="path"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="config">
            <config xsl:version="2.0">oxf:/examples-standalone/struts-view/view/<xsl:value-of select="substring-after(/request/request-path, '/examples-struts/')"/></config>
        </p:input>
        <p:input name="data" href="#path"/>
        <p:output name="data" id="url"/>
    </p:processor>

    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#url"/>
        <p:output name="data" id="user-pipeline"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#user-pipeline"/>
        <p:input name="url" href="#url"/>
        <p:input name="config">
            <p:config xsl:version="2.0">
                <p:param type="output" name="page"/>

                <xsl:for-each select="/p:config/p:param[@type='input' and @name != 'errors']">
                    <p:processor name="oxf:bean-generator">
                        <p:input name="mapping">
                            <mapping/>
                        </p:input>
                        <p:input name="config">
                            <config>
                                <attribute>
                                    <xsl:value-of select="@name"/>
                                </attribute>
                                <source>request</source>
                            </config>
                        </p:input>
                        <p:output name="data" id="bean-{@name}"/>
                    </p:processor>
                </xsl:for-each>

                <xsl:if test="/p:config/p:param[@type='input' and @name = 'errors']">
                    <p:processor name="oxf:struts-errors-generator">
                        <p:output name="data" id="errors"/>
                    </p:processor>
                </xsl:if>

                <p:processor name="oxf:url-generator">
                    <p:input name="config">
                        <xsl:copy-of select="doc('oxf:url')"/>
                    </p:input>
                    <p:output name="data" id="user-pipeline"/>
                </p:processor>

                <p:processor name="oxf:pipeline">
                    <p:input name="config" href="#user-pipeline"/>
                    <xsl:for-each select="/p:config/p:param[@type='input' and @name != 'errors']">
                        <p:input name="{@name}" href="#bean-{@name}"/>
                    </xsl:for-each>
                    <xsl:if test="/p:config/p:param[@type='input' and @name = 'errors']">
                        <p:input name="errors" href="#errors"/>
                    </xsl:if>
                    <p:output name="data" ref="page"/>
                </p:processor>
            </p:config>
        </p:input>
        <p:output name="data" id="pipeline"/>
    </p:processor>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="#pipeline"/>
        <p:output name="page" id="page"/>
    </p:processor>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="oxf:/config/epilogue.xpl"/>
        <p:input name="data" href="#page" />
        <p:input name="xforms-instance"><dummy/></p:input>
        <p:input name="xforms-model"><dummy/></p:input>
    </p:processor>

</p:config>