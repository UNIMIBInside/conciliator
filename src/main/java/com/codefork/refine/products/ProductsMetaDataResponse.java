package com.codefork.refine.products;

import com.codefork.refine.resources.NameType;
import com.codefork.refine.resources.ServiceMetaDataResponse;
import com.codefork.refine.resources.View;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductsMetaDataResponse extends ServiceMetaDataResponse {

    private final static String IDENTIFIER_SPACE = "http://inside.disco.unimib.it/products/";
    private final static View VIEW = null;
    private final static String SCHEMA_SPACE = "http://inside.disco.unimib.it/products/ontology/";
    private final static List<NameType> DEFAULT_TYPES = new ArrayList<>();

    static {
        DEFAULT_TYPES.add(new NameType("https://schema.org/Product", "Product"));
    }

    public ProductsMetaDataResponse(String baseServiceName) {
        setName(baseServiceName);
        setIdentifierSpace(IDENTIFIER_SPACE);
        setSchemaSpace(SCHEMA_SPACE);
        setView(VIEW);
        setDefaultTypes(DEFAULT_TYPES);
    }
}
