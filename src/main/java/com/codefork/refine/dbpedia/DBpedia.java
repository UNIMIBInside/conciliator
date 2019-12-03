package com.codefork.refine.dbpedia;

import com.codefork.refine.ApplicationConfig;
import com.codefork.refine.SearchQuery;
import com.codefork.refine.ThreadPoolFactory;
import com.codefork.refine.datasource.ConnectionFactory;
import com.codefork.refine.datasource.WebServiceDataSource;
import com.codefork.refine.resources.Result;
import com.codefork.refine.resources.ServiceMetaDataResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("dbpedia")
public class DBpedia extends WebServiceDataSource {

    @Autowired
    public DBpedia(ApplicationConfig config, DBpediaConfig dbpediaConfig, CacheManager cacheManager, ThreadPoolFactory threadPoolFactory, ConnectionFactory connectionFactory) {
        super(config, cacheManager, threadPoolFactory, connectionFactory);
    }

    @Override
    public ServiceMetaDataResponse createServiceMetaDataResponse(String baseUrl) {
        return new DBpediaMetaDataResponse(getName());
    }

    @Override
    public String getName() {
        return "DBpedia";
    }

    @Override
    public List<Result> search(SearchQuery query) throws Exception {
        return null;
    }
}
