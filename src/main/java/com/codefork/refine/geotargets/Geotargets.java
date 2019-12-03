package com.codefork.refine.geotargets;

import com.codefork.refine.ApplicationConfig;
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
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component("geotargets")
public class Geotargets extends WebServiceDataSource {

    private final Model model = ModelFactory.createDefaultModel();
    private final double threshold = 0.9;

    @Autowired
    public Geotargets(ApplicationConfig config, CacheManager cacheManager, ThreadPoolFactory threadPoolFactory, ConnectionFactory connectionFactory) {
        super(config, cacheManager, threadPoolFactory, connectionFactory);
        RDFDataMgr.read(this.model, "AdWords_API_Location_Criteria_DE_ES.ttl"); // TODO should be a SPARQL endpoint
    }

    @Override
    public ServiceMetaDataResponse createServiceMetaDataResponse(String baseUrl) {
        return new GeotargetsMetaDataResponse(getName());
    }

    @Override
    public String getName() {
        return "Google GeoTargets";
    }

    private Result getResultFromIdentifier(String identifier) {
        GeotargetsMetaDataResponse meta = new GeotargetsMetaDataResponse(this.getName());
        String idSpace = meta.getIdentifierSpace();
        String schemaSpace = meta.getSchemaSpace();
        String queryString = String.format(
                "SELECT * WHERE { <%s%s> ?p ?o . }",
                idSpace,
                identifier);

        Query sparqlQuery = QueryFactory.create(queryString) ;
        String title = null;
        List<NameType> nameTypes = new ArrayList<>();

        try (QueryExecution qexec = QueryExecutionFactory.create(sparqlQuery, this.model)) {
            ResultSet sparqlResults = qexec.execSelect() ;

            for ( ; sparqlResults.hasNext() ; )
            {
                QuerySolution soln = sparqlResults.nextSolution() ;
                if (soln.get("p") != null) {
                    switch (soln.get("p").toString()) {
                        case "http://xmlns.com/foaf/0.1/name":
                            title = soln.getLiteral("o").getString();
                            break;
                        case "http://www.w3.org/1999/02/22-rdf-syntax-ns#type":
                            String type = soln.get("o").toString().replaceAll(schemaSpace, "");
                            nameTypes.add(new NameType(type, type));
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        if (title != null && !nameTypes.isEmpty()) {
            return new Result(identifier, title, nameTypes, 1, true);
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

        String queryString = "SELECT * WHERE { ?s <http://xmlns.com/foaf/0.1/name> ?o . }"; // get all ProductsServices names

        Query sparqlQuery = QueryFactory.create(queryString) ;

        try (QueryExecution qexec = QueryExecutionFactory.create(sparqlQuery, this.model)) {
            ResultSet sparqlResults = qexec.execSelect() ;
            SimilarityScore<Double> jw = new JaroWinklerDistance();

            for ( ; sparqlResults.hasNext() ; )
            {
                QuerySolution soln = sparqlResults.nextSolution() ;
                if (soln.get("o") != null) {
                    double similarity = jw.apply(query.getQuery(), soln.getLiteral("o").getString());

                    if (similarity > this.threshold && soln.get("s") != null) {
                        String resource = soln.getResource("s").toString();

                        Result res = this.getResultFromIdentifier(resource.substring(resource.lastIndexOf('/') + 1));
                        if (res != null) {
                            res.setScore(similarity);
                            res.setMatch(false);
                            results.add(res);
                        }
                    }
                }
            }
        }

        return results;
    }

    @Override
    public List<Result> search(SearchQuery query) {

        List<Result> results;

        if (StringUtils.isNumeric(query.getQuery())) { // Google AdWords ProductsServices identifiers are numbers
            results = this.matchingByIdentifier(query);
        } else {
            results = this.matchingByLookup(query);
        }

        // Remove entities of non-allowed types
        if (query.getTypeStrict() != null && query.getTypeStrict().equals("should")) {
            NameType selectedType = new NameType(query.getNameType().getId(), query.getNameType().getId());
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
