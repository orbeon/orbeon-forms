<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <!-- Extract request body as a URI -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI" xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <include>/request/body</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Dereference URI -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" href="aggregate('config', aggregate('url', #request#xpointer(string(/reqest/body)))"/>
        <p:output name="data" id="file"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#file"/>
        <p:input name="config">
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                    xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:m="urn:orbeon.com">
                <xsl:template match="/">
                    <config>
                        <session>
                            <xsl:copy-of select="/SOAP-ENV:Envelope/SOAP-ENV:Body/m:sendYIM/login"/>
                            <xsl:copy-of select="/SOAP-ENV:Envelope/SOAP-ENV:Body/m:sendYIM/password"/>
                        </session>
                        <message>
                            <xsl:copy-of select="/SOAP-ENV:Envelope/SOAP-ENV:Body/m:sendYIM/to"/>
                            <xsl:copy-of select="/SOAP-ENV:Envelope/SOAP-ENV:Body/m:sendYIM/body"/>
                        </message>
                    </config>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="im"/>
    </p:processor>

    <p:processor name="oxf:im">
        <p:input name="config" href="#im#xpointer(/config/session)"/>
        <p:input name="data" href="#im#xpointer(/config/message)"/>
    </p:processor>

    <p:processor name="oxf:xml-serializer">
        <p:input name="config"><config/></p:input>
        <p:input name="data">
            <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsi="http://www.w3.org/1999/XMLSchema-instance" xmlns:xsd="http://www.w3.org/1999/XMLSchema">
                <SOAP-ENV:Body>
                    <ns1:sendYIMResponse xmlns:ns1="urn:avernet"
                        SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"/>
                </SOAP-ENV:Body>
            </SOAP-ENV:Envelope>
        </p:input>
    </p:processor>

</p:config>
