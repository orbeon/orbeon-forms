<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>

    <!-- Get URL from UUID -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <config xsl:version="2.0">
                <key>
                    <xsl:value-of select="/instance/uuid"/>
                </key>
                <scope>session</scope>
            </config>
        </p:input>
        <p:output name="data" id="scope-config"/>
    </p:processor>
    <p:processor name="oxf:scope-generator">
        <p:input name="config" href="#scope-config"/>
        <p:output name="data" id="url"/>
    </p:processor>

    <!-- Read file -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#url"/>
        <p:input name="config">
            <config xsl:version="2.0">
                <xsl:copy-of select="/url"/>
            </config>
        </p:input>
        <p:output name="data" id="url-generator-config"/>
    </p:processor>
    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#url-generator-config"/>
        <p:output name="data" id="file-content"/>
    </p:processor>

    <!-- Config for the HTTP serializer with extension based on attribute type -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <config xsl:version="2.0">
                <header>
                    <name>Content-Disposition</name>
                    <value>attachment; filename=<xsl:value-of select="replace(/instance/filename, ' ', '_')"/></value>
                </header>
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
        <p:output name="data" id="http-serializer-config"/>
    </p:processor>

    <!-- Send result through HTTP -->
    <p:processor name="oxf:http-serializer">
        <p:input name="config" href="#http-serializer-config"/>
        <p:input name="data" href="#file-content"/>
    </p:processor>

</p:config>
