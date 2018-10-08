package com.codefork.refine.geonames;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDBException;
import com.arangodb.entity.BaseDocument;
import com.arangodb.util.MapBuilder;
import com.codefork.refine.ApplicationConfig;
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

    private String dbName;
    private ArangoDB arangoDB;

    @Autowired
    public Geonames(ApplicationConfig config, GeonamesConfig geonamesConfig, CacheManager cacheManager, ThreadPoolFactory threadPoolFactory, ConnectionFactory connectionFactory) {
        super(config, cacheManager, threadPoolFactory, connectionFactory);

        String arangoHost = geonamesConfig.getHost();
        int arangoPort = geonamesConfig.getPort();
        String dbUser = geonamesConfig.getUsername();
        String dbPwd = geonamesConfig.getPassword();
        this.arangoDB = new ArangoDB.Builder().host(arangoHost, arangoPort).user(dbUser).password(dbPwd).build();
        this.dbName = geonamesConfig.getDbName();
    }

    @Override
    public ServiceMetaDataResponse createServiceMetaDataResponse(String baseUrl) {
        return new GeonamesMetaDataResponse(getName());
    }

    @Override
    public String getName() {
        return "GeoNames";
    }

    private Result getResultFromIdentifier(String identifier) {

        try {
            final String aqlQuery = "FOR feature IN `geonames-de`\n" +
                    "    FILTER feature.`@id` == @identifier\n" +
                    "    return {\n" +
                    "    \"id\": feature.`@id`,\n" +
                    "    \"name\": feature.`http://www.geonames.org/ontology#name`[0].`@value`,\n" +
                    "    \"featureClass\": feature.`http://www.geonames.org/ontology#featureClass`[0].`@id`,\n" +
                    "    \"featureCode\": feature.`http://www.geonames.org/ontology#featureCode`[0].`@id`\n" +
                    "}";
            final Map<String, Object> bindVars = new MapBuilder().put("identifier", identifier).get();
            final ArangoCursor<BaseDocument> cursor = this.arangoDB.db(dbName).query(aqlQuery, bindVars, null,
                    BaseDocument.class);

            if (cursor.hasNext()) {
                BaseDocument doc = cursor.next();
                String title = doc.getAttribute("name").toString();

                String featureCode;
                if (doc.getAttribute("featureCode") != null) {
                    featureCode = doc.getAttribute("featureCode").toString();
                } else {
                    featureCode = doc.getAttribute("featureClass").toString();
                }
                String featureCodeId = featureCode.substring(featureCode.lastIndexOf('#') + 1);
                NameType nameType = new NameType(featureCodeId, featureCodeId);

                // entities ids are stored as http://sws.geonames.org/{{id}}/
                identifier = identifier.split("/")[3];
                return new Result(identifier, title, nameType, 1, true);
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
                    "    \"name\": feature.`http://www.geonames.org/ontology#name`[0].`@value`,\n" +
                    "    \"featureCode\": feature.`http://www.geonames.org/ontology#featureCode`[0].`@id`,\n" +
                    "    \"featureClass\": feature.`http://www.geonames.org/ontology#featureClass`[0].`@id`,\n" +
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
                // Result ID - geonames entities ids are stored as http://sws.geonames.org/{{id}}/
                String identifier = doc.getAttribute("id").toString().split("/")[3];
                //Result Title
                String title = doc.getAttribute("name").toString();

                // Result nametype
                // Feature code is missing for some entities (e.g., http://sws.geonames.org/6324466/)
                String featureCode;
                if (doc.getAttribute("featureCode") != null) {
                    featureCode = doc.getAttribute("featureCode").toString();
                } else {
                    featureCode = doc.getAttribute("featureClass").toString();
                }
                String featureCodeId = featureCode.substring(featureCode.lastIndexOf('#') + 1);
                NameType nameType = new NameType(featureCodeId, featureCodeId);

                // Result score (the label with the highest similarity)
                List<String> names = (ArrayList<String>)doc.getProperties().get("names");
                double maxScore = .0;

                for (String name : names) {
                    double similarity = jw.apply(queryText, name);
                    if (similarity > maxScore) {
                        maxScore = similarity;
                    }
                }

                results.add(new Result(identifier, title, nameType, maxScore, false));
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
        if (results.size() > 0 && results.get(0).getScore() == 1.0) {
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

    @Override
    public ColumnMetaData columnMetaData(PropertyValueIdAndSettings prop) {
        // TODO: replace this query with a request to ABSTAT!
        try {
            final String aqlQuery = "let objects = (for feature in `geonames-de` return feature.@prop[0].`@id`)\n" +
                    "for feature in `geonames-de`\n" +
                    "    filter feature.`@id` in objects\n" +
                    "    COLLECT propGroup = feature.`http://www.geonames.org/ontology#featureCode`[0].`@id` WITH COUNT INTO numProps\n" +
                    "    sort propGroup DESC\n" +
                    "    return {propGroup, numProps}";
            getLog().debug(prop.getId());
            Map<String, Object> bindVars = new MapBuilder().put("prop", "http://www.geonames.org/ontology#" + prop.getId()).get();
            ArangoCursor<BaseDocument> cursor = arangoDB.db(dbName).query(aqlQuery, bindVars, null,
                    BaseDocument.class);

            if (cursor.hasNext()) {
                BaseDocument doc = cursor.next();
                String bestType = doc.getAttribute("propGroup").toString();
                ColumnMetaData col = new ColumnMetaData();
                col.setId(prop.getId());
                col.setName(prop.getId());
                col.setType(new NameType(bestType.replaceAll("http://www.geonames.org/ontology#", ""),
                        bestType.replaceAll("http://www.geonames.org/ontology#", "")));
                return col;
            }
        } catch (final ArangoDBException e) {
            System.err.println("Failed to execute query. " + e.getMessage());
        }

        return null;
    }
}
