package ru.SocialMoods.SWorldEdit;

import cn.nukkit.plugin.PluginBase;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import ru.SocialMoods.SWorldEdit.commands.*;
import ru.SocialMoods.SWorldEdit.commands.geometry.*;
import ru.SocialMoods.SWorldEdit.repository.SchematicRepository;
import ru.SocialMoods.SWorldEdit.repository.impl.SchematicRepositoryImpl;
import ru.SocialMoods.SWorldEdit.service.SchematicService;
import ru.SocialMoods.SWorldEdit.service.impl.SchematicServiceImpl;

@Slf4j
public class SWorldEdit extends PluginBase {

    private final Map<String, PlayerData> players = new HashMap<>();
    private static SWorldEdit instance;
    @Getter private ru.SocialMoods.SWorldEdit.preview.BoundingBoxes boundingBoxes;
    @Getter private SchematicRepository schematicRepository;
    @Getter private SchematicService schematicService;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        

        try {
            boundingBoxes = new ru.SocialMoods.SWorldEdit.preview.BoundingBoxes(this);
            this.getServer().getPluginManager().registerEvents(new Listener(), this);

            schematicRepository = new SchematicRepositoryImpl(getDataFolder());
            schematicService = new SchematicServiceImpl(schematicRepository);

            registerCommands();

            this.getLogger().info("SWorldEdit has been enabled successfully");

        } catch (Exception e) {
            this.getLogger().error(String.format("Error enabling SWorldEdit: %1$s", e));
        }
    }

    @Override
    public void onDisable() {
        try {
            if (boundingBoxes != null) boundingBoxes.close();
            this.getLogger().info("SWorldEdit has been disabled");
        } catch (Exception e) {
            this.getLogger().error(String.format("Error disabling SWorldEdit: %1$s", e));
        }
    }

    private void registerCommands() {
        for (var command : CommandRegistry.create(schematicService)) {
            if (!getServer().getCommandMap().register("worldedit", command)) {
                getLogger().warning("Command /" + command.getName() + " conflicts with another plugin; fallback: /worldedit:" + command.getName());
            }
        }
    }

    public static SWorldEdit get() {
        return instance;
    }

    public PlayerData getWEPlayer(cn.nukkit.Player player) {
        if (player == null) {
            return null;
        }

        String playerName = player.getName().toLowerCase();
        PlayerData data = this.players.get(playerName);

        if (data == null) {
            try {
                data = new PlayerData(player);
                this.players.put(playerName, data);
            } catch (Exception e) {
                this.getLogger().error(String.format("Error creating WEPlayer for %1$s: %2$s", playerName, e.getMessage()));
                return null;
            }
        }

        data.attachPlayer(player);
        return data;
    }
}

