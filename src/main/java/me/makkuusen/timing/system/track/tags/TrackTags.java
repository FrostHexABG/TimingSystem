package me.makkuusen.timing.system.track.tags;

import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.gui.TrackFilter;
import me.makkuusen.timing.system.track.Track;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TrackTags {


    private final Set<TrackTag> tags = new HashSet<>();
    private final int trackId;
    public TrackTags(int trackId) {
        this.trackId = trackId;
    }

    public boolean hasTag(TrackTag tag) {
        if (tag == null) {
            return true;
        }
        if (tag.value.equals("EMPTY")) {
            return tags.isEmpty();
        }
        return tags.contains(tag);
    }

    public boolean hasNoExcludedTags(TrackFilter filter) {
        for (TrackTag tag : filter.getExcludedTags()) {
            if (tag.value.equals("EMPTY") ? tags.isEmpty() : tags.contains(tag)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasAnyTag(TrackFilter filter) {
        if (filter.getTags().isEmpty()) {
            return true;
        }

        for (TrackTag tag : filter.getTags()) {
            if (tag.value.equals("EMPTY") && tags.isEmpty()) {
                return true;
            }
            if (tags.contains(tag)){
                return true;
            }
        }
        return false;
    }

    public boolean hasAllTags(TrackFilter filter) {
        if (filter.getTags().isEmpty()) {
            return true;
        }

        for (TrackTag tag : filter.getTags()) {
            if (tag.value.equals("EMPTY")) {
                if (tags.isEmpty()) {
                    continue;
                } else {
                    return false;
                }
            }
            if (!tags.contains(tag)){
                return false;
            }
        }
        return true;
    }


    public void add(TrackTag trackTag) {
        tags.add(trackTag);
    }

    public boolean create(TrackTag tag) {
        if (!tags.contains(tag)) {
            TimingSystem.getTrackDatabase().addTagToTrack(trackId, tag);
            tags.add(tag);
            return true;
        }
        return false;
    }

    public boolean remove(TrackTag tag) {
        if (tags.contains(tag)) {
            TimingSystem.getTrackDatabase().removeTagFromTrack(trackId, tag);
            tags.remove(tag);
            return true;
        }
        return false;
    }

    public List<TrackTag> get() {
        return tags.stream().sorted(Comparator.comparingInt(TrackTag::getWeight).reversed()).collect(Collectors.toList());
    }

    public List<TrackTag> getDisplayTags() {
        return tags.stream().filter(tag -> tag.getWeight() > 0).sorted(Comparator.comparingInt(TrackTag::getWeight).reversed()).collect(Collectors.toList());
    }
}
