package ru.SocialMoods.SWorldEdit;

import ru.SocialMoods.SWorldEdit.i18n.Messages;

import cn.nukkit.plugin.PluginBase;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import ru.SocialMoods.SWorldEdit.commands.*;
import ru.SocialMoods.SWorldEdit.repository.SchematicRepository;
import ru.SocialMoods.SWorldEdit.repository.impl.SchematicRepositoryImpl;
import ru.SocialMoods.SWorldEdit.service.SchematicService;
import ru.SocialMoods.SWorldEdit.service.impl.SchematicServiceImpl;

public class SWorldEdit extends PluginBase {

    private final Map<String, PlayerData> players = new HashMap<>();
    private static SWorldEdit instance;
    @Getter private ru.SocialMoods.SWorldEdit.preview.BoundingBoxes boundingBoxes;
    @Getter private SchematicRepository schematicRepository;
    @Getter private SchematicService schematicService;
    @Getter private ru.SocialMoods.SWorldEdit.utils.StreamingClipboard streamingClipboard;
    @Getter private ru.SocialMoods.SWorldEdit.utils.WorkTracker workTracker;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Messages.configure(getDataFolder().toPath(), getConfig().getString("language", "en_US"),
                message -> getLogger().warning(message));

        try {
            workTracker = new ru.SocialMoods.SWorldEdit.utils.WorkTracker(
                task -> getServer().getScheduler().scheduleDelayedTask(this,task,1),
                (owner,message) -> { if(owner.getPlayer()!=null && owner.getPlayer().isOnline())owner.getPlayer().sendMessage(message); },
                getConfig().getInt("progress.interval-seconds",5),System::nanoTime);
            if (getConfig().getBoolean("clipboard.streaming.enabled", true)) {
                streamingClipboard = new ru.SocialMoods.SWorldEdit.utils.StreamingClipboard(
                    new ru.SocialMoods.SWorldEdit.utils.StreamingClipboard.Settings(
                        getDataFolder().toPath().resolve("clipboard-cache"),
                        getConfig().getInt("clipboard.streaming.max-blocks", 50000000),
                        getConfig().getInt("clipboard.blocks-per-tick", 512),
                        getConfig().getInt("clipboard.streaming.page-cells", 2048),
                        getConfig().getInt("clipboard.streaming.max-states", 8192),
                        getConfig().getLong("clipboard.streaming.memory-bytes", 1048576),
                        getConfig().getLong("clipboard.streaming.disk-bytes", 1073741824),
                        getConfig().getLong("clipboard.streaming.reserve-bytes", 536870912),
                        java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(getConfig().getInt("clipboard.streaming.tick-millis", 3)),
                        getConfig().getInt("clipboard.streaming.max-jobs", 4),
                        getConfig().getInt("clipboard.streaming.max-history", 10),
                        getConfig().getInt("clipboard.streaming.max-stores", 128)),
                    task -> getServer().getScheduler().scheduleDelayedTask(this, task, 1),
                    level -> getServer().getLevel(level.getId()) == level);
                getLogger().info(Messages.text("streaming.enabled", getConfig().getInt("clipboard.streaming.max-blocks",50000000),
                        getConfig().getInt("clipboard.streaming.tick-millis",3)));
            }
            boundingBoxes = new ru.SocialMoods.SWorldEdit.preview.BoundingBoxes(this);
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
            if (workTracker != null) workTracker.close();
            if (streamingClipboard != null) streamingClipboard.close();
            if (boundingBoxes != null) boundingBoxes.close();
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

        data.attachPlayer(player);
        return data;
    }
}

