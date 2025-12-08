<p:config
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xf="http://www.w3.org/2002/xforms"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
>
    <p:param type="input"  name="data"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:regexp">
        <p:input name="config"><config>/fr/(service/)?([^/]+)/([^/]+)/([A-Za-z_][A-Za-z0-9\-_:]*)(/([^/]+))?(/([0-9A-Za-z\-]+)(?:/[^/]+)?\.(?:pdf|tiff))?</config></p:input>
        <p:input name="data" href="#data#xpointer(/_/path-query)"/>
        <p:output name="data" id="matcher-groups"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="config" href="/apps/fr/components/components.xsl"/>

        <p:input name="data"   href="/forms/issue/6707/form/form.xhtml"/>

        <p:input name="instance" transform="oxf:unsafe-xslt" href="#matcher-groups">
            <request xsl:version="2.0">
                <app><xsl:value-of select="/*/group[2]"/></app>
                <form><xsl:value-of select="/*/group[3]"/></form>
                <form-version>1</form-version>
                <document><xsl:value-of select="/*/group[6]"/></document>
                <mode><xsl:value-of select="/*/group[4]"/></mode>
            </request>
        </p:input>

        <p:input name="request" transform="oxf:unsafe-xslt" href="aggregate('_', #data, #matcher-groups)">
            <request xsl:version="2.0">
                <method><xsl:value-of select="/_/_/method"/></method>
                <xsl:variable name="path" select="/_/_/path-query/string()"/>
                <request-path><xsl:value-of select="$path"/></request-path>
                <request-uri><xsl:value-of select="$path"/></request-uri>
                <parameters>
                    <xsl:for-each select="p:uri-param-names($path)">
                        <xsl:variable name="name"   select="."/>
                        <xsl:variable name="values" select="p:uri-param-values($path, $name)"/>
                        <parameter>
                            <name><xsl:value-of select="$name"/></name>
                            <xsl:for-each select="$values">
                                <value><xsl:value-of select="."/></value>
                            </xsl:for-each>
                        </parameter>
                    </xsl:for-each>
                </parameters>
            </request>
        </p:input>

        <p:output name="data"    id="unrolled-form"/>
    </p:processor>

     <p:processor name="oxf:unsafe-xslt">
        <p:input name="config">
            <xsl:transform version="2.0">
                <xsl:import href="/oxf/xslt/utils/copy-modes.xsl"/>
                <xsl:template match="/">
                    <_>
                        <xsl:apply-templates select="//*[@id = 'fr-view-component']/*"/>
                    </_>
                </xsl:template>
                <xsl:template match="xf:label | xf:hint | xf:help | xf:alert | xf:itemset | xxf:setvisited"/>
                <xsl:template match="fr:section | fr:grid | fr:c">
                    <xsl:apply-templates select="*"/>
                </xsl:template>
            </xsl:transform>
        </p:input>
        <p:input name="data"  href="#unrolled-form"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>