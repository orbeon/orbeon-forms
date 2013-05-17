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
    xmlns:order="urn:oasis:names:tc:ubl:Order:1.0:0.70"
    xmlns:cat="urn:oasis:names:tc:ubl:CommonAggregateTypes:1.0:0.70"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xforms="http://www.w3.org/2002/xforms">

    <p:param name="instance" type="input"/>
    <p:param name="instance" type="output"/>
    <p:param name="data" type="output"/>

    <!-- Perform custom validation, and generate errors -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <errors xsl:version="2.0">
                <!-- Check that not more than 100 items are ordered -->
                <xsl:variable name="total-quantity"
                    select="sum(/form/order:Order/cat:OrderLine/cat:Quantity[. castable as xs:decimal])"/>
                <xsl:if test="$total-quantity > 100">
                    <error>Warning: order contains more than 100 units</error>
                </xsl:if>
                <!-- Check if more than 5 lines -->
                <xsl:variable name="lines"
                    select="count(/form/order:Order/cat:OrderLine[not(@xxforms:relevant = 'false')])"/>
                <xsl:if test="$lines > 5">
                    <error>Warning: more than 5 order lines</error>
                </xsl:if>
            </errors>
        </p:input>
        <p:output name="data" id="errors"/>
    </p:processor>

    <!-- Annotate XForms instance -->
    <p:processor name="oxf:xslt-2.0">
        <p:input name="errors" href="#errors"/>
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="global-validation">
                    <xsl:copy>
                        <xsl:attribute name="xxforms:valid">
                            <xsl:value-of select="if (doc('input:errors')/errors/error) then 'false' else 'true'"/>
                        </xsl:attribute>
                    </xsl:copy>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="instance"/>
    </p:processor>

    <p:processor name="oxf:identity">
        <p:input name="data" href="aggregate('root', #errors, #instance)"/>
        <p:output name="data" ref="data"/>
    </p:processor>
    
</p:config>
