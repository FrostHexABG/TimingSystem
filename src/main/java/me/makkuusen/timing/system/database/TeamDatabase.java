package me.makkuusen.timing.system.database;

import co.aikar.idb.DbRow;
import me.makkuusen.timing.system.team.Team;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Database interface for team operations following existing TSDatabase patterns
 */
public interface TeamDatabase {

    /**
     * Create a new team in the database
     * @param name team name
     * @param creator UUID of the team creator
     * @param dateCreated timestamp when team was created
     * @return the ID of the created team
     * @throws SQLException if database operation fails
     */
    long createTeam(String name, UUID creator, long dateCreated) throws SQLException;

    /**
     * Select all teams from the database
     * @return list of team database rows
     * @throws SQLException if database operation fails
     */
    List<DbRow> selectTeams() throws SQLException;

    /**
     * Select a specific team by ID
     * @param teamId the team ID
     * @return team database row or null if not found
     * @throws SQLException if database operation fails
     */
    DbRow selectTeam(int teamId) throws SQLException;

    /**
     * Delete a team from the database (soft delete using isRemoved flag)
     * @param teamId the team ID to delete
     */
    void deleteTeam(int teamId);

    /**
     * Add a player to a team
     * @param teamId the team ID
     * @param playerUuid the player's UUID
     * @param position the position in the team (for ordering)
     * @throws SQLException if database operation fails
     */
    void addPlayerToTeam(int teamId, UUID playerUuid, int position) throws SQLException;

    /**
     * Remove a player from a team
     * @param teamId the team ID
     * @param playerUuid the player's UUID
     */
    void removePlayerFromTeam(int teamId, UUID playerUuid);

    /**
     * Select all players for a specific team
     * @param teamId the team ID
     * @return list of team player database rows
     * @throws SQLException if database operation fails
     */
    List<DbRow> selectTeamPlayers(int teamId) throws SQLException;

    /**
     * Check if a team name already exists
     * @param name the team name to check
     * @return true if name exists
     * @throws SQLException if database operation fails
     */
    boolean teamNameExists(String name) throws SQLException;

    /**
     * Select a team by name
     * @param name the team name
     * @return team database row or null if not found
     * @throws SQLException if database operation fails
     */
    DbRow selectTeamByName(String name) throws SQLException;

    /**
     * Update a team field
     * @param teamId the team ID
     * @param column the column to update
     * @param value the new value
     */
    void teamSet(int teamId, String column, String value);

    /**
     * Get the next position for a player in a team
     * @param teamId the team ID
     * @return the next available position
     * @throws SQLException if database operation fails
     */
    int getNextPlayerPosition(int teamId) throws SQLException;

    /**
     * Get a team by ID
     * @param teamId the team ID
     * @return Optional containing the team if found
     */
    java.util.Optional<Team> getTeam(Integer teamId);

    /**
     * Save or update a team's tuning profile as JSON
     * @param teamId the team ID
     * @param attributesJson the JSON string of tuning attributes
     */
    void saveTeamTuning(int teamId, String attributesJson);

    /**
     * Load a team's tuning profile JSON
     * @param teamId the team ID
     * @return the JSON string, or null if no tuning saved yet
     */
    String loadTeamTuning(int teamId) throws SQLException;
}