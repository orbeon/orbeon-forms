#!/bin/sh

# This is a script which you can run under the checked out eXist source code directory to update eXist's references to
# XML parser and XSLT code. This worked as of 2007-12-18.

ORBEON_HOME=../orbeon
rm lib/endorsed/xercesImpl-2.9.1.jar
rm lib/endorsed/xalan-2.7.0.jar
cp $ORBEON_HOME/lib/xerces-xercesImpl-2_9_orbeon_20070711.jar lib/endorsed/
cp $ORBEON_HOME/lib/xalan-2_5_1_orbeon.jar lib/endorsed/
cp $ORBEON_HOME/lib/saxon-8_8_orbeon_20070817.jar lib/endorsed/
for F in $(find src -name *.java)
do
    sed -i -e 's/org.apache.xerces/orbeon.apache.xerces/g' $F
    sed -i -e 's/org.apache.xalan/orbeon.apache.xalan/g' $F
    sed -i -e 's/net.sf.saxon/org.orbeon.saxon/g' $F
done
ant clean
ant
rm $ORBEON_HOME/lib/*exist*
VERSION=1_2
cp exist.jar $ORBEON_HOME/lib/exist_$VERSION.jar
cp exist-optional.jar $ORBEON_HOME/lib/exist-optional_$VERSION.jar
cp lib/extensions/exist-modules.jar $ORBEON_HOME/lib/exist-modules_$VERSION.jar
cp lib/extensions/exist-ngram-module.jar $ORBEON_HOME/lib/exist-ngram-module_$VERSION.jar

cp lib/core/antlr-2.7.6.jar $ORBEON_HOME/lib/exist-antlr-2_7_6.jar
cp lib/core/jgroups-all.jar $ORBEON_HOME/lib/exist-jgroups-all-exist.jar
cp lib/core/xmldb.jar $ORBEON_HOME/lib/exist-xmldb.jar
cp lib/core/xmlrpc-1.2-patched.jar $ORBEON_HOME/lib/exist-xmlrpc-1_2-patched.jar
cp lib/core/quartz-1.6.0.jar $ORBEON_HOME/lib/exist-quartz-1_6_0.jar 
cp lib/core/jta.jar $ORBEON_HOME/lib/exist-jta.jar
cp lib/core/stax-api-1.0.1.jar $ORBEON_HOME/lib/exist-stax-api-1_0_1.jar
