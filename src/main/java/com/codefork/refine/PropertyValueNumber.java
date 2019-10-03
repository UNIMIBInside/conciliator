package com.codefork.refine;

public class PropertyValueNumber extends PropertyValue {

    private long number;

    public PropertyValueNumber(long number) {
        this.number = number;
        super.opt = new PropertyOption();
    }

    public PropertyValueNumber(long number, PropertyOption opt) {
        this.number = number;
        super.opt = opt;
    }

    public long getNumber() {
        return number;
    }

    public void setNumber(long number) {
        this.number = number;
    }

    @Override
    public PropertyValueType getValueType() {
        return PropertyValueType.NUMBER;
    }

    @Override
    public String asString() {
        return String.valueOf(number);
    }

}
