<p:config xmlns:oxf="http://www.orbeon.com/oxf/processors" xmlns:p="http://www.orbeon.com/oxf/pipeline">
    <p:param type="input" name="data"/>
    <p:param type="output" name="otherdata"/>

    <p:processor name="oxf:identity">
        <p:input name="data" href="#data"/>
        <p:output name="data" ref="otherdata"/>
    </p:processor>
</p:config>