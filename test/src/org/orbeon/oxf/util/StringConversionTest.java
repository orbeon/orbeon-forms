package org.orbeon.oxf.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class StringConversionTest
{
    @Test
    public void testAddValueToStringArrayMap() {
        Map<String, String[]> actual = new HashMap<String, String[]>();
        StringConversions.addValueToStringArrayMap(actual, "key", "value");
        StringConversions.addValueToStringArrayMap(actual, "key2", "value2");
        StringConversions.addValueToStringArrayMap(actual, "key2", "value3");
        
        Map<String, String[]> expected = new HashMap<String, String[]>();
        expected.put("key", new String[]{"value"});
        expected.put("key2", new String[]{"value2", "value3"});
        
        assertMapEquals(expected, actual);
    }
    
    private void assertMapEquals(Map<String, String[]> expectedMap, Map<String, String[]> actualMap) {
        for(Map.Entry<String, String[]> expectedEntry : expectedMap.entrySet()) {
            String key = expectedEntry.getKey();
            String[] actual = actualMap.get(key);
            assertArrayEquals(expectedEntry.getValue(), actual);
            actualMap.remove(key);
        }
        assertEquals("Actual map contains extra elements", 0, actualMap.size());
    }

    @Test
    public void testAddValuesToStringArrayMap() {
        Map<String, String[]> actual = new HashMap<String, String[]>();
        StringConversions.addValuesToStringArrayMap(actual, "key", new String[]{"value"});
        StringConversions.addValuesToStringArrayMap(actual, "key2", new String[]{"value2", "value3"});
        StringConversions.addValuesToStringArrayMap(actual, "key2", new String[]{"value4", "value5"});
        
        Map<String, String[]> expected = new HashMap<String, String[]>();
        expected.put("key", new String[]{"value"});
        expected.put("key2", new String[]{"value2", "value3", "value4", "value5"});
        
        assertMapEquals(expected, actual);
    }
}