package me.makkuusen.timing.system.tuning;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class Part {
    @Getter
    private String name;
    @Getter
    private String id;
    @Getter
    public PartCatagory CatagoryName;
    @Getter
    @Setter
    public Integer rating;
    @Getter
    @Setter
    private String description;
    private Map<Attribute, Integer> attributes = new HashMap<>();

    public Part(String TheName){
        this.name = TheName;
    }

    public void addAttribute(Attribute name, Integer value){
        attributes.put(name, value);
    }

    public void removeAttribute(String name){
        attributes.remove(name);
    }

    public Map getAttributes(){
        return attributes;
    }
}
