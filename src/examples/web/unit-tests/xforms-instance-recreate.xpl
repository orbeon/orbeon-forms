<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="instance"/>

    <p:processor uri="oxf/processor/xslt-2.0">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <xforms:model xsl:version="2.0">
                <xforms:instance>
                    <xsl:copy-of select="/*"/>
                </xforms:instance>
            </xforms:model>
        </p:input>
        <p:output name="data" id="model"/>
    </p:processor>

    <p:processor name="oxf:xforms-output">
        <p:input name="model" href="#model"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="data">
            <html>
                <xforms:group ref="/*"/>
            </html>
        </p:input>
        <p:output name="data" id="xf-out"/>
    </p:processor>

    <p:processor uri="oxf/processor/xslt-2.0">
        <p:input name="data" href="#xf-out"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <request>
                        <parameters>
                            <parameter>
                                <name>$submitted</name>
                                <value>true</value>
                            </parameter>
                            <xsl:for-each select="//xxforms:hidden">
                                <parameter>
                                    <name>
                                        <xsl:value-of select="@xxforms:name"/>
                                    </name>
                                    <value>
                                        <xsl:value-of select="@xxforms:value"/>
                                    </value>
                                </parameter>
                            </xsl:for-each>
                        </parameters>
                    </request>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:xforms-input">
        <p:input name="model" href="#model"/>
        <p:input name="request" href="#request"/>
        <p:input name="filter">
            <null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>
        </p:input>
        <p:input name="instance" href="#model#xpointer(xforms:model/xforms:instance/*)"/>
        <p:output name="data" ref="instance"/>
    </p:processor>

</p:config>
