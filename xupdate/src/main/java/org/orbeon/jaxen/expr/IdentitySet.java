/*
 $Id$

 Copyright 2005 Elliotte Rusty Harold. All Rights Reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.

  * Neither the name of the Jaxen Project nor the names of its
    contributors may be used to endorse or promote products derived
    from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */
package org.orbeon.jaxen.expr;

import java.util.HashSet;


/**
 * <p>
 *  This is a set that uses identity rather than equality semantics.
 * </p>
 *
 * @author Elliotte Rusty Harold
 *
 */
final class IdentitySet {

    private HashSet contents = new HashSet();

    IdentitySet() {
        super();
    }

    void add(Object object) {
        IdentityWrapper wrapper = new IdentityWrapper(object);
        contents.add(wrapper);
    }

    public boolean contains(Object object) {
        IdentityWrapper wrapper = new IdentityWrapper(object);
        return contents.contains(wrapper);
    }

    private static class IdentityWrapper {

        private Object object;

        IdentityWrapper(Object object) {
            this.object = object;
        }

        public boolean equals(Object o) {
            IdentityWrapper w = (IdentityWrapper) o;
            return object == w.object;
        }

        public int hashCode() {
            return System.identityHashCode(object);
        }

    }


}
