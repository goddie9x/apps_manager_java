package com.god.ApplicationManager.Entity;

public class ColumnTypeDescriptionForModel {
    public String key;
    public String type;
    public String constraint;
    public ColumnTypeDescriptionForModel(String key, String type, String constraint){
        this.key = key;
        this.type = type;
        this.constraint = constraint;
    }
    public ColumnTypeDescriptionForModel(String key, String type){
        this.key = key;
        this.type=type;
        this.constraint = "";
    }
    public String toQuery(){
        return key+" "+type+(constraint==""?"":" "+constraint)+",";
    }
}
