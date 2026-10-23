package ru.SocialMoods.SWorldEdit.i18n;

import cn.nukkit.utils.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {
    @TempDir Path dir;
    @Test void traditionalChineseAndOnePassReplacement() {
        Messages.configure(dir, "zh-TW", message -> fail(message));
        assertEquals("已複製 12 個方塊至剪貼簿", Messages.text("text.successfully.copied.blocks.to.clipboard", 12));
        assertEquals("錯誤：${1}\\$", Messages.text("text.error", "${1}\\$", "should not substitute"));
        assertEquals("missing.key", Messages.text("missing.key"));
    }
    @Test void externalOverridesAndEnglishFallbackForCustomLocales() throws Exception {
        Files.createDirectories(dir.resolve("lang"));
        Files.writeString(dir.resolve("lang/fr_FR.yml"), "text.error: 'Test ${0}'\n");
        Messages.configure(dir, "fr_FR", message -> fail(message));
        assertEquals("Test OK", Messages.text("text.error", "OK"));
        assertEquals("Player data not found", Messages.text("text.player.data.not.found"));
        assertTrue(Files.exists(dir.resolve("lang/zh_TW.yml")));
    }
    @Test void bundledCataloguesHaveSameKeysAndPlaceholders() {
        Config en = load("en_US"), zh = load("zh_TW");
        assertFalse(en.getAll().isEmpty());
        assertEquals(en.getAll().keySet(), zh.getAll().keySet());
        for (Map.Entry<String, Object> entry : en.getAll().entrySet()) {
            assertEquals(arguments(entry.getValue().toString()), arguments(zh.getString(entry.getKey())), entry.getKey());
        }
    }
    private static Set<String> arguments(String text) {
        Set<String> result = new TreeSet<>();
        var matcher = Pattern.compile("\\$\\{\\d+}").matcher(text);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }
    private static Config load(String locale) {
        Config config = new Config(Config.YAML);
        assertTrue(config.load(Messages.class.getResourceAsStream("/lang/" + locale + ".yml")));
        return config;
    }
}
