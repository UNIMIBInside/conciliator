package com.codefork.refine.geonames;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

@Component("geonames")
public class Geonames extends WebServiceDataSource {

    private final ObjectMapper mapper = new ObjectMapper();
//    private static final NameType featureType = new NameType("http://www.geonames.org/ontology#Feature", "Feature");

    @Autowired
    public Geonames(Config config, CacheManager cacheManager, ThreadPoolFactory threadPoolFactory, ConnectionFactory connectionFactory) {
        super(config, cacheManager, threadPoolFactory, connectionFactory);
    }

    @Override
    public ServiceMetaDataResponse createServiceMetaDataResponse(String baseUrl) {
        return new GeonamesMetaDataResponse(getName());
    }

    private String createQuery(SearchQuery query) {
        Properties props = getConfig().getProperties();
        String userKey = null;
        if (props.containsKey("geonames.key")) {
            userKey = props.getProperty("geonames.key");
        }
        if (userKey == null) {
            return null;
        }

        String text = query.getQuery();
        // Split camel case strings
        text = String.join(" ", text.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])"));

        return String.format("q=%s" +
                "&maxRows=10" +
                "&username=%s", text, userKey);
    }

    @Override
    public List<Result> search(SearchQuery query) throws Exception {
        List<Result> results = new ArrayList<>();
        String url = new URI("http", "api.geonames.org", "/searchJSON", createQuery(query), null).toURL().toString();

        HttpURLConnection conn = getConnectionFactory().createConnection(url);

        JsonNode root = mapper.readTree(conn.getInputStream());
        JsonNode annotations = root.get("geonames");
        if (annotations.isArray()) {
            Iterator<JsonNode> iter = annotations.iterator();
            while (iter.hasNext() && results.size() < query.getLimit()) {
                JsonNode doc = iter.next();
                String title = doc.get("toponymName").asText();
                int key = doc.get("geonameId").asInt();
                String featureClass = doc.get("fcl").asText();
                String clsCode = featureClass;
                if (doc.get("fcode") != null) { // append feature code, if exists
                    String featureCode = doc.get("fcode").asText();
                    clsCode = featureClass + "." + featureCode;
                }
                NameType featureType = new NameType("http://www.geonames.org/ontology#" + clsCode, clsCode);
                if (query.getTypeStrict() != null && query.getTypeStrict().equals("should")) {
                    if (query.getNameType().getId().equals(featureType.getId())) {
                        results.add(new Result(Integer.toString(key), title, featureType, 1, false));
                    }
                } else {
                    results.add(new Result(Integer.toString(key), title, featureType, 1, false));
                }
            }
        }

        return results;
    }
}
