package com.codefork.refine.geonames;

import com.codefork.refine.*;
import com.codefork.refine.resources.ObjectPV;
import com.codefork.refine.datasource.ConnectionFactory;
import com.codefork.refine.datasource.WebServiceDataSource;
import com.codefork.refine.resources.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.apache.commons.text.similarity.SimilarityScore;
import org.apache.http.HttpHost;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Resource;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.TDigestState;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.Double.NaN;

@Component("geonames")
public class Geonames extends WebServiceDataSource {

    /**
     * A private read-only object that represents a coordinate in the WGS 84 system.
     */
    private class Coordinate {
        private double latitude;
        private double longitude;

        Coordinate(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        boolean isValid() {
            return this.latitude <= 90 && this.latitude >= -90 && this.longitude <= 180 && this.longitude >= -180;
        }
    }

    private GeonamesConfig gnConfig;

    private RestHighLevelClient client;
    private String sparqlEndpoint;
    private String graphName;
    private String ontologyGraphName;

    // RDF prefixes for GeoNames ontology and resources
    private final String GN_ONTOLOGY_PREFIX = "http://www.geonames.org/ontology#";
    private final String GN_RESOURCE_PREFIX = "http://sws.geonames.org/";

    @Autowired
    public Geonames(ApplicationConfig config, GeonamesConfig geonamesConfig, CacheManager cacheManager, ThreadPoolFactory threadPoolFactory, ConnectionFactory connectionFactory) {
        super(config, cacheManager, threadPoolFactory, connectionFactory);

        HttpHost[] hosts = new HttpHost[geonamesConfig.getElastic().size()];
        for (int i = 0; i < hosts.length; ++i) {
            hosts[i] = new HttpHost(geonamesConfig.getElastic().get(i).getHost(),
                    geonamesConfig.getElastic().get(i).getPort(), "http");
        }

        this.gnConfig = geonamesConfig;

        this.client = new RestHighLevelClient(RestClient.builder(hosts));

        this.sparqlEndpoint = geonamesConfig.getVirtuoso().getEndpoint();
        this.graphName = geonamesConfig.getVirtuoso().getGraphName();
        this.ontologyGraphName = geonamesConfig.getVirtuoso().getOntologyGraphName();
    }

    @Override
    public ServiceMetaDataResponse createServiceMetaDataResponse(String baseUrl) {
        return new GeonamesMetaDataResponse(getName(), this.gnConfig.getProposeProperties());
    }

    @Override
    public String getName() {
        return "GeoNames";
    }

    /**
     * Return the URI of the GeoNames resource identified by the given ID
     *
     * @param id the id of a GeoNames resource
     * @return the URI of the resource identified by the given id
     */
    private String urifyGeoNamesId(String id) {
        // URIs of resources in GeoNames end with the /
        return String.format("%s%s/", GN_RESOURCE_PREFIX, id);
    }

    /**
     * Return the URI of the property identified by the given ID.
     * IDs which are already URI are returned as are, while other IDs
     * are transformed to GeoNames ontology properties.
     *
     * @param id a string containing the ID
     * @return the URI of the property identified by the given ID
     */
    private String urifyPropertyId(String id) {
        if (id.startsWith("http:")) {
            return id;
        }
        return GN_ONTOLOGY_PREFIX + id;
    }

    /**
     * Use a Source Map obtained from ElasticSearch (geonames index)
     * to build a new Result.
     * Result score and match are set to -1 and false, respectively.
     *
     * @param sourceMap a source map obtained from the "geonames" index
     * @return a new Result with score = -1 and match = false
     */
    private Result buildResultFromSourceMap(Map<String, Object> sourceMap) {

        // Feature code is missing for some entities (e.g., http://sws.geonames.org/6324466/)
        String featureCode = sourceMap.get("fclass").toString();
        if (sourceMap.get("fcode") != null) {
            featureCode += "." + sourceMap.get("fcode").toString();
        }

        return new Result(sourceMap.get("geonameid").toString(),
                sourceMap.get("name").toString(),
                new NameType(featureCode, featureCode));
    }

    private List<Result> getResultsFromElasticQueryBuilder(BoolQueryBuilder qb, SearchQuery query) {

        if (query.getTypeStrict() != null && query.getTypeStrict().equals("should")) {
            // The type is given as "class.code", or only "class" if the code is not available
            String[] typeSplit = query.getNameType().getId().split("\\.");
            if (typeSplit.length > 0) {
                qb.filter(QueryBuilders.termQuery("fclass", typeSplit[0]));
            }
            if (typeSplit.length == 2) {
                qb.filter(QueryBuilders.termQuery("fcode", typeSplit[1]));
            }
        }

        List<Result> results = new ArrayList<>();
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(qb);
        sourceBuilder.size(query.getLimit());

        SearchRequest searchRequest = new SearchRequest("geonames");
        searchRequest.source(sourceBuilder);

        try {
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            SearchHits hits = searchResponse.getHits();

            SearchHit[] searchHits = hits.getHits();
            for (SearchHit hit : searchHits) {

                Result r = buildResultFromSourceMap(hit.getSourceAsMap());
                r.setScore(hit.getScore());
                results.add(r);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Check if the given string could represent a GeoNames ID.
     * Since GeoNames IDs are integers, this method only check if
     * the given string represents a number.
     *
     * @param query a string
     * @return true if the string could represent a GeoNames ID (i.e., contains a number)
     */
    private boolean isGeonamesId(String query) {
        return StringUtils.isNumeric(query);
    }

    /**
     * Create a new Coordinate by reading a string.
     * A new Coordinate object is returned if the string matches
     * the "lat,long" format, and the coordinate is valid in the
     * WGS 84 system.
     *
     * @param s a string
     * @return a Coordinate object
     */
    private Coordinate getCoordinateFromString(String s) {
        try {
            String[] latLongStr = s.split(",");
            Coordinate c = new Coordinate(Double.parseDouble(latLongStr[0]), Double.parseDouble(latLongStr[1]));
            if (c.isValid()) {
                return c;
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private List<Result> matchingByIdentifier(SearchQuery query) {
        List<Result> results = new ArrayList<>();

        try {
            GetRequest getRequest = new GetRequest("geonames", "geoname", query.getQuery());
            GetResponse getResponse = client.get(getRequest, RequestOptions.DEFAULT);

            Result r = buildResultFromSourceMap(getResponse.getSourceAsMap());

            // Filter invalid result (i.e., right ID, but different type)
            if (query.getTypeStrict() == null || (query.getTypeStrict() != null && query.getTypeStrict().equals("should")
                    && query.getNameType().getId().equalsIgnoreCase(r.getType().get(0).getId()))) {
                r.setScore(1.);
                r.setMatch(true);
                results.add(r);
            }
        } catch (IOException e) {
            System.err.println("Failed to get document from index. " + e.getMessage());
        }

        return results;
    }

    private List<Result> matchingByLookup(SearchQuery query) {

        BoolQueryBuilder boolBuilder = QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery("name", query.getQuery().toLowerCase()).boost(2.0f))
                .should(QueryBuilders.matchQuery("alternatenames", query.getQuery().toLowerCase()))
                .should(QueryBuilders.matchQuery("asciiname", query.getQuery().toLowerCase()).boost(1.5f));

        return getResultsFromElasticQueryBuilder(boolBuilder, query);
    }

    private List<Result> matchingByCoordinate(SearchQuery query, Coordinate coordinate) {

        BoolQueryBuilder boolBuilder = QueryBuilders.boolQuery()
                .must(QueryBuilders.geoDistanceQuery("location")
                        .point(coordinate.latitude, coordinate.longitude)
                        .distance(10, DistanceUnit.KILOMETERS));

        return getResultsFromElasticQueryBuilder(boolBuilder, query);
    }

    private List<Result> matchingByProperties(SearchQuery query, List<Result> results) {
        List<Result> lr = new ArrayList<>();

        for (Result res : results) {

            String columnValue = "";
            StringBuilder queryString = new StringBuilder("Select distinct ?q where {\n");

            for (Map.Entry<String, PropertyValue> entry : query.getProperties().entrySet()) {
                if (entry.getValue() != null) {
                    if (entry.getKey().contains("|")) {
                        String p1 = entry.getKey().substring(0, entry.getKey().indexOf("|"));
                        String p2 = entry.getKey().substring(entry.getKey().indexOf("|") + 1);
                        queryString.append(String.format(
                                "<%s> <%s>/<%s> ?q .\n",
                                urifyGeoNamesId(res.getId()),
                                urifyPropertyId(p1),
                                urifyPropertyId(p2)));
                        columnValue = entry.getValue().asString();
                    } else {
                        queryString.append(String.format(
                                "<%s> <%s> ?q .\n",
                                urifyGeoNamesId(res.getId()),
                                urifyPropertyId(entry.getKey())));
                        columnValue = entry.getValue().asString();
                    }
                }

                queryString.append("}");
                Query sparqlQuery = QueryFactory.create(queryString.toString());
                try (QueryExecution qexec = QueryExecutionFactory.sparqlService(sparqlEndpoint, sparqlQuery, graphName, null, null)) {
                    ResultSet sparqlResults = qexec.execSelect();
                    double threshold = entry.getValue().opt.getThreshold();//User's confidence about similarity between columnvalue-propertyvalue
                    String restrict = entry.getValue().opt.getRestrict();// restriction of propriety
                    String typeOfFilter = entry.getValue().opt.getFilterType();
                    String operator = entry.getValue().opt.getOperator();


                    if (typeOfFilter.toUpperCase().equals("SIMILARITY")) {/// if this filter is enabled then
                        SimilarityScore<Double> jw = new JaroWinklerDistance();
                        boolean control = true;
                        while (sparqlResults.hasNext() && control) {
                            ObjectPV pairPV = new ObjectPV();
                            QuerySolution soln = sparqlResults.nextSolution();
                            if (soln.get("q") != null) {
                                if (soln.get("q").isLiteral()) {
                                    double similarity = jw.apply(columnValue, soln.getLiteral("q").getString());
                                    if(operator.equals('>')){
                                    if (similarity > threshold) {
                                        pairPV.setcolumnValue(columnValue);
                                        pairPV.setpropertyValue(soln.getLiteral("q").getString());
                                        pairPV.setlabelOfProperty(entry.getKey());
                                        pairPV.setLocalScore(similarity);
                                        pairPV.setRestrict(restrict);
                                        pairPV.setfilterType(typeOfFilter);
                                        res.addPairPV(pairPV);
                                        control = false;
                                    }
                                    }else if(operator.equals('=')){
                                        if (similarity == threshold) {
                                            pairPV.setcolumnValue(columnValue);
                                            pairPV.setpropertyValue(soln.getLiteral("q").getString());
                                            pairPV.setlabelOfProperty(entry.getKey());
                                            System.out.println(similarity);
                                            pairPV.setLocalScore(similarity);
                                            pairPV.setRestrict(restrict);
                                            pairPV.setfilterType(typeOfFilter);
                                            res.addPairPV(pairPV);
                                            control = false;
                                        }
                                    }else if( operator.equals('<')){
                                        if (similarity < threshold){
                                            pairPV.setcolumnValue(columnValue);
                                            pairPV.setpropertyValue(soln.getLiteral("q").getString());
                                            pairPV.setlabelOfProperty(entry.getKey());
                                            System.out.println(similarity);
                                            pairPV.setLocalScore(similarity);
                                            pairPV.setRestrict(restrict);
                                            pairPV.setfilterType(typeOfFilter);
                                            res.addPairPV(pairPV);
                                            control = false;
                                        }
                                    }
                                } else {
                                    double similarity = jw.apply(urifyGeoNamesId(columnValue), soln.getResource("q").toString());
                                    if (similarity == 1) {/// in this case the user has to insert the condition of exactMatch(threshold == 1)

                                        pairPV.setcolumnValue(urifyGeoNamesId(columnValue));

                                        pairPV.setpropertyValue(soln.getResource("q").toString());

                                        pairPV.setlabelOfProperty(entry.getKey());

                                        pairPV.setLocalScore(similarity);
                                        pairPV.setRestrict(restrict);
                                        pairPV.setfilterType(typeOfFilter);
                                        res.addPairPV(pairPV);
                                        control = false;
                                    }
                                }
                            } else {// if propertyvalue don't exist in knowledge-base
                                if (restrict.toUpperCase().equals("HARD")) { // if restriction condition is "hard" then
                                    pairPV.setcolumnValue(columnValue); // set value column
                                    pairPV.setLocalScore(0); // local score
                                    pairPV.setRestrict(restrict);
                                    res.addPairPV(pairPV);// add pair at list
                                }// otherwise is"soft" and i can not considerate the pair and i don't add it
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            double totalscore = 0.0;
            for (ObjectPV pairPV : res.getPairPV()) {
                totalscore += pairPV.getLocalScore();
            }
            if (res.getPairPV().size() > 0) {
                totalscore = totalscore / res.getPairPV().size();
                res.setScore((totalscore * 100) + res.getScore());
            }
            lr.add(res);
        }
        return lr;
    }

    @Override
    public List<Result> search(SearchQuery query) {

        List<Result> results;
        Coordinate coordinate = this.getCoordinateFromString(query.getQuery());

        if (coordinate != null) {
            results = this.matchingByCoordinate(query, coordinate);
        } else if (isGeonamesId(query.getQuery())) {
            results = this.matchingByIdentifier(query);
        } else {
            results = this.matchingByLookup(query);
        }

        // Filter results by properties (if any given)
        if (!query.getProperties().isEmpty()) {
            results = this.matchingByProperties(query, results);
        }

        // Match if the first result score is equal to 1.0
        if (results.size() > 0 && results.get(0).getScore() == 1.0) {
            results.get(0).setMatch(true);
        }

        return results;
    }

    @Override
    public CellList extend(String id, List<PropertyValueIdAndSettings> idsAndSettings) {
        CellList<String> cl = new CellList<>();

        for (PropertyValueIdAndSettings pv : idsAndSettings) {
            String queryString = String.format(
                    "PREFIX gn: <%s>\n" +
                            "select ?o ?name where {\n" +
                            "  <%s> <%s> ?o .\n" +
                            "  OPTIONAL {?o gn:name ?name .}\n" +
                            "}",
                    GN_ONTOLOGY_PREFIX, urifyGeoNamesId(id), urifyPropertyId(pv.getId()));

            Query sparqlQuery = QueryFactory.create(queryString);

            ArrayList<Cell> cells = new ArrayList<>();

            try (QueryExecution qexec = QueryExecutionFactory.sparqlService(sparqlEndpoint, sparqlQuery, graphName, null, null)) {
                ResultSet sparqlResults = qexec.execSelect();
                while (sparqlResults.hasNext()) {
                    QuerySolution soln = sparqlResults.nextSolution();
                    if (soln.getLiteral("name") != null) {
                        cells.add(new Cell(soln.getResource("o").getURI()
                                .replace(GN_RESOURCE_PREFIX, "")
                                .replace("/", ""),
                                soln.getLiteral("name").getString()));
                    } else {
                        cells.add(new Cell(soln.getLiteral("o").getString()));
                    }
                }

            } catch (Exception e) {
                return null;
            }

            cl.put(pv.getId(), cells);
        }

        return cl;
    }

    @Override
    public ColumnMetaData columnMetaData(PropertyValueIdAndSettings prop) {

        String queryString;
        String onGraph;

        if (this.ontologyGraphName != null) {
            queryString = String.format("PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                            "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
                            "select ?type where {\n" +
                            "  <%s> rdfs:range [owl:hasValue ?type] .\n" +
                            "}",
                    urifyPropertyId(prop.getId()));
            onGraph = this.ontologyGraphName;
        } else {
            // TODO: replace this query with a request to ABSTAT!
            queryString = String.format(
                    "PREFIX gn: <%s>\n" +
                            "select ?type (count(?type) as ?count)\n" +
                            "where {\n" +
                            "  ?s <%s> ?o .\n" +
                            "  OPTIONAL {?o gn:featureCode ?type .}\n" +
                            "}\n" +
                            "group by ?type\n" +
                            "order by desc(?count)",
                    GN_ONTOLOGY_PREFIX, urifyPropertyId(prop.getId()));
            onGraph = this.graphName;
        }

        Query sparqlQuery = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(sparqlEndpoint, sparqlQuery, onGraph, null, null)) {
            ResultSet sparqlResults = qexec.execSelect();

            ColumnMetaData col = new ColumnMetaData();
            col.setId(prop.getId());
            col.setName(prop.getId());

            if (sparqlResults.hasNext()) {
                Resource mostFreqType = sparqlResults.nextSolution().getResource("type");
                if (mostFreqType != null) {
                    String type = mostFreqType.toString().replace(GN_ONTOLOGY_PREFIX, "");
                    col.setType(new NameType(type, type));
                }
            }
            return col;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public ProposePropertiesResponse proposeProperties(String type, int limit) {

        // TODO: replace this query with a request to ABSTAT!
        String queryString = "select ?p\n" +
                "where {\n" +
                "  ?s ?p ?o.\n" +
                "}\n" +
                "group by ?p\n" +
                "order by desc(count (?p))";

        if (type != null && !type.isEmpty()) {
            // type can be a featureCode (e.g., A.ADM1) or a featureClass (e.g., A), or null
            String typeProperty = type.contains(".") ? "featureCode" : "featureClass";
            queryString = String.format(
                    "PREFIX gn: <%s>\n" +
                            "select ?p\n" +
                            "where {\n" +
                            "?s ?p ?o;\n" +
                            "gn:%s gn:%s .\n" +
                            "}\n" +
                            "group by ?p\n" +
                            "order by desc(count (?p))",
                    GN_ONTOLOGY_PREFIX, typeProperty, type);
        }

        Query sparqlQuery = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(sparqlEndpoint, sparqlQuery, graphName, null, null)) {
            ResultSet sparqlResults = qexec.execSelect();

            List<NameType> properties = new ArrayList<>();
            while (sparqlResults.hasNext()) {
                QuerySolution soln = sparqlResults.nextSolution();
                String property = soln.getResource("p").toString().replace(GN_ONTOLOGY_PREFIX, "");
                properties.add(new NameType(property, property));
                if (limit > 0 && properties.size() == limit) {
                    break;
                }
            }

            ProposePropertiesResponse res = new ProposePropertiesResponse();
            res.setProperties(properties);
            res.setLimit(limit);
            res.setType(type);
            return res;

        } catch (Exception e) {
            return null;
        }
    }
}