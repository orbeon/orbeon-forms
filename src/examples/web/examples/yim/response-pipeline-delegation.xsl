<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="/">
        <delegation:execute service="google" operation="doGoogleSearch" xmlns:delegation="http://orbeon.org/oxf/xml/delegation" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            <key xsi:type="xsd:string">ZO+2Z/9QFHJH2g0sRFVSt/bp8EZ1/Kjr</key>
            <q xsi:type="xsd:string">
                <xsl:value-of select="/message/body"/>
            </q>
            <start xsi:type="xsd:int">0</start>
            <maxResults xsi:type="xsd:int">3</maxResults>
            <filter xsi:type="xsd:boolean">1</filter>
            <restrict xsi:type="xsd:string">String</restrict>
            <safeSearch xsi:type="xsd:boolean">0</safeSearch>
            <lr xsi:type="xsd:string"></lr>
            <ie xsi:type="xsd:string"></ie>
            <oe xsi:type="xsd:string"></oe>
        </delegation:execute>
    </xsl:template>
</xsl:stylesheet>
