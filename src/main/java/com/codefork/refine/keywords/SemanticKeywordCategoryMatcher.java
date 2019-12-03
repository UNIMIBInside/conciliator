package com.codefork.refine.keywords;

import com.codefork.refine.Config;
import com.codefork.refine.SearchQuery;
import com.codefork.refine.ThreadPoolFactory;
import com.codefork.refine.datasource.ConnectionFactory;
import com.codefork.refine.datasource.WebServiceDataSource;
import com.codefork.refine.resources.NameType;
import com.codefork.refine.resources.Result;
import com.codefork.refine.resources.ServiceMetaDataResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

@Component("keywordsMatcher")
public class SemanticKeywordCategoryMatcher extends WebServiceDataSource {


    private static final String SERVICE_HOST = "host";
    private static final String SERVICE_PORT = "port";

    private final String host = getConfigProperties().getProperty(SERVICE_HOST);
    private final int port = Integer.parseInt(getConfigProperties().getProperty(SERVICE_PORT));
    private final ObjectMapper mapper = new ObjectMapper();

    public SemanticKeywordCategoryMatcher(Config config, CacheManager cacheManager, ThreadPoolFactory threadPoolFactory, ConnectionFactory connectionFactory) {
        super(config, cacheManager, threadPoolFactory, connectionFactory);
    }

    @Override
    public List<Result> search(SearchQuery query) throws Exception {
        List<Result> results = new ArrayList<>();
        if (query.getQuery().isEmpty()) return results;

        String url = new URI("http", null, host, port, "/categorise_keywords", null, null).toURL().toString();

        HttpURLConnection connection = getConnectionFactory().createConnection(url);

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoInput(true);
        connection.setDoOutput(true); // for enabling POST requests



        String jsonInputString = "{\"keywords\": [\""+query.getQuery()+"\"]}";

        try(OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }


        JsonNode root = mapper.readTree(new InputStreamReader(connection.getInputStream(), "utf-8"));
        if (root.isArray()) { // it will have only one element actually.
            JsonNode elem = root.get(0);
            JsonNode categories = elem.get("categories");
            if (categories.isArray()) {
                Iterator<JsonNode> iter = categories.iterator();
                while (iter.hasNext() && results.size() < query.getLimit()) {
                    JsonNode doc = iter.next();
                    String categoryName = doc.get("category").asText();
                    String categoryId = doc.get("id").asText();
                    NameType nameType = new NameType("http://www.w3.org/2004/02/skos/core#Concept", "Concept");
                    double score = doc.get("distance").asDouble();
                    results.add(new Result(categoryId, categoryName, nameType, 1-score, false));
                }
            }
        }
        return results;
    }


    @Override
    public ServiceMetaDataResponse createServiceMetaDataResponse(String baseUrl) {
        return new SemanticKeywordCategoryMatcherMetaDataResponse(getName());
    }

    @Override
    public String getName() {
        return "Matched Google Categories";
    }

}
