package CoplenChristian.FileManagerGUI.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class Settings {
    public enum Theme { LIGHT, DARK }

    public Theme theme = Theme.LIGHT;
    public boolean alwaysPermanentDelete = false;
    public boolean confirmPermanentDelete = true;

    private static final File SETTINGS_FILE = new File("settings.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Load settings.json if it exists, else defaults
    public static Settings load() {
        if (SETTINGS_FILE.exists()) {
            try {
                return MAPPER.readValue(SETTINGS_FILE, Settings.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new Settings(); // defaults
    }

    // Save to settings.json
    public void save() {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(SETTINGS_FILE, this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
