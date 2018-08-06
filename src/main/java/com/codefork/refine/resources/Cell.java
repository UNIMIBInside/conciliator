package com.codefork.refine.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Cell {

    private String str;
    private LocalDateTime date;
    @JsonProperty("float")
    private Float floatVal;
    @JsonProperty("int")
    private Integer intVal;
    private Boolean bool;
    private String id;
    private String name;

    public Cell(String str) {
        this.str = str;
    }

    public Cell(LocalDateTime date) {
        this.date = date;
    }

    public Cell(Float floatVal) {
        this.floatVal = floatVal;
    }

    public Cell(Integer intVal) {
        this.intVal = intVal;
    }

    public Cell(Boolean bool) {
        this.bool = bool;
    }

    public Cell(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getStr() {
        return str;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public Float getFloatVal() {
        return floatVal;
    }

    public Integer getIntVal() {
        return intVal;
    }

    public Boolean getBool() {
        return bool;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
