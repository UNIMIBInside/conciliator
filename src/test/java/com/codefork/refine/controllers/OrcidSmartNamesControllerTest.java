package com.codefork.refine.controllers;

import com.codefork.refine.datasource.ConnectionFactory;
import com.codefork.refine.datasource.SimulatedConnectionFactory;
import com.codefork.refine.orcid.OrcidSmartNames;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class OrcidSmartNamesControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        ConnectionFactory connectionFactory() {
            return new SimulatedConnectionFactory();
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    public void testSmartNamesServiceMetaData() throws Exception {
        MvcResult result = mvc.perform(get("/reconcile/orcid/smartnames")).andReturn();

        String body = result.getResponse().getContentAsString();

        JsonNode root = new ObjectMapper().readTree(body);

        assertEquals("ORCID - Smart Names Mode", root.get("name").asText());
    }
    // https://github.com/codeforkjeff/conciliator/issues/8
    @Test
    public void testLiveSearchSmartNames() throws Exception {

        String json = "{\"q0\":{\"query\": \"Igor Ozerov\",\"type\":\"/people/person\",\"type_strict\":\"should\"}}";

        MvcResult mvcResult = mvc.perform(get("/reconcile/orcid/smartnames").param("queries", json)).andReturn();

        String body = mvcResult.getResponse().getContentAsString();

        JsonNode root = new ObjectMapper().readTree(body);

        JsonNode results = root.get("q0").get("result");

        assertEquals(1, results.size());

        JsonNode result1 = results.get(0);
        assertEquals("Igor OZEROV", result1.get("name").asText());
        assertEquals("Person", result1.get("type").get(0).get("name").asText());
        assertEquals("0000-0001-5839-7854", result1.get("id").asText());
        assertFalse(result1.get("match").asBoolean());
    }

    @Test
    public void testParseName() {
        assertArrayEquals(OrcidSmartNames.parseName("joe schmoe"),
                new String[] { "joe", "schmoe" });
        assertArrayEquals(OrcidSmartNames.parseName("schmoe, joe"),
                new String[] { "joe", "schmoe" });
        assertArrayEquals(OrcidSmartNames.parseName("dr. joe schmoe"),
                null);
    }
}
