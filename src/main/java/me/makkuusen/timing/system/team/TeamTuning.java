package me.makkuusen.timing.system.team;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.Setter;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.tuning.parts;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class TeamTuning {
    private int id;
    private int teamID;
    private Map<String, Integer> attributes = new LinkedHashMap<>();
    private List<parts> partsList = new ArrayList<>();

    public int MAX_TOTAL_POINTS = 30;
    public static final int MIN_STAT_VALUE = 0;
    public static final int MAX_STAT_VALUE = 30000;
    public static final int BASE_STAT_VALUE = 5;
    
    // Define all available attributes here
    // when adding new ones change here and parts.java
    public static final Map<String, TuningAttribute> AVAILABLE_ATTRIBUTES = new LinkedHashMap<>();
    static {
        // name, packetId, vanillaDefault, category, multiplier
        // Multiplier > 1 amplifies the effect per point, < 1 dampens it

        // --- Acceleration ---
        AVAILABLE_ATTRIBUTES.put("forwardAcceleration",
            new TuningAttribute("forwardAcceleration", (short)11, 0.04f, "acceleration", 0.6f));

        AVAILABLE_ATTRIBUTES.put("turningForwardAcceleration",
            new TuningAttribute("turningForwardAcceleration", (short)13, 0.005f, "acceleration", 10.0f));

        AVAILABLE_ATTRIBUTES.put("backwardAcceleration",
            new TuningAttribute("backwardAcceleration", (short)12, 0.005f, "acceleration", 9.0f));

        // --- Speed ---
        AVAILABLE_ATTRIBUTES.put("defaultSlipperiness",
            new TuningAttribute("defaultSlipperiness", (short)2, 0.6f, "speed", 1.0f));

        AVAILABLE_ATTRIBUTES.put("packedIceSlipperiness",
            new TuningAttribute("packedIceSlipperiness", (short)3, 0.98f, "speed", -0.1f));

        AVAILABLE_ATTRIBUTES.put("blueIceSlipperiness",
            new TuningAttribute("blueIceSlipperiness", (short)3, 0.989f, "speed", -0.1f));

        // --- Handling ---
        AVAILABLE_ATTRIBUTES.put("yawAcceleration",
            new TuningAttribute("yawAcceleration", (short)10, 1.0f, "handling", 9.0f));
    }

    // applies parts to the overall tuning attribute
    public void applyParts(){
        for (parts part : partsList){
            Map<String, Integer> attributes = part.getAttributes();

            for (String attr : attributes.keySet()){
                Integer times = attributes.get(attr);

                for (int x = 0; x < times; x++){
                    increaseAttribute(attr);
                }
            }
        }
    }

    public void setMAX_TOTAL_POINTS(int MAX_TOTAL_POINTS) {
        this.MAX_TOTAL_POINTS = MAX_TOTAL_POINTS;
    }

    public TeamTuning(int teamID){
        this.teamID = teamID;
        // Initialize all attributes at base value
        for (String attrName : AVAILABLE_ATTRIBUTES.keySet()) {
            attributes.put(attrName, BASE_STAT_VALUE);
        }
    }

    public boolean addAttribute(String name){
        try{
            attributes.put(name, BASE_STAT_VALUE);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void increaseAttribute(String name){
        try{
            int current = attributes.get(name);
            if (current < MAX_STAT_VALUE && getTotalPoints() < MAX_TOTAL_POINTS) {
                attributes.put(name, current + 1);
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public void decreaseAttribute(String name){
        try{
            int current = attributes.get(name);
            if (current > MIN_STAT_VALUE) {
                attributes.put(name, current - 1);
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public int getTotalPoints(){
        int total = 0;
        for (int value : attributes.values()){
            total += value;
        }
        return total;
    }
    
    public boolean isValid() {
        return getTotalPoints() <= MAX_TOTAL_POINTS;
    }

    public String toJson() {
        return new Gson().toJson(attributes);
    }

    public static TeamTuning fromJson(int teamID, String json) {
        TeamTuning tuning = new TeamTuning(teamID);
        if (json == null || json.isEmpty()) return tuning;

        Type type = new TypeToken<LinkedHashMap<String, Integer>>(){}.getType();
        Map<String, Integer> loaded = new Gson().fromJson(json, type);

        // Merge: keep saved values for known attributes, use base value for any new ones
        for (String attr : AVAILABLE_ATTRIBUTES.keySet()) {
            tuning.attributes.put(attr, loaded.getOrDefault(attr, BASE_STAT_VALUE));
        }
        return tuning;
    }

    public void save() {
        TimingSystem.getTeamDatabase().saveTeamTuning(teamID, toJson());
    }
}
