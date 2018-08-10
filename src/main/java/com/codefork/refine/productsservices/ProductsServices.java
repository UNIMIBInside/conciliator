package com.codefork.refine.productsservices;

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

@Component("productsservices")
public class ProductsServices extends WebServiceDataSource {

    private final Model model = ModelFactory.createDefaultModel();
    private final double threshold = 0.9;
    private final double alpha = 0.5;

    @Autowired
    public ProductsServices(Config config, CacheManager cacheManager, ThreadPoolFactory threadPoolFactory, ConnectionFactory connectionFactory) {
        super(config, cacheManager, threadPoolFactory, connectionFactory);
        RDFDataMgr.read(this.model, "productsservices.ttl"); // TODO should be a SPARQL endpoint
    }

    @Override
    public ServiceMetaDataResponse createServiceMetaDataResponse(String baseUrl) {
        return new ProductsServicesMetaDataResponse(getName());
    }

    @Override
    public String getName() {
        return "Google Categories";
    }

    private Result getResultFromIdentifier(String identifier) {
        String queryString = String.format("SELECT * WHERE { <%s> ?p ?o . }", identifier);

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
                        case "http://www.w3.org/2004/02/skos/core#prefLabel":
                            title = soln.getLiteral("o").getString();
                            break;
                        case "http://www.w3.org/1999/02/22-rdf-syntax-ns#type":
                            nameTypes.add(new NameType(soln.get("o").toString(), soln.get("o").toString()));
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        if (title != null && !nameTypes.isEmpty()) {
            identifier = identifier.replace("http://www.jot-im.com/rdf/adwords/", "");
            return new Result(identifier, title, nameTypes, 1, true);
        }

        return null;
    }

    @Override
    public List<Result> search(SearchQuery query) {

        List<Result> results = new ArrayList<>();

        String queryString = "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>" +
                "SELECT * WHERE { ?s skos:prefLabel ?pref ; skos:altLabel ?alt }"; // get all ProductsServices names

        Query sparqlQuery = QueryFactory.create(queryString) ;

        try (QueryExecution qexec = QueryExecutionFactory.create(sparqlQuery, this.model)) {
            ResultSet sparqlResults = qexec.execSelect() ;

            for ( ; sparqlResults.hasNext() ; )
            {
                QuerySolution soln = sparqlResults.nextSolution() ;
                SimilarityScore<Double> jw = new JaroWinklerDistance();
                double prefSim = -1.;
                double altSim = -1.;
                double similarity = -1.;

                if (soln.get("pref") != null) {
                    prefSim = jw.apply(query.getQuery(), soln.getLiteral("pref").getString());
                }

                if (soln.get("alt") != null) {
                    altSim = jw.apply(query.getQuery(), soln.getLiteral("alt").getString());
                }

                if (prefSim == 1.0 || altSim == 1.0) {
                    similarity = 1.0;
                } else {
                    similarity = alpha * prefSim + (1 - alpha) * altSim;
                }

                if (similarity > this.threshold && soln.get("s") != null) {
                    String resource = soln.getResource("s").toString();

                    Result res = this.getResultFromIdentifier(resource);
                    if (res != null) {
                        res.setScore(similarity);
                        res.setMatch(false);
                        results.add(res);
                    }
                }
            }
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
