package me.makkuusen.timing.system.event;

import lombok.Getter;
import me.makkuusen.timing.system.heat.Heat;
import me.makkuusen.timing.system.participant.Driver;
import me.makkuusen.timing.system.round.QualificationRound;
import me.makkuusen.timing.system.team.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
public class EventResults {


    public static List<Driver> generateHeatResults(Heat heat) {
        List<Driver> newList = new ArrayList<>(heat.getDrivers().values());
        newList.sort(Comparator.comparingInt(Driver::getPosition));
        return newList;
    }

    public static List<Driver> generateRoundResults(List<Heat> heats) {
        List<Driver> results = new ArrayList<>();
        for (Heat heat : heats) {
            List<Driver> newList = new ArrayList<>(heat.getDrivers().values());
            newList.sort(Comparator.comparingInt(Driver::getPosition));
            results.addAll(newList);
        }
        if (!results.isEmpty()) {
            if (results.get(0).getHeat().getRound() instanceof QualificationRound) {
                results.sort(Driver::compareTo);
            }
        }

        return results;
    }

    public static List<Team> generateTeamRoundResults(List<Heat> heats) {
        List<Driver> driverResults = generateRoundResults(heats);
        
        List<Team> teams = new ArrayList<>();
        Set<Integer> addedTeamIds = new HashSet<>();
        
        for (Driver driver : driverResults) {
            var teamEntry = driver.getHeat().getTeamEntryByPlayer(driver.getTPlayer().getUniqueId());
            if (teamEntry.isPresent() && teamEntry.get().getTeam() != null) {
                int teamId = teamEntry.get().getTeam().getId();
                if (!addedTeamIds.contains(teamId)) {
                    teams.add(teamEntry.get().getTeam());
                    addedTeamIds.add(teamId);
                }
            }
        }
        
        return teams;
    }
}
