
package com.codefork.refine.resources;

public class pvObject{

private String columnValue;
    private String propertyValue;
    private String labelOfProperty;
    private double localScore;
    private String filterType;

    public pvObject() {
        this.localScore = 0.0;
    }

    public pvObject(String columnValue,String propertyValue, String labelOfProperty, double localScore, String filterType) {
        this.columnValue = columnValue;
        this.propertyValue = propertyValue;
        this.labelOfProperty = propertyValue;
        this.localScore = localScore;
        this.filterType = filterType;
    }
    public pvObject(String columnValue,String propertyValue, String labelOfProperty, double localScore) {
        this.columnValue = columnValue;
        this.propertyValue = propertyValue;
        this.labelOfProperty = propertyValue;
        this.localScore = localScore;
    }
    
    public void setcolumnValue(String columnValue){
        this.columnValue = columnValue;
    }
    public String getcolumnValue(){
        return this.columnValue;
    }
    public void setpropertyValue(String propertyValue){
        this.propertyValue = propertyValue;
    }
    public String getpropertyValue(){
        return this.propertyValue;
    }
    public void setlabelOfPropertye(String labelOfProperty){
        this.labelOfProperty = labelOfProperty;
    }
    public String getlabelOfProperty(){
        return this.labelOfProperty;
    }
    public void setLocalScore(double localscore){
        this.localScore = localscore;
    }
    public double getLocalScore(){
        return this.localScore;
    }
    public void setfilterType(String filterType){
        this.filterType = filterType;
    }
    public String getfilterType(){
        return this.filterType;
    }

}