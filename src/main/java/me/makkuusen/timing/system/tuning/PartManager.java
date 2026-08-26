package me.makkuusen.timing.system.tuning;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import me.makkuusen.timing.system.TimingSystem;

import javax.swing.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

public class PartManager {
    private static Map<String, Part> parts = new HashMap<>();

    public PartManager(){

    }

    public static boolean addPart(Part part){
        if (partExists(part.getName())) return false;

        parts.put(part.getId(), part);
        return true;
    }

    public static boolean removePart(String name){
        String id = getPartByName(name).getId();
        return parts.remove(id) != null;
    }

    public static List<String> getPartNames() {
        List<String> names = new ArrayList<>();

        for (Part part : parts.values()) {
            names.add(part.getName());
        }

        return names;
    }

    public static Collection<Part> getParts() {
        return parts.values();
    }

    public static Part getPart(String id){
        return parts.get(id);
    }

    public static boolean partExists(String name) {
        return getPartByName(name) != null;
    }

    public static Part getPartByName(String name) {
        for (Part part : parts.values()) {
            if (part.getName().equalsIgnoreCase(name)) {
                return part;
            }
        }
        return null;
    }

    public static void saveParts(){
        try{
            Gson gson = new Gson();
            File file = new File(TimingSystem.getPlugin().getDataFolder(), "parts.json");

            file.getParentFile().mkdirs();

            FileWriter writer = new FileWriter(file);

            gson.toJson(parts.values(), writer);

            writer.flush();
            writer.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void loadParts(){
        try{
            Gson gson = new Gson();
            File file = new File(TimingSystem.getPlugin().getDataFolder(), "parts.json");

            if (!file.exists()){
                saveParts();
                return;
            }

            FileReader reader = new FileReader(file);

            Type type = new TypeToken<List<Part>>(){}.getType();

            List<Part> loadedParts = gson.fromJson(reader, type);

            parts.clear();

            for (Part part : loadedParts){
                parts.put(part.getId(), part);
            }

            reader.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
