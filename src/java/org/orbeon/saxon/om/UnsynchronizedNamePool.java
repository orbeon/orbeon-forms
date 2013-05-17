/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.saxon.om;

import org.orbeon.saxon.style.StandardNames;

public class UnsynchronizedNamePool extends NamePool {
    /*

    private int savePrefix( final String pfx, final short uriCode ) {
        final String pfxs = prefixesForUri[ uriCode ];
        final int pfxsLen = pfxs.length();
        final int pfxLen = pfx.length();
        final char[] carr = new char[ pfxsLen + pfxLen + 1 ];

        pfxs.getChars( 0, pfxsLen, carr, 0 );
        pfx.getChars( 0, pfxLen, carr, pfxsLen );
        carr[ pfxsLen + pfxLen ] = ' ';
        prefixesForUri[ uriCode ] = new String( carr );
        final int ret = getPrefixIndex( uriCode, pfx );
        return ret;
    }

    public int allocate( final int fngrprnt, final String pfx ) {
        final short uriCode = getURICode( fngrprnt );
        int prefixIndex = getPrefixIndex( uriCode, pfx );
        if ( prefixIndex < 0 ) {
            prefixIndex = savePrefix( pfx, uriCode );
        }
        return ( prefixIndex<< 20 ) | fngrprnt;
    }

    public int allocate( final String pfx, final short uriCode, String lnam ) {
            // System.err.println("Allocate " + prefix + " : " + uriCode + " : " + localName);
        final int hash = (lnam.hashCode() & 0x7fffffff) % 1023;
        int depth = 1;
        int prefixIndex = getPrefixIndex( uriCode, pfx );

        if ( prefixIndex < 0 ) {
            prefixIndex = savePrefix( pfx, uriCode );
        }

        NameEntry entry;

        if ( hashslots[ hash ] == null ) {
            entry = new NameEntry( uriCode, lnam );
            hashslots[ hash ] = entry;
        } else {
            entry = hashslots[ hash ];
            while ( true ) {
                final boolean sameLocalName = ( entry.localName.equals( lnam ) );
                final boolean sameURI = ( entry.uriCode == uriCode );

                if ( sameLocalName && sameURI ) {
                            // may need to add a new prefix to the entry
                    break;
                } else {
                    final NameEntry next = entry.nextEntry;
                    depth++;
                    if ( depth >= 1024 ) {
                        throw new NamePoolLimitException( "Saxon name pool is full" );
                    }
                    if ( next == null ) {
                        final NameEntry newentry = new NameEntry( uriCode, lnam );
                        entry.nextEntry = newentry;
                        break;
                    } else {
                        entry = next;
                    }
                }
            }
        }
        // System.err.println("name code = " + prefixIndex + "/" + depth + "/" + hash);
        return ( ( prefixIndex << 20 ) + ( depth << 10 ) + hash );
    }
    public int allocate( final String pfx, final String uri, final String lnam ) {
        final int ret;
        done : 
        {
            if ( NamespaceConstant.isReserved( uri ) || uri.equals( NamespaceConstant.SAXON ) ) {
                final int fp = StandardNames.getFingerprint( uri, lnam );
                if ( fp != -1 ) {
                    final short uriCode = StandardNames.getURICode( fp );
                    final int pfxIdx = getPrefixIndex( uriCode, pfx );

                    if ( pfxIdx < 0 ) {
                        savePrefix( pfx, uriCode );
                    }

                    ret = ( pfxIdx << 20 ) + fp;
                    break done;
                }
            }
            // otherwise register the name in this NamePool
            final short uriCode = allocateCodeForURI( uri );
            ret = allocate( pfx, uriCode, lnam );
        }
        return ret;
    }
    public short allocateCodeForPrefix( final String pfx ) {
        final short ret;
        done :
        {
            // TODO: this search can be quite lengthy (typically 9 entries) - use some better approach.
            for ( short i = 0; i < prefixesUsed; i++ ) {
                if ( prefixes[ i ].equals( pfx ) ) {
                    ret = i;
                    break done;
                }
            }
            if ( prefixesUsed >= prefixes.length ) {
                if ( prefixesUsed > 32000 ) {
                    throw new NamePoolLimitException( "Too many namespace prefixes" );
                }
                final String[] p = new String[ prefixesUsed * 2 ];
                System.arraycopy( prefixes, 0, p, 0, prefixesUsed );
                prefixes = p;
            }
            prefixes[ prefixesUsed ] = pfx;
            ret = prefixesUsed++;
        }
        return ret;
    }
    public short allocateCodeForURI( final String uri ) {
        //System.err.println("allocate code for URI " + uri);
        final short ret;
        done :
        {
            for ( short j = 0; j < urisUsed; j++ ) {
                if ( uris[ j ].equals( uri ) ) {
                    ret = j;
                    break done;
                }
            }
            if ( urisUsed >= uris.length ) {
                if ( urisUsed > 32000 ) {
                    throw new NamePoolLimitException( "Too many namespace URIs" );
                }
                final String[] p = new String[ urisUsed * 2 ];
                final String[] u = new String[ urisUsed * 2 ];
                System.arraycopy( prefixesForUri, 0, p, 0, urisUsed );
                System.arraycopy( uris, 0, u, 0, urisUsed );
                prefixesForUri = p;
                uris = u;
            }
            uris[ urisUsed ] = uri;
            prefixesForUri[ urisUsed ] = "";
            ret = urisUsed++;
        }
        return ret;
    }
    public int allocateDocumentNumber( final DocumentInfo doc ) {
        if ( documentNumberMap == null ) {
            // this can happen after deserialization
            documentNumberMap = new java.util.WeakHashMap( 10 );
        }

        final Integer nr = ( Integer )documentNumberMap.get( doc );
        final int ret;
        if ( nr!=null ) {
            ret = nr.intValue();
        } else {
            int next = numberOfDocuments++;
            final Integer i = new Integer( next );
            documentNumberMap.put( doc, i );
            ret = next;
        }
        return ret;
    }
    public int allocateNamespaceCode( final int ncode ) {
        final short uriCode;
        final int fp = ncode & 0xfffff;
        
        final int ret;
        done : 
        {
            if ( ( fp & 0xffc00 ) == 0 ) {
                uriCode = StandardNames.getURICode( fp );
            } else {
                final NameEntry entry = getNameEntry( ncode );
                if ( entry == null ) {
                    unknownNameCode( ncode );
                    ret = -1;
                    break done;
                } else {
                    uriCode = entry.uriCode;
                }
            }
            final int pfxIdx = ( ncode >> 20 ) & 0xff;
            final String pfx = getPrefixWithIndex( uriCode, pfxIdx );
            final int pfxCd = allocateCodeForPrefix( pfx );
            ret =( pfxCd << 16 ) + uriCode;
        }
        return ret;
    }
    public int allocateNamespaceCode( final String pfx, final String uri ) {
        // System.err.println("allocate nscode for " + prefix + " = " + uri);

        final int prefixCode = allocateCodeForPrefix( pfx );
        final int uriCode = allocateCodeForURI( uri );

        if ( prefixCode != 0 ) {
            // ensure the prefix is in the list of prefixes used with this URI
            final int pfxLen = pfx.length();
            final char[] keyArr = new char[ pfxLen + 1 ];

            pfx.getChars( 0, pfxLen, keyArr, 0 );
            keyArr[ pfxLen ] = ' ';

            final String key = new String( keyArr );

            if ( prefixesForUri[ uriCode ].indexOf( key ) < 0 ) {
                final String pfxs = prefixesForUri[ uriCode ];
                final int pfxsLen = pfxs.length();
                final char[] pfxsArr = new char[ pfxsLen + keyArr.length ];

                pfxs.getChars( 0, pfxsLen, pfxsArr, 0 );
                System.arraycopy( keyArr, 0, pfxsArr, pfxsLen, keyArr.length );

                prefixesForUri[ uriCode ] = new String( pfxsArr );
            }
        }
        return ( prefixCode << 16 ) + uriCode;
    }
    public void diagnosticDump() {
        System.err.println( "Contents of NamePool " + this );
        for ( int i = 0; i < 1024; i++ ) {
            NameEntry entry = hashslots[ i ];
            int depth = 0;
            while ( entry != null ) {
                System.err.println( "Fingerprint " + depth + '/' + i );
                System.err.println
                    ( "  local name = " + entry.localName + " uri code = " + entry.uriCode );
                entry = entry.nextEntry;
                depth++;
            }
        }

        for ( int p = 0; p < prefixesUsed; p++ ) {
            System.err.println( "Prefix " + p + " = " + prefixes[ p ] );
        }
        for ( int u = 0; u < urisUsed; u++ ) {
            System.err.println( "URI " + u + " = " + uris[ u ] );
            System.err.println( "Prefixes for URI " + u + " = " + prefixesForUri[ u ] );
        }
    }
    */
}