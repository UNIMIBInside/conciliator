package com.codefork.refine.wikidata;

import com.codefork.refine.resources.NameType;
import com.codefork.refine.resources.ServiceMetaDataResponse;
import com.codefork.refine.resources.View;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WikidataMetaDataResponse extends ServiceMetaDataResponse {

    private final static String IDENTIFIER_SPACE = "http://www.wikidata.org/entity/";
    private final static View VIEW = new View("http://www.wikidata.org/entity/{{id}}");
    private final static String SCHEMA_SPACE = "http://www.wikidata.org/prop/direct/";
    private final static List<NameType> DEFAULT_TYPES = new ArrayList<>();

    static {
        DEFAULT_TYPES.add(new NameType("Q35120", "entity"));
    }

    public WikidataMetaDataResponse(String baseServiceName) {
        setName(baseServiceName);
        setIdentifierSpace(IDENTIFIER_SPACE);
        setSchemaSpace(SCHEMA_SPACE);
        setView(VIEW);
        setDefaultTypes(DEFAULT_TYPES);
    }
}
