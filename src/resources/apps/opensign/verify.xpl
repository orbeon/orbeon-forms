<!--
    Copyright (C) 2007 Orbeon, Inc.

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
        xmlns:saxon="http://saxon.sf.net/"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters</include>
            </config>
        </p:input>
        <p:output name="data" id="request" debug="request"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#request"/>
        <p:input name="config">
            <result xsl:version="2.0">
                <xsl:value-of select="saxon:base64Binary-to-string(xs:base64Binary(/request/parameters/parameter[name = 'message']/value), 'UTF-8')"/>
            </result>
        </p:input>
        <p:output name="data" id="result" debug="result"/>
    </p:processor>

    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#result"/>
    </p:processor>

</p:config>
