<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="/">
        <p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
                  xmlns:oxf="http://www.orbeon.com/oxf/processors"
                  xmlns:soap-env="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:gs="urn:GoogleSearch">

            <p:param name="data" type="input"/>

            <p:processor name="oxf:xslt">
                <p:input name="data" href="#data"/>
                <p:input name="config" href="oxf:/examples/yim/response-pipeline-delegation.xsl"/>
                <p:output name="data" id="delegation"/>
            </p:processor>

            <p:processor name="oxf:delegation">
                <p:input name="interface">
                    <config>
                        <service id="google" type="webservice"
                            endpoint="http://api.google.com/search/beta2">
                            <operation nsuri="urn:GoogleSearch" name="doGoogleSearch"
                                select="/soap-env:Envelope/soap-env:Body/gs:doGoogleSearchResponse/return"/>
                        </service>
                    </config>
                </p:input>
                <p:input name="call" href="#delegation"/>
                <p:output name="data" id="google-response"/>
            </p:processor>

            <p:processor name="oxf:xslt">
                <p:input name="data" href="aggregate('root', #data#xpointer(/message/from),
                        #google-response#xpointer(/return/resultElements/item))"/>
                <p:input name="config" href="oxf:/examples/yim/response-pipeline-message.xsl"/>
                <p:output name="data" id="yim"/>
            </p:processor>

            <p:processor name="oxf:im">
                <p:input name="config">
                    <session>
                        <login>
                            <xsl:value-of select="/form/ws-account"/>
                        </login>
                        <password>
                            <xsl:value-of select="/form/ws-password"/>
                        </password>
                    </session>
                </p:input>
                <p:input name="data" href="#yim"/>
            </p:processor>

        </p:config>
    </xsl:template>
</xsl:stylesheet>
