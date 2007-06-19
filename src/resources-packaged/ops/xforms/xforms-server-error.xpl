<!--
    Copyright (C) 2005 Orbeon, Inc.

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
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <!-- Generate exception document -->
    <p:processor name="oxf:exception">
        <p:output name="data" id="exception"/>
    </p:processor>

    <!-- Format exception -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#exception"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" saxon:allow-all-built-in-types="yes">
                <xsl:import href="oxf:/config/error.xsl"/>
                <xsl:template match="/">
                    <error>
                        <message>
                            <xsl:call-template name="format-message">
                                <xsl:with-param name="exceptions" select="/exceptions/exception"/>
                            </xsl:call-template>
                        </message>
                        <call-stack>
                            <xsl:call-template name="format-orbeon-call-stack">
                                <xsl:with-param name="exceptions" select="/exceptions/exception"/>
                            </xsl:call-template>
                        </call-stack>
                    </error>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="formatted-exception"/>
    </p:processor>

    <!-- Generate response -->
    <p:processor name="oxf:xml-serializer">
        <!--<p:input name="data" href="#exception" debug="xxxerror"/>-->
        <p:input name="data" href="#formatted-exception"/>
        <p:input name="config">
            <config/>
        </p:input>
    </p:processor>

</p:config>
