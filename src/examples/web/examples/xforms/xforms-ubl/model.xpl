<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:order="urn:oasis:names:tc:ubl:Order:1.0:0.70"
    xmlns:cat="urn:oasis:names:tc:ubl:CommonAggregateTypes:1.0:0.70"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>
    <p:param name="instance" type="output"/>

    <!-- Perform custom validation, and generate global errors -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

                <xsl:template match="/form/errors">
                    <xsl:copy>
                        <!-- Check that not more than 100 items are ordered -->
                        <xsl:variable name="total-quantity"
                            select="sum(/form/order:Order/cat:OrderLine/cat:Quantity[. castable as xs:decimal])"/>
                        <xsl:if test="$total-quantity > 100">
                            <error xxforms:error="Warning: order contains more than 100 units"/>
                        </xsl:if>
                        <!-- Check if more than 5 lines -->
                        <xsl:variable name="lines"
                            select="count(/form/order:Order/cat:OrderLine[not(@xxforms:relevant = 'false')])"/>
                        <xsl:if test="$lines > 5">
                            <error xxforms:error="Warning: more than 5 order lines"/>
                        </xsl:if>
                    </xsl:copy>
                </xsl:template>

                <!-- Copy everything -->
                <xsl:template match="@*|node()">
                    <xsl:copy>
                        <xsl:apply-templates select="@*|node()"/>
                    </xsl:copy>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="instance"/>
    </p:processor>

</p:config>
