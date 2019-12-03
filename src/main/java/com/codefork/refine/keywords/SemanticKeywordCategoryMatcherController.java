package com.codefork.refine.keywords;


import com.codefork.refine.controllers.DataSourceController;
import com.codefork.refine.datasource.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/reconcile/keywordsmatcher")
public class SemanticKeywordCategoryMatcherController extends DataSourceController {

    @Autowired
    @Qualifier("keywordsMatcher")
    @Override
    public void setDataSource(DataSource dataSource) {
        super.setDataSource(dataSource);
    }
    // the SemanticKeywordCategoryMatcher will be used as data
}
