package com.codefork.refine.geonames;

import com.codefork.refine.resources.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeonamesMetaDataResponse extends ServiceMetaDataResponse {

    private final static String IDENTIFIER_SPACE = "http://sws.geonames.org/";
    private final static View VIEW = new View("http://sws.geonames.org/{{id}}");
    private final static String SCHEMA_SPACE = "http://www.geonames.org/ontology#";
    private final static List<NameType> DEFAULT_TYPES = new ArrayList<>();

    static {
        DEFAULT_TYPES.add(new NameType("Feature", "Feature"));
    }

    public GeonamesMetaDataResponse(String baseServiceName, ProposeProperties proposeProperties) {
        setName(baseServiceName);
        setIdentifierSpace(IDENTIFIER_SPACE);
        setSchemaSpace(SCHEMA_SPACE);
        setView(VIEW);
        setDefaultTypes(DEFAULT_TYPES);
        setExtend(new Extend(proposeProperties));
    }

}
