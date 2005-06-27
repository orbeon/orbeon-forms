/**
 *
 */
package org.orbeon.oxf.util;

import java.util.Iterator;

/**
 * Iterator over an empty collection.
 */
public class EmptyIterator implements Iterator {

    private static Iterator instance = new EmptyIterator();

    public static Iterator getInstance() {
        return instance;
    }

    private EmptyIterator() {
    }

    public boolean hasNext() {
        return false;
    }

    public Object next() {
        return null;
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}
