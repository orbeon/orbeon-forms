#!/bin/sh

set -e

SITE=ops-unstable

# 2/27/2005 d : If we don't delete this then sitecopy complains that because the
# the certificate is for forge.objectweb.org but has been received when 
# accessing ops.forge.objectweb.org.  i.e. It thinks someone's spoofing the 
# site.
BOGUSCERT=$HOME/.sitecopy/$SITE.crt
if [ -e $BOGUSCERT ]
then
  rm $BOGUSCERT
fi  

echo  y | sitecopy -u $SITE
