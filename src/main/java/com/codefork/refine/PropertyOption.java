package com.codefork.refine;

public class PropertyOption {
    private String filterType;
    private String operator;
    private double threshold;
    private String restrict;

    PropertyOption() {
        this("exactMatch","=",1, "hard");
    }

    public PropertyOption(String filterType, String operator, double threshold, String restrict){
        this.filterType = filterType;
        this.operator = operator;
        this.threshold = threshold;
        this.restrict = restrict;
    }

    public void setFilterType(String filterType) {
        this.filterType = filterType;
    }
    public String getFilterType() {
        return filterType;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }
    public double getThreshold() {
        return threshold;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
    public String getOperator() {
        return operator;
    }

    public void setRestrict(String restrict) {
        this.restrict = restrict;
    }

    public String getRestrict() {
        return restrict;
    }
}
