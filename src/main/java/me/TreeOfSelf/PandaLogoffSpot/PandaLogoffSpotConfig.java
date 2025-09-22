package me.TreeOfSelf.PandaLogoffSpot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class PandaLogoffSpotConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("PandaLogoffSpot.json").toFile();

    private static ConfigData config = new ConfigData();

    public static class ConfigData {
        public String nameColor = "#F08080";
        public boolean showCoords = true;
        public int durationSeconds = 300;
        public float scale = 0.75f;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                config = GSON.fromJson(reader, ConfigData.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int getNameColor() {
        try {
            return Integer.parseInt(config.nameColor.replace("#", ""), 16);
        } catch (Exception e) {
            return 0xF08080;
        }
    }

    public static boolean shouldShowCoords() {
        return config.showCoords;
    }

    public static int getDurationSeconds() {
        return config.durationSeconds;
    }

    public static float getScale() {
        return config.scale;
    }
}