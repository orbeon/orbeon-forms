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
package org.orbeon.oxf.processor.converter;

import org.orbeon.oxf.processor.serializer.legacy.SVGSerializer;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;

/**
 * Converts XML in SVG format into a binary image.
 */
public class SVGConverter extends SVGSerializer {
    public SVGConverter() {
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }
}
