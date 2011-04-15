<!--
    Copyright (C) 2011 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:thread="java.lang.Thread">

    <p:param name="data" type="output"/>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data"><dummy/></p:input>
        <p:input name="config">
            <uuid xsl:version="2.0" xmlns:uuid="java:org.orbeon.oxf.util.UUIDUtils">
                <!-- Prevent pipeline engine to cache the output of this processor -->
                <xsl:if test="false()">
                    <xsl:value-of select="doc('http://dummy')"/>
                </xsl:if>
                <!-- Wait for 5 seconds -->
                <xsl:value-of select="uuid:createPseudoUUID()"/>
            </uuid>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
