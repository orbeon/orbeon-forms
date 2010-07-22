/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xml;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.NumberUtils;

import java.security.MessageDigest;
import java.util.*;

/**
 * Optimized namespace mapping that includes a hash.
 *
 * This helps with memory use and with getting XPath expressions from cache. For optimal use, the mappings should be
 * kept around so the hash doesn't have to be recomputed over and over.
 */
public class NamespaceMapping {

    public static NamespaceMapping EMPTY_MAPPING = new NamespaceMapping(Collections.<String, String> emptyMap());

    public final String hash;
    public final Map<String, String> mapping;

    // NOTE: mapping MUST already be sorted
    public NamespaceMapping(String hash, Map<String, String> mapping) {

        assert hash != null;
        assert mapping != null;

        this.hash = hash;
        this.mapping = mapping;
    }

    public NamespaceMapping(Map<String, String>  mapping) {

        assert mapping != null;

        final TreeMap<String, String> sortedMapping = new TreeMap<String, String>();
        sortedMapping.putAll(mapping);

        this.hash = NamespaceMapping.hashMapping(sortedMapping);
        this.mapping = sortedMapping;
    }

    public static String hashMapping(Map<String, String> sortedMapping) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA1");
            for (final Map.Entry<String, String> sortedEntry : sortedMapping.entrySet()) {
                digest.update(sortedEntry.getKey().getBytes("UTF-8"));
                digest.update((byte) ' ');
                digest.update(sortedEntry.getValue().getBytes("UTF-8"));
                digest.update((byte) ' ');
            }

            return NumberUtils.toHexString(digest.digest());
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
