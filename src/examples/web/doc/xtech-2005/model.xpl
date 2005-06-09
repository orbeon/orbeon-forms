<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="data" type="output"/>

    <p:processor name="oxf:identity">
        <p:input name="data" href="server-side-xforms-article.xml" schema-href="ideadb-1.1/rng/ideadb.rng"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
