<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config" href="response-pipeline.xsl"/>
        <p:output name="data" id="response-pipeline"/>
    </p:processor>

    <p:processor name="oxf:identity">
        <p:input name="data">
            <message/>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

    <p:processor name="oxf:xslt" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <xsl:stylesheet version="1.0">
                <xsl:template match="/">
                    <yim>
                        <session>
                            <login>
                                <xsl:value-of select="/form/ws-account"/>
                            </login>
                            <password>
                                <xsl:value-of select="/form/ws-password"/>
                            </password>
                            <on-message-received>#response-pipeline</on-message-received>
                        </session>
                        <message>
                            <to>
                                <xsl:value-of select="/form/user-account"/>
                            </to>
                            <body>PresentationServer Google ready. Please type in a query.</body>
                        </message>
                    </yim>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="yim"/>
    </p:processor>

    <p:processor name="oxf:im">
        <p:input name="response-pipeline" href="#response-pipeline"/>
        <p:input name="config" href="#yim#xpointer(/yim/session)"/>
        <p:input name="data" href="#yim#xpointer(/yim/message)"/>
    </p:processor>

</p:config>
