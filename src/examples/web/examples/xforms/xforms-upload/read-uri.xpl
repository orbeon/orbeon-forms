<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="uri" type="input"/>
    <p:param name="data" type="output"/>

    <!-- Create URL generator configuration and generate data from the URL -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#uri"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <config>
                        <url><xsl:value-of select="/*"/></url>
                        <content-type>application/octet-stream</content-type>
                        <force-content-type>true</force-content-type>
                        <cache-control>
                            <use-local-cache>false</use-local-cache>
                        </cache-control>
                    </config>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="url-config"/>
    </p:processor>
    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#url-config"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
