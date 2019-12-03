package com.codefork.refine.wikifier;

import com.codefork.refine.ApplicationConfig;
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

@Component("wikifier")
public class Wikifier extends WebServiceDataSource {

    private final ObjectMapper mapper = new ObjectMapper();
    private String apiToken;

    @Autowired
    public Wikifier(ApplicationConfig config, WikifierConfig wikifierConfig, CacheManager cacheManager, ThreadPoolFactory threadPoolFactory, ConnectionFactory connectionFactory) {
        super(config, cacheManager, threadPoolFactory, connectionFactory);
        this.apiToken = wikifierConfig.getApiToken();
    }

    @Override
    public ServiceMetaDataResponse createServiceMetaDataResponse(String baseUrl) {
        return new WikifierMetaDataResponse(getName());
    }

    @Override
    public String getName() {
        return "Wikifier For Tables";
    }

    private String createQuery(SearchQuery query) {
        String text = query.getQuery();
        // Split camel case strings
        text = String.join(" ", text.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])"));

        return String.format("text=%s" +
                "&userKey=%s" +
                "&lang=de" +
                "&maxMentionEntropy=3" +
                "&minLinkFrequency=2",
                text,
                this.apiToken);
    }

    @Override
    public List<Result> search(SearchQuery query) throws Exception {
        List<Result> results = new ArrayList<>();
        String url = new URI("http", "wikifier.org", "/annotate-article/", createQuery(query), null).toURL().toString();

        HttpURLConnection conn = getConnectionFactory().createConnection(url);

        JsonNode root = mapper.readTree(conn.getInputStream());
        JsonNode annotations = root.get("annotations");
        if (annotations.isArray()) {
            Iterator<JsonNode> iter = annotations.iterator();
            while (iter.hasNext() && results.size() < query.getLimit()) {
                JsonNode doc = iter.next();
                String title = doc.get("title").asText();
                String key = doc.get("dbPediaIri").asText().replace("http://dbpedia.org/resource/","");
                JsonNode types = doc.get("dbPediaTypes");
                List<NameType> nameTypes = new ArrayList<>();
                for (JsonNode type : types) {
                    nameTypes.add(new NameType(type.asText(), type.asText()));
                }
                Double pageRank = doc.get("pageRank").asDouble();
                results.add(new Result(key, title, nameTypes, pageRank, false));
            }
        }
        return results;
    }
}
