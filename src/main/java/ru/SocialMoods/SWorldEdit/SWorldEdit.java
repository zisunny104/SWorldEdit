package ru.SocialMoods.SWorldEdit;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

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
    @Getter private SchematicRepository schematicRepository;
    @Getter private SchematicService schematicService;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Messages.configure(getDataFolder().toPath(), getConfig().getString("language", "en_US"),
                message -> getLogger().warning(message));

        try {
            this.getServer().getPluginManager().registerEvents(new Listener(), this);

            schematicRepository = new SchematicRepositoryImpl(getDataFolder());
            schematicService = new SchematicServiceImpl(schematicRepository);

            registerCommands();

            this.getLogger().info(Messages.text("text.sworldedit.has.been.enabled.successfully"));

        } catch (Exception e) {
            this.getLogger().error(Messages.text("text.error.enabling.sworldedit", e));
        }
    }

    @Override
    public void onDisable() {
        try {
            this.getLogger().info(Messages.text("text.sworldedit.has.been.disabled"));
        } catch (Exception e) {
            this.getLogger().error(Messages.text("text.error.disabling.sworldedit", e));
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
                this.getLogger().error(Messages.text("text.error.creating.weplayer.for", playerName, e.getMessage()));
                return null;
            }
        }

        return data;
    }
}

