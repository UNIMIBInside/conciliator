package com.codefork.refine.geonames;

import com.codefork.refine.resources.ProposeProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Geonames configuration
 */
@Configuration
@ConfigurationProperties(prefix = "datasource.geonames")
public class GeonamesConfig {

    public static class Elastic {
        private String host;
        private int port;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static class Virtuoso {
        private String endpoint;
        private String graphName;
        private String ontologyGraphName;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getGraphName() {
            return graphName;
        }

        public void setGraphName(String graphName) {
            this.graphName = graphName;
        }

        public String getOntologyGraphName() {
            return ontologyGraphName;
        }

        public void setOntologyGraphName(String ontologyGraphName) {
            this.ontologyGraphName = ontologyGraphName;
        }
    }

    private List<Elastic> elastic = new ArrayList<>();
    private Virtuoso virtuoso;
    private ProposeProperties proposeProperties;

    public List<Elastic> getElastic() {
        return elastic;
    }

    public void setElastic(List<Elastic> elastic) {
        this.elastic = elastic;
    }

    public Virtuoso getVirtuoso() {
        return virtuoso;
    }

    public void setVirtuoso(Virtuoso virtuoso) {
        this.virtuoso = virtuoso;
    }

    public ProposeProperties getProposeProperties() {
        return proposeProperties;
    }

    public void setProposeProperties(ProposeProperties proposeProperties) {
        this.proposeProperties = proposeProperties;
    }
}
