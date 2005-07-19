/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.util;

/**
 * <!--  NullFriendlyStringComparator -->
 * String comparator that allows for null.  null is less than empty string is less than non
 * empty strings. 
 * @author d
 */
public class NullFriendlyStringComparator implements java.util.Comparator {
    /**
     * <!-- instance -->
     * What you think.
     * @author d
     */
    public static final NullFriendlyStringComparator instance = new NullFriendlyStringComparator();
    /**
     * <!-- NullFriendlyStringComparator -->
     * What you think.
     * @see #instance
     * @author d
     */
    private NullFriendlyStringComparator() {
        // no construction
    }
    /**
     * <!-- compare -->
     * @see NullFriendlyStringComparator
     * @author d
     */
    public int compare( final Object lhsObj, final Object rhsObj ) {
        final String lhs = ( String )lhsObj;
        final String rhs = ( String )rhsObj;
        final int ret;
        if ( lhs == null ) {
            ret = rhs == null ? 0 : -1;
        } else if ( rhs == null ) {
            ret = 1;
        } else {
            ret = lhs.compareTo( rhs );
        }
        return ret;
    }
    
}