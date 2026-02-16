package me.makkuusen.timing.system.team;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class TeamTuning {
    private int id;
    private int teamID;
    private Map<String, Integer> attributes = new HashMap<>();

    @Setter
    public int MAX_TOTAL_POINTS = 15;
    public int MIN_STAT_VALUE = 0;
    public int MAX_STAT_VALUE = 15;
    private int currentAvailablePoints = 0;
    public static final int BASE_STAT_VALUE = 5;

    public TeamTuning(int teamID){
        this.teamID = teamID;
        attributes.put("topSpeed", 5);
        attributes.put("acceleration", 5);
        attributes.put("handling", 5);
    }

    public boolean addAttribute(String name){
        try{
            attributes.put(name, 5);
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
}
