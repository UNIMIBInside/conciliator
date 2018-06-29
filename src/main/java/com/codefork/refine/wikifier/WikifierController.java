package com.codefork.refine.wikifier;

import com.codefork.refine.controllers.DataSourceController;
import com.codefork.refine.datasource.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/reconcile/wikifier")
public class WikifierController extends DataSourceController {

    @Autowired
    @Qualifier("wikifier")
    @Override
    public void setDataSource(DataSource dataSource) {
        super.setDataSource(dataSource);
    }

}
