package com.codefork.refine.solr;

import com.codefork.refine.ApplicationConfig;
import com.codefork.refine.SearchQuery;
import com.codefork.refine.ThreadPoolFactory;
import com.codefork.refine.datasource.ConnectionFactory;
import com.codefork.refine.datasource.WebServiceDataSource;
import com.codefork.refine.resources.NameType;
import com.codefork.refine.resources.Result;
import com.codefork.refine.resources.ServiceMetaDataResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;

/**
 * Data source for a Solr interface
 */
@Component("solr")
public class Solr extends WebServiceDataSource {

    private SolrConfig solrConfig;
    Log log = LogFactory.getLog(Solr.class);

    private SAXParserFactory spf = SAXParserFactory.newInstance();

    @Autowired
    public Solr(ApplicationConfig config, SolrConfig solrConfig, CacheManager cacheManager, ThreadPoolFactory threadPoolFactory, ConnectionFactory connectionFactory) {
        super(config, cacheManager, threadPoolFactory, connectionFactory);
        this.solrConfig = solrConfig;
    }

    @Override
    public ServiceMetaDataResponse createServiceMetaDataResponse(String baseUrl) {
        return new SolrMetaDataResponse(getName(), this.solrConfig.getUrl().getDocument());
    }

    public String createURL(SearchQuery query) throws Exception {
        String urlTemplate = this.solrConfig.getUrl().getQuery();
        return urlTemplate.replace("{{QUERY}}", UriUtils.encodeQueryParam(query.getQuery(), "UTF-8"))
                .replace("{{ROWS}}", String.valueOf(query.getLimit()));
    }

    @Override
    public List<Result> search(SearchQuery query) throws Exception {
        String url = createURL(query);
        log.debug("Making request to " + url);
        HttpURLConnection conn = getConnectionFactory().createConnection(url);

        InputStream response = conn.getInputStream();
        MultiValueFieldStrategy multiValueFieldStrategy = MultiValueFieldStrategy.CONCAT;
        if(MultiValueFieldStrategy.CONCAT.toString().toLowerCase().equals(this.solrConfig.getField().getMultiValue().getStrategy())) {
            multiValueFieldStrategy = MultiValueFieldStrategy.CONCAT;
        } else if(MultiValueFieldStrategy.FIRST.toString().toLowerCase().equals(this.solrConfig.getField().getMultiValue().getStrategy())) {
            multiValueFieldStrategy = MultiValueFieldStrategy.FIRST;
        }

        SAXParser parser = spf.newSAXParser();
        SolrParser solrParser = new SolrParser(
                this.solrConfig.getField().getId(),
                this.solrConfig.getField().getName(),
                multiValueFieldStrategy,
                this.solrConfig.getField().getMultiValue().getDelimiter(),
                new NameType(this.solrConfig.getNameType().getId(), this.solrConfig.getNameType().getName())
        );

        long start = System.currentTimeMillis();
        parser.parse(response, solrParser);
        long parseTime = System.currentTimeMillis() - start;

        try {
            response.close();
            conn.disconnect();
        } catch(IOException ioe) {
            log.error("Ignoring error from trying to close input stream and connection: " + ioe);
        }

        log.debug(String.format("Query: %s - parsing took %dms, got %d results",
                query.getQuery(), parseTime, solrParser.getResults().size()));

        return solrParser.getResults();
    }
}
