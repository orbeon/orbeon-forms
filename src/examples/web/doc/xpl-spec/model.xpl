<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:processor name="oxf:xslt">
        <p:input name="data" href="xpl-draft.xml"/>
        <p:input name="config" href="xmlspec.xsl"/>
        <p:output name="data" id="xhtml"/>
    </p:processor>

    <p:processor name="oxf:html-serializer">
        <p:input name="config">
            <config>
            </config>
        </p:input>
        <p:input name="data" href="#xhtml"/>
    </p:processor>

</p:config>
