package com.codefork.refine.geonames;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDBException;
import com.arangodb.entity.BaseDocument;
import com.arangodb.util.MapBuilder;
import com.codefork.refine.Config;
import com.codefork.refine.SearchQuery;
import com.codefork.refine.ThreadPoolFactory;
import com.codefork.refine.datasource.ConnectionFactory;
import com.codefork.refine.datasource.WebServiceDataSource;
import com.codefork.refine.resources.NameType;
import com.codefork.refine.resources.Result;
import com.codefork.refine.resources.ServiceMetaDataResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.apache.commons.text.similarity.SimilarityScore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.*;

@Component("geonames")
public class Geonames extends WebServiceDataSource {

    private static final String PROP_ARANGO_HOST = "dbhost";
    private static final String PROP_ARANGO_PORT = "dbport";
    private static final String PROP_ARANGO_DBNAME = "dbname";
    private static final String PROP_ARANGO_USER = "dbuser";

    private final String dbName = getConfigProperties().getProperty(PROP_ARANGO_DBNAME);
    private ArangoDB arangoDB;

    private final double threshold = 0.9;

    @Autowired
    public Geonames(Config config, CacheManager cacheManager, ThreadPoolFactory threadPoolFactory, ConnectionFactory connectionFactory) {
        super(config, cacheManager, threadPoolFactory, connectionFactory);

        String arangoHost = getConfigProperties().getProperty(PROP_ARANGO_HOST);
        int arangoPort = Integer.parseInt(getConfigProperties().getProperty(PROP_ARANGO_PORT));
        String dbUser = getConfigProperties().getProperty(PROP_ARANGO_USER);
        this.arangoDB = new ArangoDB.Builder().host(arangoHost, arangoPort).user(dbUser).build();
    }

    @Override
    public ServiceMetaDataResponse createServiceMetaDataResponse(String baseUrl) {
        return new GeonamesMetaDataResponse(getName());
    }

    private Result getResultFromIdentifier(String identifier) {

        try {
            String title = null;
            List<NameType> nameTypes = new ArrayList<>();

            final String aqlQuery = "FOR feature IN `geonames-es`\n" +
                    "    FILTER feature.`@id` == @identifier\n" +
                    "    return {\n" +
                    "    \"id\": feature.`@id`,\n" +
                    "    \"names\": feature.`http://www.geonames.org/ontology#name`[*].`@value`,\n" +
                    "    \"featureCodes\": feature.`http://www.geonames.org/ontology#featureCode`[*].`@id`\n" +
                    "}";
            final Map<String, Object> bindVars = new MapBuilder().put("identifier", identifier).get();
            final ArangoCursor<BaseDocument> cursor = this.arangoDB.db(dbName).query(aqlQuery, bindVars, null,
                    BaseDocument.class);

            for (; cursor.hasNext();) {
                BaseDocument doc = cursor.next();

                List<String> names = (ArrayList<String>)doc.getAttribute("names");
                title = names.get(0);

                ArrayList<String> featureCodes = (ArrayList<String>)doc.getAttribute("featureCodes");
                for (String featureCode : featureCodes) {
                    String featureCodeId = featureCode.substring(featureCode.lastIndexOf('#') + 1);
                    nameTypes.add(new NameType(featureCodeId, featureCodeId));
                }

            }

            if (title != null && !nameTypes.isEmpty()) {

                // entities ids are stored as http://sws.geonames.org/{{id}}/
                identifier = identifier.split("/")[3];
                return new Result(identifier, title, nameTypes, 1, true);
            }

        } catch (final ArangoDBException e) {
            System.err.println("Failed to execute query. " + e.getMessage());
        }

        return null;
    }

    private List<Result> matchingByIdentifier(SearchQuery query) {
        List<Result> results = new ArrayList<>();

        Result res = this.getResultFromIdentifier(query.getQuery());
        if (res != null) {
            results.add(res);
        }

        return results;
    }

    private List<Result> matchingByLookup(SearchQuery query) {

        List<Result> results = new ArrayList<>();

        String queryText = query.getQuery();
        // Split camel case strings
        queryText = String.join(" ", queryText.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])"));

        // execute AQL queries
        try {
            final String aqlQuery = "for feature in `geonames-es`\n" +
                    "return {\n" +
                    "    \"id\": feature.`@id`,\n" +
                    "    \"names\": APPEND(feature.`http://www.geonames.org/ontology#name`[*].`@value`, " +
                    "                      feature.`http://www.geonames.org/ontology#alternateName`[*].`@value`, " +
                    "                      true)\n" +
                    "}";
            final ArangoCursor<BaseDocument> cursor = arangoDB.db(dbName).query(aqlQuery, null, null,
                    BaseDocument.class);

            SimilarityScore<Double> jw = new JaroWinklerDistance();

            for (; cursor.hasNext();) {
                BaseDocument doc = cursor.next();
                String identifier = (String)doc.getAttribute("id");
                List<String> names = (ArrayList<String>)doc.getProperties().get("names");

                for (String name : names) {
                    double similarity = jw.apply(queryText, name);

                    if (similarity > threshold) {
                        Result res = this.getResultFromIdentifier(identifier);
                        if (res != null) {
                            res.setScore(similarity);
                            res.setMatch(false);
                            results.add(res);
                        }
                        break;
                    }
                }
            }
        } catch (final ArangoDBException e) {
            System.err.println("Failed to execute query. " + e.getMessage());
        }

        return results;
    }

    @Override
    public List<Result> search(SearchQuery query) {

        List<Result> results;

        if (StringUtils.isNumeric(query.getQuery())) {
            results = this.matchingByIdentifier(query);
        } else {
            results = this.matchingByLookup(query);
        }

        // Remove entities of non-allowed types
        if (query.getTypeStrict() != null && query.getTypeStrict().equals("should")) {
            NameType selectedType = new NameType(query.getNameType().getId(), null);
            results.removeIf(obj -> !obj.getType().contains(selectedType));
        }

        // Sort the results based on their score
        results.sort(Comparator.comparingDouble(Result::getScore).reversed());

        // Match if the first result score is equal to 1.0 or
        // if there is only one result with a score greater than the threshold
        if (results.size() > 0 && results.get(0).getScore() == 1.0 ||
                results.size() == 1 && results.get(0).getScore() > threshold) {
            results.get(0).setMatch(true);
        }

        return results;
    }
}
