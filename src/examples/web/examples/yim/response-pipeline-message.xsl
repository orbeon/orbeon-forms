<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="/">
        <message>
            <to>
                <xsl:value-of select="/root/from"/>
            </to>
            <body>
                <xsl:text>&#10;</xsl:text>
                <xsl:for-each select="/root/item">
                    <xsl:value-of select="position()"/>
                    <xsl:text>) </xsl:text>
                    <xsl:value-of select="title"/>
                    <xsl:text>&#10;&amp;nbsp;&amp;nbsp;&amp;nbsp;&amp;nbsp;</xsl:text>
                    <xsl:value-of select="URL"/>
                    <xsl:text>&#10;</xsl:text>
                </xsl:for-each>
            </body>
        </message>
    </xsl:template>
</xsl:stylesheet>
