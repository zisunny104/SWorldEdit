package ru.SocialMoods.SWorldEdit.i18n;

import cn.nukkit.utils.Config;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plugin-local language catalogues. No external plugin or global core translations required. */
public final class Messages {
    private static final Pattern ARGUMENT = Pattern.compile("\\$\\{(\\d+)}");
    private static volatile Map<String, String> current = bundled("en_US");
    private Messages() { }

    public static synchronized void configure(Path folder, String language, Consumer<String> warn) {
        String locale = language == null ? "en_US" : language.replace('-', '_');
        if (!locale.matches("[a-z]{2}_[A-Z]{2}")) {
            warn.accept("Invalid language: " + language + "; using en_US");
            locale = "en_US";
        }
        Map<String, String> next = new HashMap<>(bundled("en_US"));
        try {
            Path directory = folder.resolve("lang");
            Files.createDirectories(directory);
            for (String name : new String[]{"en_US", "zh_TW"}) {
                Path file = directory.resolve(name + ".yml");
                if (!Files.exists(file)) {
                    try (InputStream stream = Messages.class.getResourceAsStream("/lang/" + name + ".yml")) {
                        if (stream != null) Files.copy(stream, file);
                    }
                }
            }
            overlay(next, directory.resolve("en_US.yml"), warn);
            next.putAll(bundled(locale));
            overlay(next, directory.resolve(locale + ".yml"), warn);
        } catch (Exception e) { warn.accept("Cannot load language files: " + e.getMessage()); }
        current = Map.copyOf(next);
    }

    private static void overlay(Map<String, String> target, Path file, Consumer<String> warn) {
        if (!Files.isRegularFile(file)) return;
        try (InputStream stream = Files.newInputStream(file)) { target.putAll(read(stream)); }
        catch (Exception e) { warn.accept("Cannot read " + file.getFileName() + ": " + e.getMessage()); }
    }

    private static Map<String, String> bundled(String locale) {
        try (InputStream stream = Messages.class.getResourceAsStream("/lang/" + locale + ".yml")) {
            return stream == null ? Map.of() : read(stream);
        } catch (Exception e) { return Map.of(); }
    }

    private static Map<String, String> read(InputStream stream) {
        Config config = new Config(Config.YAML);
        if (!config.load(stream)) throw new IllegalArgumentException("Invalid language YAML");
        Map<String, String> values = new HashMap<>();
        config.getAll().forEach((key, value) -> {
            if (value instanceof String) values.put(key, (String)value);
        });
        return values;
    }

    public static String text(String key, Object... values) {
        String template = current.getOrDefault(key, key);
        Matcher matcher = ARGUMENT.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            int index;
            try { index = Integer.parseInt(matcher.group(1)); }
            catch (NumberFormatException e) { continue; }
            String value = index < values.length ? String.valueOf(values[index]) : matcher.group();
            matcher.appendReplacement(output, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
