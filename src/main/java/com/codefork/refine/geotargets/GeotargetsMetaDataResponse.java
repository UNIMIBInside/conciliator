package com.codefork.refine.geotargets;

import com.codefork.refine.resources.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeotargetsMetaDataResponse extends ServiceMetaDataResponse {

    private final static String IDENTIFIER_SPACE = "http://inside.disco.unimib.it/ontology/geo/";
    private final static View VIEW = new View("http://inside.disco.unimib.it/ontology/geo/{{id}}");
    private final static String SCHEMA_SPACE = "http://rdf.freebase.com/ns/type.object.id";
    private final static List<NameType> DEFAULT_TYPES = new ArrayList<>();

    static {
        DEFAULT_TYPES.add(new NameType("http://www.w3.org/2002/07/owl#Thing", "Thing"));
    }

    public GeotargetsMetaDataResponse(String baseServiceName) {
        setName(baseServiceName);
        setIdentifierSpace(IDENTIFIER_SPACE);
        setSchemaSpace(SCHEMA_SPACE);
        setView(VIEW);
        setDefaultTypes(DEFAULT_TYPES);
    }

}
