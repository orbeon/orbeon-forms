<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/scheme</include>
                <include>/request/server-name</include>
                <include>/request/server-port</include>
                <include>/request/context-path</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="aggregate('root', #request, #instance)"/>
        <p:input name="config">
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <root>
                        <config>
                            <service id="yim" type="webservice" endpoint="{/root/request/scheme}://{/root/request/server-name}:{/root/request/server-port}{/root/request/context-path}/example-resources/yim/im">
                                <operation nsuri="urn:orbeon.com" name="sendYIM"/>
                            </service>
                        </config>
                        <delegation:execute service="yim" operation="sendYIM" xmlns:delegation="http://orbeon.org/oxf/xml/delegation" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                                <login>
                                    <xsl:value-of select="/root/form/ws-account"/>
                                </login>
                                <password>
                                    <xsl:value-of select="/root/form/ws-password"/>
                                </password>
                                <to>
                                    <xsl:value-of select="/root/form/friend-account"/>
                                </to>
                                <body>
                                    <xsl:value-of select="/root/form/message"/>
                                </body>
                        </delegation:execute>
                    </root>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="send-yim"/>
    </p:processor>

    <p:processor name="oxf:delegation">
        <p:input name="interface" href="#send-yim#xpointer(/root/config)"/>
        <p:input name="call" href="aggregate('root', #send-yim#xpointer(/root/delegation:execute))" xmlns:delegation="http://orbeon.org/oxf/xml/delegation"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
