package me.makkuusen.timing.system.team;

import co.aikar.idb.DbRow;
import lombok.Getter;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.database.TSDatabase;
import me.makkuusen.timing.system.tplayer.TPlayer;

import java.util.*;

@Getter
public class Team implements Comparable<Team> {
    private final int id;
    private String name;
    private final List<TPlayer> players;
    private final long dateCreated;
    private final UUID creator;
    private boolean playersLoaded = false;
    private TeamTuning tuning;

    /**
     * Constructor for creating a Team from database data
     */
    public Team(DbRow data) {
        this.id = data.getInt("id");
        this.name = data.getString("name");
        this.dateCreated = data.getInt("dateCreated");
        this.creator = UUID.fromString(data.getString("creator"));
        this.players = new ArrayList<>();
    }

    /**
     * Constructor for creating a new Team
     */
    public Team(int id, String name, UUID creator, long dateCreated) {
        this.id = id;
        this.name = name;
        this.creator = creator;
        this.dateCreated = dateCreated;
        this.players = new ArrayList<>();
    }

    /**
     * Add a player to the team
     * @param player The player to add
     * @return true if player was added, false if already in team
     */
    public boolean addPlayer(TPlayer player) {
        if (hasPlayer(player)) {
            return false;
        }
        return players.add(player);
    }

    /**
     * Remove a player from the team
     * @param player The player to remove
     * @return true if player was removed, false if not in team
     */
    public boolean removePlayer(TPlayer player) {
        return players.remove(player);
    }

    /**
     * Check if a player is in the team
     * @param player The player to check
     * @return true if player is in team
     */
    public boolean hasPlayer(TPlayer player) {
        return players.contains(player);
    }

    /**
     * Mark that players have been loaded from database
     */
    public void setPlayersLoaded(boolean loaded) {
        this.playersLoaded = loaded;
    }

    /**
     * Check if players have been loaded from database
     * @return true if players are loaded
     */
    public boolean arePlayersLoaded() {
        return playersLoaded;
    }

    /**
     * Clear the players list and mark as not loaded
     */
    public void clearPlayers() {
        players.clear();
        playersLoaded = false;
    }

    /**
     * Get the number of players in the team
     * @return player count
     */
    public int getPlayerCount() {
        return players.size();
    }

    /**
     * Check if the team is empty
     * @return true if no players in team
     */
    public boolean isEmpty() {
        return players.isEmpty();
    }

    /**
     * Get the display name of the team
     * @return team name
     */
    public String getDisplayName() {
        return name;
    }

    /**
     * Get the creator of the team as a TPlayer
     * @return TPlayer who created the team, or null if not found
     */
    public TPlayer getCreator() {
        return TSDatabase.getPlayer(creator);
    }

    /**
     * Set the team name
     * @param name new team name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Validate team name
     * @param name team name to validate
     * @return true if valid
     */
    public static boolean isValidTeamName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        // Check length (max 32 characters)
        if (name.length() > 32) {
            return false;
        }
        
        // Check for special characters that could interfere with commands
        // Allow letters, numbers, spaces, hyphens, and underscores
        return name.matches("^[a-zA-Z0-9\\s\\-_]+$");
    }

    @Override
    public int compareTo(Team other) {
        return this.name.compareToIgnoreCase(other.name);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Team team = (Team) obj;
        return id == team.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Team{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", playerCount=" + players.size() +
                '}';
    }

    public TeamTuning getTuning() {
        if (tuning == null) {
            tuning = new TeamTuning(id);
        }
        return tuning;
    }
}