package ro.atf.holdem.android;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Stores every update from one settled result in the same crash-safe write. */
final class GameplayAchievementFile implements GameplayAchievements.Storage {
    private final AtomicFile file;

    private GameplayAchievementFile(File directory) {
        file = new AtomicFile(new File(directory, "gameplay-achievements.json"));
    }

    static GameplayAchievements local(Context context) {
        return new GameplayAchievements(new GameplayAchievementFile(context.getFilesDir()));
    }

    @Override public Map<GameplayAchievements.Metric, Set<String>> read(String profile) {
        try {
            var result = new EnumMap<GameplayAchievements.Metric, Set<String>>(GameplayAchievements.Metric.class);
            var entry = document().optJSONObject(profile);
            if (entry == null) return result;
            for (var metric : GameplayAchievements.Metric.values()) {
                var array = entry.optJSONArray(metric.name());
                if (array == null) continue;
                var ids = new HashSet<String>();
                for (int index = 0; index < array.length(); index++) ids.add(array.getString(index));
                // An older catalog may have credited a higher target. Preserve those event IDs.
                if (ids.size() != array.length())
                    throw new JSONException("Invalid recorded IDs for " + metric);
                result.put(metric, Set.copyOf(ids));
            }
            return result;
        } catch (IOException | JSONException failure) {
            throw new IllegalStateException("Could not read gameplay achievements", failure);
        }
    }

    @Override public void write(String profile, Map<GameplayAchievements.Metric, Set<String>> updates) {
        try {
            var root = document();
            var entry = root.optJSONObject(profile);
            if (entry == null) {
                entry = new JSONObject();
                root.put(profile, entry);
            }
            for (var update : updates.entrySet()) entry.put(update.getKey().name(), new JSONArray(update.getValue()));
            var stream = file.startWrite();
            try {
                stream.write(root.toString().getBytes(StandardCharsets.UTF_8));
                stream.flush();
                stream.getFD().sync();
                file.finishWrite(stream);
            } catch (IOException | RuntimeException failure) {
                file.failWrite(stream);
                throw failure;
            }
        } catch (IOException | JSONException failure) {
            throw new IllegalStateException("Could not save gameplay achievements", failure);
        }
    }

    private JSONObject document() throws IOException, JSONException {
        try {
            return new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
        } catch (FileNotFoundException missing) {
            return new JSONObject();
        }
    }
}
