<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="/">
        <xhtml:html xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xmlns:xforms="http://www.w3.org/2002/xforms"
            xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

            <xhtml:head>
                <xhtml:title>PresentationServer Example - Instant Message</xhtml:title>
            </xhtml:head>
            <xhtml:body>
                <xforms:group ref="/form">
                    <xhtml:p>
                        <xhtml:table class="gridtable">
                            <xhtml:tr>
                                <xhtml:th colspan="4">Google Search Through Yahoo! IM</xhtml:th>
                            </xhtml:tr>
                            <xhtml:tr>
                                <xhtml:th>WebService YIM Account</xhtml:th>
                                <xhtml:td>
                                    <xforms:input ref="ws-account"/>
                                </xhtml:td>
                                <xhtml:th>Password</xhtml:th>
                                <xhtml:td>
                                    <xforms:secret ref="ws-password"/>
                                </xhtml:td>
                            </xhtml:tr>
                            <xhtml:tr>
                                <xhtml:th>User YIM Account</xhtml:th>
                                <xhtml:td colspan="3">
                                    <xforms:input ref="user-account"/>
                                </xhtml:td>
                            </xhtml:tr>
                            <xhtml:tr>
                                <xhtml:th/>
                                <xhtml:td colspan="3">
                                    <xforms:submit>
                                        <xforms:label>Register</xforms:label>
                                        <xforms:setvalue ref="action">register</xforms:setvalue>
                                    </xforms:submit>
                                </xhtml:td>
                            </xhtml:tr>
                        </xhtml:table>
                        <br/>
                    </xhtml:p>
                    <xhtml:p>
                        <xhtml:table class="gridtable">
                            <xhtml:tr>
                                <xhtml:th colspan="4">Send a Message Through a Web Service</xhtml:th>
                            </xhtml:tr>
                            <xhtml:tr>
                                <xhtml:th>Yahoo Account</xhtml:th>
                                <xhtml:td><xforms:input ref="friend-account"/></xhtml:td>
                            </xhtml:tr>
                            <xhtml:tr>
                                <xhtml:th>Message</xhtml:th>
                                <xhtml:td><xforms:textarea ref="message"/></xhtml:td>
                            </xhtml:tr>
                            <xhtml:tr>
                                <xhtml:th/>
                                <xhtml:td>
                                    <xforms:submit>
                                        <xforms:label>Send</xforms:label>
                                        <xforms:setvalue ref="action">send</xforms:setvalue>
                                    </xforms:submit>
                                </xhtml:td>
                            </xhtml:tr>
                        </xhtml:table>
                    </xhtml:p>
                </xforms:group>
            </xhtml:body>
        </xhtml:html>
    </xsl:template>
</xsl:stylesheet>