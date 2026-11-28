package io.github.libfdx.samples.g2d.platformer;

/** The six levels in play order. A new journey starts in the ravine after the lighthouse. */
public enum Trail {
    RAVINE("meadow", "Glasshouse Ravine", "Reach the glasshouse", "The glasshouse is awake"),
    QUARRY("quarry", "Amber Quarry", "Find the winding house", "The lift is running"),
    HOLLOW("hollow", "Fernwood Hollow", "Climb to the treehouse", "Shelter in the canopy"),
    SUMMIT("summit", "Snowline Ridge", "Reach the observatory", "The dome is open"),
    FORGE("forge", "Cinder Crossing", "Find the old furnace", "The forge is glowing"),
    SOUND("moonrise", "Lantern Sound", "Light the coastal beacon", "The beacon is lit");

    private static final Trail[] ROUTES = values();
    public final String id, title, levelTitle, objective, arrival;

    Trail(String id, String title, String objective, String arrival) {
        this.id = id;
        this.title = title;
        this.levelTitle = "Level " + (ordinal() + 1) + " · " + title;
        this.objective = objective;
        this.arrival = arrival;
    }

    public String mapPath() {
        return "levels/" + id + ".tmj";
    }

    public boolean isLast() {
        return this == SOUND;
    }

    public Trail next() {
        return ROUTES[(ordinal() + 1) % ROUTES.length];
    }

    public static Trail forMap(String path) {
        for (Trail trail : ROUTES) {
            if (trail.mapPath().equals(path)) return trail;
        }
        throw new IllegalArgumentException("Unknown trail " + path);
    }

}
