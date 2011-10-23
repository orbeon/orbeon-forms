/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.xbl

import org.orbeon.oxf.xforms.Caches
import net.sf.ehcache.{Element => EhElement}
import org.dom4j.QName

// Cache binding path/QName -> AbstractBinding
object BindingCache {

    private val cache = Caches.xblCache

    private def cacheKey(path: String, name: QName) = path  + '#' + name.getQualifiedName

    def put(path: String, name: QName, lastModified: Long, abstractBinding: AbstractBinding) = synchronized {
        val key = cacheKey(path, name)
        cache.put(new EhElement(key, abstractBinding, lastModified))
    }

    def get(path: String, name: QName, lastModified: Long): Option[AbstractBinding] = synchronized {
        val key = cacheKey(path, name)
        Option(cache.get(key)) flatMap { element =>
            // NOTE: As of Ehcache 2.4.0, the version attribute is entirely handled by the caller. See:
            // http://jira.terracotta.org/jira/browse/EHC-666
            val cacheLastModified = element.getVersion
            if (lastModified <= cacheLastModified) {
                Some(element.getValue.asInstanceOf[AbstractBinding])
            } else {
                cache.remove(key)
                None
            }
        }
    }
}