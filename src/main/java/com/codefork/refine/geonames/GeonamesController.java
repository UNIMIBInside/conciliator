package com.codefork.refine.geonames;

import com.codefork.refine.controllers.DataSourceController;
import com.codefork.refine.datasource.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/reconcile/geonames")
public class GeonamesController extends DataSourceController {

    @Autowired
    @Qualifier("geonames")
    @Override
    public void setDataSource(DataSource dataSource) {
        super.setDataSource(dataSource);
    }

}
