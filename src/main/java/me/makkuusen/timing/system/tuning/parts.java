package me.makkuusen.timing.system.tuning;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class parts {
    @Getter
    private String name;
    @Getter
    public String CatagoryName;
    private Map<String, Integer> attributes = new HashMap<>();

    public parts(String TheName){
        this.name = TheName;
    }

    public void addAttribute(String name, Integer value){
        attributes.put(name, value);
    }

    public void removeAttribute(String name){
        attributes.remove(name);
    }

    public Map getAttributes(){
        return attributes;
    }
}
