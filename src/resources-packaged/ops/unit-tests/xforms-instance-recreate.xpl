<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <p:param type="input" name="model"/>
    <p:param type="input" name="view"/>
    <p:param type="output" name="instance"/>

    <p:processor name="oxf:identity">
        <p:input name="data" href="#model#xpointer(/xforms:model/xforms:instance/*)"/>
        <p:output name="data" id="instance"/>
    </p:processor>

    <p:processor name="oxf:xforms-output">
        <p:input name="model" href="#model"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="data" href="#view"/>
        <p:output name="data" id="annotated-data"/>
    </p:processor>
    
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="config" href="oxf:/config/deprecated/xforms-to-xhtml.xsl"/>
        <p:input name="model" href="#model"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="data" href="#annotated-data"/>
        <p:output name="data" id="xhtml"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#xhtml"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <request>
                        <parameters>
                            <xsl:for-each select="//xhtml:input">
                                <parameter>
                                    <name>
                                        <xsl:value-of select="@name"/>
                                    </name>
                                    <value>
                                        <xsl:value-of select="@value"/>
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
        <p:input name="filter"><params/></p:input>
        <p:input name="matcher-result"><result/></p:input>
        <p:output name="data" ref="instance"/>
    </p:processor>

</p:config>
