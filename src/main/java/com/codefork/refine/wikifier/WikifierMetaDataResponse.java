package com.codefork.refine.wikifier;

import com.codefork.refine.resources.NameType;
import com.codefork.refine.resources.ServiceMetaDataResponse;
import com.codefork.refine.resources.View;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WikifierMetaDataResponse extends ServiceMetaDataResponse {

    private final static String IDENTIFIER_SPACE = "http://www.dbpedia.org/entity/";
    private final static View VIEW = new View("http://www.dbpedia.org/resource/{{id}}");
    private final static String SCHEMA_SPACE = "http://rdf.freebase.com/ns/type.object.id";
    private final static List<NameType> DEFAULT_TYPES = new ArrayList<>();

    static {
        DEFAULT_TYPES.add(new NameType("http://www.w3.org/2002/07/owl#Thing", "Thing"));
    }

    public WikifierMetaDataResponse(String baseServiceName) {
        setName(baseServiceName);
        setIdentifierSpace(IDENTIFIER_SPACE);
        setSchemaSpace(SCHEMA_SPACE);
        setView(VIEW);
        setDefaultTypes(DEFAULT_TYPES);
    }

}
