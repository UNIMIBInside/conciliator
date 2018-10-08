package com.codefork.refine.solr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Solr configuration
 */
@Configuration
@ConfigurationProperties(prefix = "datasource.solr")
public class SolrConfig {

    public static class NameType {
        private String id;
        private String name;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Url {
        private String query;
        private String document;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public String getDocument() {
            return document;
        }

        public void setDocument(String document) {
            this.document = document;
        }
    }

    public static class Field {

        public static class MultiValue {
            private String strategy;
            private String delimiter;

            public String getStrategy() {
                return strategy;
            }

            public void setStrategy(String strategy) {
                this.strategy = strategy;
            }

            public String getDelimiter() {
                return delimiter;
            }

            public void setDelimiter(String delimiter) {
                this.delimiter = delimiter;
            }
        }

        private String id;
        private String name;
        private MultiValue multiValue;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public MultiValue getMultiValue() {
            return multiValue;
        }

        public void setMultiValue(MultiValue multiValue) {
            this.multiValue = multiValue;
        }
    }

    private String name;
    private NameType nameType;
    private Url url;
    private Field field;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public NameType getNameType() {
        return nameType;
    }

    public void setNameType(NameType nameType) {
        this.nameType = nameType;
    }

    public Url getUrl() {
        return url;
    }

    public void setUrl(Url url) {
        this.url = url;
    }

    public Field getField() {
        return field;
    }

    public void setField(Field field) {
        this.field = field;
    }
}
