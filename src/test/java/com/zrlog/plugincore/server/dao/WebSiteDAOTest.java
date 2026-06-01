package com.zrlog.plugincore.server.dao;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WebSiteDAOTest {

    @Test
    public void shouldTreatStoredTextAndTypedValueAsSame() {
        assertTrue(WebSiteDAO.sameStoredValue("true", Boolean.TRUE));
        assertTrue(WebSiteDAO.sameStoredValue("10", 10));
    }

    @Test
    public void shouldGenerateCasVersionRemark() {
        assertTrue(WebSiteDAO.nextVersionRemark().startsWith("cas:"));
    }

    @Test
    public void shouldFilterUnchangedValues() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("flag", "true");
        existing.put("title", "Old");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("flag", Boolean.TRUE);
        values.put("title", "New");
        values.put("missing", "value");

        Map<String, Object> changedValues = WebSiteDAO.changedValues(existing, values);

        assertEquals(2, changedValues.size());
        assertEquals("New", changedValues.get("title"));
        assertEquals("value", changedValues.get("missing"));
    }
}
