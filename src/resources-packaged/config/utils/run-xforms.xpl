<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <!-- Generate resource based on path -->
    <p:processor name="oxf:java">
        <p:input name="config">
            <config sourcepath="oxf:/" class="config.utils.GenerateResource"/>
        </p:input>
        <p:output name="data" id="xhtml-xforms"/>
    </p:processor>
    <!-- Annotate elements in view with ids and alerts -->
    <p:processor name="oxf:xforms-document-annotator">
        <p:input name="data" href="#xhtml-xforms"/>
        <p:output name="data" id="annotated-view"/>
    </p:processor>
    <!-- Native XForms Initialization -->
    <p:processor name="oxf:xforms-to-xhtml">
        <p:input name="annotated-document" href="#annotated-view"/>
        <p:output name="document" id="xformed-data"/>
    </p:processor>
    <!-- Rewrite all URLs in HTML and XHTML documents -->
    <p:processor name="oxf:xhtml-rewrite">
        <p:input name="rewrite-in" href="#xformed-data"/>
        <p:output name="rewrite-out" id="rewritten-data"/>
    </p:processor>
    <!-- Move from XHTML namespace to no namespace -->
    <p:processor name="oxf:qname-converter">
        <p:input name="config">
            <config>
                <match>
                    <uri>http://www.w3.org/1999/xhtml</uri>
                </match>
                <replace>
                    <uri></uri>
                    <prefix></prefix>
                </replace>
            </config>
        </p:input>
        <p:input name="data" href="#rewritten-data"/>
        <p:output name="data" id="html-data"/>
    </p:processor>
    <!-- Convert to plain HTML -->
    <p:processor name="oxf:html-converter">
        <p:input name="config">
            <config>
                <public-doctype>-//W3C//DTD HTML 4.01 Transitional//EN</public-doctype>
                <version>4.01</version>
                <encoding>utf-8</encoding>
            </config>
        </p:input>
        <p:input name="data" href="#html-data"/>
        <p:output name="data" id="converted"/>
    </p:processor>
    <!-- Serialize to HTTP -->
    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <header>
                    <name>Cache-Control</name>
                    <value>post-check=0, pre-check=0</value>
                </header>
                <!-- NOTE: HTML converter specifies text/html content-type -->
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>

</p:config>
