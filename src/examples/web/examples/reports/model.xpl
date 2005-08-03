<!--
    Copyright (C) 2004 Orbeon, Inc.
  
    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.
  
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.
  
    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param name="data" type="output"/>

    <!-- Make sure database is initialized with the tables and data we are using -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="initialization/init-database.xpl"/>
    </p:processor>

    <p:processor name="oxf:sql">
        <p:input name="data"><dummy/></p:input>
        <p:input name="datasource" href="../datasource-sql.xml"/>
        <p:input name="config">
          <sql:config  xmlns:sql="http://orbeon.org/oxf/xml/sql">
              <sql:connection>
                  <sql:execute>
                      <sql:query>
                          select cd.catid, cd.name category_name, p.productid, pd.cname product_name,
                                 i.itemid, id.descn item_name, id.listprice
                            from oxf_category_details cd, oxf_product p, oxf_product_details pd, oxf_item i, oxf_item_details id
                           where cd.locale = 'en_US'
                             and pd.locale = 'en_US'
                             and id.locale = 'en_US'
                             and cd.catid = p.catid
                             and p.productid = i.productid
                             and p.productid = pd.productid
                             and i.itemid = id.itemid
                          order by cd.catid, p.productid
                      </sql:query>
                      <sql:results>
                          <categories>
                              <sql:row-results>
                                  <sql:group column="catid">
                                      <category>
                                          <name><sql:get-column type="xs:string" column="category_name"/></name>
                                          <sql:member>
                                              <sql:group column="productid">
                                                  <product>
                                                     <name><sql:get-column type="xs:string" column="product_name"/></name>
                                                      <sql:member>
                                                          <sql:group column="itemid">
                                                              <sql:member>
                                                                  <item>
                                                                      <name><sql:get-column type="xs:string" column="item_name"/></name>
                                                                      <listprice><sql:get-column type="xs:decimal" column="listprice"/></listprice>
                                                                  </item>
                                                              </sql:member>
                                                          </sql:group>
                                                      </sql:member>
                                                  </product>
                                              </sql:group>
                                          </sql:member>
                                      </category>
                                  </sql:group>
                              </sql:row-results>
                          </categories>
                      </sql:results>
                  </sql:execute>
              </sql:connection>
          </sql:config>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
