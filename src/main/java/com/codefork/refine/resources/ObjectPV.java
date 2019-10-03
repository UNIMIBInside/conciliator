//object for extend result in OpenRefine model
package com.codefork.refine.resources;

public class ObjectPV {
    private String columnValue;
    private String propertyValue;
    private String labelOfProperty;
    private double localScore;
    private String filterType;

    private enum pair_Strict {soft, hard}

    private pair_Strict restrict;


    public ObjectPV() {
        this.localScore = 0.0;
        this.restrict = pair_Strict.soft;
    }

    public ObjectPV(String columnValue, String propertyValue, String labelOfProperty) {
        this.columnValue = columnValue;
        this.propertyValue = propertyValue;
        this.labelOfProperty = labelOfProperty;
    }

    public void setcolumnValue(String columnValue) {
        this.columnValue = columnValue;
    }

    public String getcolumnValue() {
        return this.columnValue;
    }

    public void setpropertyValue(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String getpropertyValue() {
        return this.propertyValue;
    }

    public void setlabelOfProperty(String labelOfProperty) {
        this.labelOfProperty = labelOfProperty;
    }

    public String getlabelOfProperty() {
        return this.labelOfProperty;
    }

    public void setLocalScore(double localscore) {
        this.localScore = localscore;
    }

    public double getLocalScore() {
        return this.localScore;
    }

    public void setfilterType(String filterType) {
        this.filterType = filterType;
    }

    public String getfilterType() {
        return this.filterType;
    }

    public void setRestrict(String restrict) {
        if (restrict.toUpperCase().equals("SOFT")) {
            this.restrict = pair_Strict.soft;
        } else if (restrict.toUpperCase().equals("HARD")) {
            this.restrict = pair_Strict.hard;
        } else {
            System.err.println("invalid Restrict");
        }

    }

    public pair_Strict getRestrict() {
        return this.restrict;
    }

}