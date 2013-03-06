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
package org.orbeon.oxf.test.beans;

public class House {
    private String name;
    private int stories;
    private String address;
    private boolean condo;
    private Window[] windows;

    public House() {}

    public House(String name) {
        this.name = name;
        this.stories = 3;
        this.address = "10 Mountain Side Street";
        this.condo = false;
        this.windows = new Window[]{new Window("East"), new Window("South")};
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public boolean isCondo() {
        return condo;
    }

    public int getStories() {
        return stories;
    }

    public Window[] getWindows() {
        return windows;
    }
}