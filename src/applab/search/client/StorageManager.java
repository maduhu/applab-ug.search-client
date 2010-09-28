package applab.search.client;

import applab.client.ApplabActivity;

/**
 * Singleton that is responsible for managing the local storage of our data.
 * 
 * This is primarily our local cache of keywords and results, but also includes local storage of our unsent queries and
 * local search usage
 * 
 * TODO: clean this up, unify with Storage class, and migrate the majority to common code
 */
public class StorageManager {
    private static StorageManager singleton = new StorageManager();
    private Storage legacyStorage;
    private boolean hasKeywords;

    private StorageManager() {
    }

    public static boolean hasKeywords() {
        return singleton.privateHasKeywords();
    }

    public static String getActiveTable() {
        String table = GlobalConstants.DATABASE_TABLE;
        // Check if other table qualifies otherwise return above table
        if (singleton.getLegacyStorage().tableExistsAndIsValid(GlobalConstants.DATABASE_TABLE2)) {
            table = GlobalConstants.DATABASE_TABLE2;
        }

        return table;
    }

    private Storage getLegacyStorage() {
        if (this.legacyStorage == null) {
            this.legacyStorage = new Storage(ApplabActivity.getGlobalContext());
            this.legacyStorage.open();
        }

        return this.legacyStorage;
    }

    private boolean privateHasKeywords() {
        // once we have valid data, that never changes, but we can
        // switch from invalid to valid at any time
        if (!this.hasKeywords) {
            this.hasKeywords = getLegacyStorage().tableExistsAndIsValid(GlobalConstants.DATABASE_TABLE)
                    || getLegacyStorage().tableExistsAndIsValid(GlobalConstants.DATABASE_TABLE2);
        }

        return this.hasKeywords;
    }
}
