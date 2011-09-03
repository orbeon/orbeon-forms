<!--
  Copyright (C) 2009 Orbeon, Inc.

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
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:thread="java.lang.Thread">

    <p:param name="data" type="output"/>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters/parameter[name = 'delay']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#request"/>
        <p:input name="config">
            <delay xsl:version="2.0">
                <!-- Prevent pipeline engine to cache the output of this processor -->
                <xsl:if test="false()">
                    <xsl:value-of select="doc('http://dummy')"/>
                </xsl:if>
                <!-- Wait for n seconds -->
                <xsl:value-of select="thread:sleep(xs:integer(/request/parameters/parameter/value * 1000))"/>
                <xsl:value-of select="/request/parameters/parameter/value"/>
            </delay>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
