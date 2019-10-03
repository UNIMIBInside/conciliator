package com.codefork.refine;

public class PropertyValueId extends PropertyValue {

    private String id;

    public PropertyValueId(String id) {
        this.id = id;
        super.opt = new PropertyOption();
    }
    public PropertyValueId(String id, PropertyOption opt) {
        this.id = id;
        super.opt = opt;
    }
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public PropertyValueType getValueType() {
        return PropertyValueType.ID;
    }

    @Override
    public String asString() {
        return id;
    }

}
