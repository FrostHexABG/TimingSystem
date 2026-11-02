package me.makkuusen.timing.system.team;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.tuning.Attribute;
import me.makkuusen.timing.system.tuning.Part;
import me.makkuusen.timing.system.tuning.PartCategory;
import me.makkuusen.timing.system.tuning.PartManager;
import org.w3c.dom.Attr;

import java.util.*;

@Getter
@Setter
public class TeamTuning {
    private int id;
    private int teamID;
    private Map<Attribute, Integer> attributes = new LinkedHashMap<>();
    private Map<PartCategory, Part> equippedParts = new EnumMap<>(PartCategory.class);

    public int MAX_TOTAL_POINTS = 30;
    public static final int MIN_STAT_VALUE = 0;
    public static final int MAX_STAT_VALUE = 30000;
    public static final int BASE_STAT_VALUE = 5;
    
    // Define all available attributes here
    // when adding new ones change here and parts.java
    public static final Map<Attribute, TuningAttribute> AVAILABLE_ATTRIBUTES = new LinkedHashMap<>();
    static {
        // name, packetId, vanillaDefault, category, multiplier
        // Multiplier > 1 amplifies the effect per point, < 1 dampens it

        // --- Acceleration ---
        AVAILABLE_ATTRIBUTES.put(Attribute.FORWARD_ACCEL,
            new TuningAttribute("forwardAcceleration", (short)11, 0.04f, "acceleration", 0.6f));

        AVAILABLE_ATTRIBUTES.put(Attribute.TURNING_FORWARD_ACCEL,
            new TuningAttribute("turningForwardAcceleration", (short)13, 0.005f, "acceleration", 10.0f));

        AVAILABLE_ATTRIBUTES.put(Attribute.BACKWARD_ACCEL,
            new TuningAttribute("backwardAcceleration", (short)12, 0.005f, "acceleration", 9.0f));

        // --- Speed ---
        AVAILABLE_ATTRIBUTES.put(Attribute.DEFAULT_SLIPPERINESS,
            new TuningAttribute("defaultSlipperiness", (short)2, 0.6f, "speed", 3f));

        AVAILABLE_ATTRIBUTES.put(Attribute.PACKED_ICE_SLIPPERINESS,
            new TuningAttribute("packedIceSlipperiness", (short)3, 0.98f, "speed", 0.1f));

        AVAILABLE_ATTRIBUTES.put(Attribute.BLUE_ICE_SLIPPERINESS,
            new TuningAttribute("blueIceSlipperiness", (short)3, 0.989f, "speed", 0.1f));

        // --- Handling ---
        AVAILABLE_ATTRIBUTES.put(Attribute.YAW_ACCEL,
            new TuningAttribute("yawAcceleration", (short)10, 1.0f, "handling", 9.0f));

        // --- new stuff i'll move later ---
        AVAILABLE_ATTRIBUTES.put(Attribute.SCALE,
                new TuningAttribute("scale", (short)36, 1.0f, "handling", 1.0f));
        AVAILABLE_ATTRIBUTES.put(Attribute.MAX_SPEED,
                new TuningAttribute("maxSpeed", (short)45, 3.0f, "speed", 1.0f));

        AVAILABLE_ATTRIBUTES.put(Attribute.MAX_SPEED_RESISTANCE,
                new TuningAttribute("maxSpeedResistance", (short)46, 1.0f, "speed", 1.0f));

        AVAILABLE_ATTRIBUTES.put(Attribute.BRAKE_SLIPPERINESS,
                new TuningAttribute("brakeSlipperiness", (short)41, 1.0f, "speed", -0.1f));

        AVAILABLE_ATTRIBUTES.put(Attribute.WALLTAP_MULTIPLIER,
                new TuningAttribute("WALLTAP_MULTIPLIER", (short)34, 0.001f, "handling", 1000.0f));

        AVAILABLE_ATTRIBUTES.put(Attribute.LATERAL_SLIPPERINESS,
                new TuningAttribute("LATERAL_SLIPPERINESS", (short)40, 1.0f, "handling", -1.0f));
    }


    public void setMAX_TOTAL_POINTS(int MAX_TOTAL_POINTS) {
        this.MAX_TOTAL_POINTS = MAX_TOTAL_POINTS;
    }

    public TeamTuning(int teamID){
        this.teamID = teamID;
        // Initialize all attributes at base value
        for (Attribute attrName : AVAILABLE_ATTRIBUTES.keySet()) {
            attributes.put(attrName, BASE_STAT_VALUE);
        }
    }

    public void equipPart(Part part){
        equippedParts.put(part.getCategoryName(), part);
        rebuildStats();
    }

    public void unequipPart(PartCategory category){
        equippedParts.remove(category);
        rebuildStats();
    }

    public void rebuildStats() {
        resetAttributes();

        for (Part part : equippedParts.values()) {
            for (Attribute attr : part.getAttributes().keySet()) {

                int value = part.getValue(attr);

                // there is probbably a better way of doing this but i'm tired
                int toReplace;
                try{
                    toReplace = attributes.get(attr);
                } catch (Exception e) {
                    toReplace = BASE_STAT_VALUE;
                }

                attributes.put(
                        attr,
                        toReplace + value
                );
            }
        }
    }

    public void resetAttributes(){
        Set<Attribute> keySet = attributes.keySet();
        attributes.clear();
        for (Attribute attr : keySet){
            attributes.put(attr, BASE_STAT_VALUE);
        }
    }

    public int getTotalRating(){
        int total = 0;

        for (Part part : equippedParts.values()){
            total += part.getRating();
        }

        return total;
    }

    public void increaseAttribute(Attribute name){
        try{
            int current = attributes.get(name);
            if (current < MAX_STAT_VALUE && getTotalPoints() < MAX_TOTAL_POINTS) {
                attributes.put(name, current + 1);
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public void decreaseAttribute(Attribute name){
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
        TeamTuningData data = new TeamTuningData();

        for (Map.Entry<PartCategory, Part> entry : equippedParts.entrySet()) {
            data.equippedParts.put(
                    entry.getKey(),
                    entry.getValue().getId()
            );
        }

        return new Gson().toJson(data);
    }

    public static TeamTuning fromJson(int teamID, String json) {
        TeamTuning tuning = new TeamTuning(teamID);

        if (json == null || json.isEmpty()) {
            return tuning;
        }

        Gson gson = new Gson();

        // Detect legacy format
        if (json.contains("forwardAcceleration")) {

            // old system fallback
            return tuning;
        }

        TeamTuningData data =
                gson.fromJson(json, TeamTuningData.class);

        for (Map.Entry<PartCategory, String> entry :
                data.equippedParts.entrySet()) {


            Part part = PartManager.getPart(entry.getValue());

            if (part != null) {
                tuning.equipPart(part);
            }
        }

        tuning.rebuildStats();

        return tuning;
    }

    public void save() {
        TimingSystem.getTeamDatabase().saveTeamTuning(teamID, toJson());
    }
}
