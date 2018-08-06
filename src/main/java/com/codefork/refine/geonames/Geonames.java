package com.codefork.refine.geonames;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDBException;
import com.arangodb.entity.BaseDocument;
import com.arangodb.util.MapBuilder;
import com.codefork.refine.Config;
import com.codefork.refine.PropertyValueIdAndSettings;
import com.codefork.refine.SearchQuery;
import com.codefork.refine.ThreadPoolFactory;
import com.codefork.refine.datasource.ConnectionFactory;
import com.codefork.refine.datasource.WebServiceDataSource;
import com.codefork.refine.resources.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.apache.commons.text.similarity.SimilarityScore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

            final String aqlQuery = "FOR feature IN `geonames-de`\n" +
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

        String queryText = query.getQuery().replaceAll("-", " "); // remove dashes (ARANGO escapes them while indexing)
        // Split camel case strings
//        queryText = String.join(" ", queryText.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])"));

        // execute AQL queries
        try {

            // Try to find candidates by executing a fulltext query
            final String aqlFulltextQuery = "for feature in FULLTEXT(`geonames-de`, \"allNames\", @queryText)\n" +
                    "return {\n" +
                    "    \"id\": feature.`@id`, \n" +
                    "    \"names\": feature.allNames\n" +
                    "    }";
            Map<String, Object> bindVars = new MapBuilder().put("queryText", queryText).get();
            ArangoCursor<BaseDocument> cursor = arangoDB.db(dbName).query(aqlFulltextQuery, bindVars, null,
                    BaseDocument.class);

            // In case of no results, try searching terms by using OR op
            if (!cursor.hasNext()) {
                queryText = queryText.replaceAll(" ", ",|");
                bindVars.put("queryText", queryText);
                cursor = arangoDB.db(dbName).query(aqlFulltextQuery, bindVars, null,
                        BaseDocument.class);
            }

            // In case of empty results, get all the features available in Geonames
//            if (!cursor.hasNext()) {
//                final String aqlQuery = "for feature in `geonames-de`\n" +
//                        "return {\n" +
//                        "    \"id\": feature.`@id`,\n" +
//                        "    \"names\": feature.allNames\n" +
//                        "}";
//                cursor = arangoDB.db(dbName).query(aqlQuery, null, null,
//                        BaseDocument.class);
//            }

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

    @Override
    public CellList extend(String id, List<PropertyValueIdAndSettings> idsAndSettings) {
        CellList<String> cl = new CellList<>();

        String geonamesId = "http://sws.geonames.org/" + id + "/";
        BaseDocument doc = null;
        try {
            final String aqlQuery = "for feature in `geonames-de`\n" +
                    "FILTER feature.`@id` == @geonamesId\n" +
                    "return feature";
            Map<String, Object> bindVars = new MapBuilder().put("geonamesId", geonamesId).get();
            ArangoCursor<BaseDocument> cursor = arangoDB.db(dbName).query(aqlQuery, bindVars, null,
                    BaseDocument.class);

            if (cursor.hasNext()) {
                doc = cursor.next();
            }
        } catch (final ArangoDBException e) {
            System.err.println("Failed to execute query. " + e.getMessage());
        }

        if (doc != null) {
            for (PropertyValueIdAndSettings pv: idsAndSettings) {

                if (doc.getProperties().containsKey("http://www.geonames.org/ontology#" + pv.getId())) {

                    cl.put(pv.getId(), new ArrayList<>());

                    ArrayList<Map<String, Object>> propertyObjects = (ArrayList<Map<String, Object>>)
                            doc.getAttribute("http://www.geonames.org/ontology#" + pv.getId());

                    for (Map<String, Object> objects: propertyObjects) {
                        if (objects.containsKey("@id")) {
                            String objectId = objects.get("@id").toString();
                            Result result = this.getResultFromIdentifier(objectId);
                            if (result != null) {
                                cl.get(pv.getId()).add(new Cell(result.getId(), result.getName()));
                            }
                        }
                    }
                }
            }
        }
        return cl;
    }
    
}
