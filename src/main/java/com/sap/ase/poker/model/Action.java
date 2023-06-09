package com.sap.ase.poker.model;

public enum Action {
    FOLD("fold"),
    RAISE("raise"),
    CALL("call"),
    CHECK("check");

    private final String value;
    Action(String value){
        this.value = value;
    }

    public String getValue(){
        return this.value;
    }
}
