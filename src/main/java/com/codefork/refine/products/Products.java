package com.codefork.refine.products;

import com.codefork.refine.ApplicationConfig;
import com.codefork.refine.SearchQuery;
import com.codefork.refine.ThreadPoolFactory;
import com.codefork.refine.datasource.ConnectionFactory;
import com.codefork.refine.datasource.WebServiceDataSource;
import com.codefork.refine.resources.NameType;
import com.codefork.refine.resources.Result;
import com.codefork.refine.resources.ServiceMetaDataResponse;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.apache.commons.text.similarity.SimilarityScore;
import org.apache.jena.query.*;
import org.apache.jena.sparql.engine.http.QueryEngineHTTP;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component("products")
public class Products extends WebServiceDataSource {

    private final double threshold = 0.5;
    private String sparqlService;

    @Autowired
    public Products(ApplicationConfig config, ProductsConfig productsConfig, CacheManager cacheManager, ThreadPoolFactory threadPoolFactory, ConnectionFactory connectionFactory) {
        super(config, cacheManager, threadPoolFactory, connectionFactory);
        this.sparqlService = productsConfig.getSparqlEndpoint();
    }

    @Override
    public ServiceMetaDataResponse createServiceMetaDataResponse(String baseUrl) {
        return new ProductsMetaDataResponse(getName());
    }

    @Override
    public String getName() {
        return "GFK Product";
    }

    @Override
    public List<Result> search(SearchQuery query) {

        List<Result> results = new ArrayList<>();

        String queryString = String.format("PREFIX sdo: <http://schema.org/> " +
                "             SELECT * WHERE { ?productID a sdo:Product ; " +
                "                                         sdo:model ?model . " +
                "                              FILTER( regex(lcase(str(?model)), lcase(\"%s\") )) " +
                "                            }", query.getQuery()); // get all Products names

        Query sparqlQuery = QueryFactory.create(queryString) ;

        QueryEngineHTTP qexec = QueryExecutionFactory.createServiceRequest(this.sparqlService, sparqlQuery);
        ResultSet sparqlResults = qexec.execSelect() ;
        for ( ; sparqlResults.hasNext() ; ) {
            QuerySolution soln = sparqlResults.nextSolution() ;
            SimilarityScore<Double> jw = new JaroWinklerDistance();
            double similarity = -1.;

            if (soln.get("model") != null) {
                similarity = jw.apply(query.getQuery(), soln.getLiteral("model").getString());
            }

            if (similarity > this.threshold && soln.get("productID") != null) {
                String resource = soln.getResource("productID").toString();

                Result res = new Result(resource,
                        soln.getLiteral("model").getString(),
                        new NameType("http://schema.org/Product", "Product"),
                        similarity, false);
                results.add(res);
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
