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
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters/parameter[name = 'country-name']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="countries.xml"/>
        <p:input name="request" href="#request"/>
        <p:input name="instance" href="#instance" debug="instance"/>
        <p:input name="config">
            <countries xsl:version="2.0">
                <xsl:variable name="name" select="if (doc('input:instance')/*/@xsi:nil = 'true')            (: No instance was posted to this service :)
                    then doc('input:request')/request/parameters/parameter[name = 'country-name']/value     (: Try getting request parameter :)
                    else doc('input:instance')/instance/country-name                                        (: Use name in posted instance :)"/>
                <xsl:choose>
                    <!-- If no name is specified, just take the first 10 countries -->
                    <xsl:when test="empty($name)">
                        <xsl:copy-of select="/countries/country[10 >= position()]"/>
                    </xsl:when>
                    <!-- Get first 10 countries that start with provided name -->
                    <xsl:otherwise>
                        <xsl:copy-of select="/countries/country[starts-with(lower-case(name), lower-case($name))][10 >= position()]"/>
                    </xsl:otherwise>
                </xsl:choose>
            </countries>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
