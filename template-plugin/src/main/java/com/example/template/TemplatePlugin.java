package com.example.template;

import com.example.template.command.TemplateCommand;
import com.example.template.data.PlayerData;
import com.example.template.data.PlayerRepository;
import com.example.template.dev.DevMode;
import com.example.template.module.SidebarModule;
import com.example.template.module.StatsModule;
import com.example.template.module.WelcomeModule;
import dev.lunamc.moonlib.MoonPlugin;
import dev.lunamc.moonlib.command.CommandMessages;
import dev.lunamc.moonlib.config.ConfigFile;
import dev.lunamc.moonlib.config.Messages;
import dev.lunamc.moonlib.database.Database;
import dev.lunamc.moonlib.module.ModuleManager;
import dev.lunamc.moonlib.player.PlayerCache;
import dev.lunamc.moonlib.util.Log;
import dev.lunamc.moonlib.util.UpdateChecker;

import java.util.concurrent.TimeUnit;

/**
 * The plugin's entry point. It only wires things together; every feature lives in a module
 * ({@code module/}), so features can be turned off in config.yml, reloaded on their own, and
 * hot-reloaded by {@link DevMode} while you develop.
 *
 * <p>Start order matters a little: config and messages first, then storage, then modules (which
 * use storage), then commands (which use modules).</p>
 */
public final class TemplatePlugin extends MoonPlugin {

    private ConfigFile settings;
    private Messages messages;
    private Database database;
    private PlayerCache<PlayerData> players;
    private ModuleManager modules;

    @Override
    protected void enable() {
        // ---- configuration: both files reload by themselves when you save them in an editor
        settings = loadConfig("config.yml");
        messages = new Messages(this).useRichText(true);
        applySettings();
        settings.watch(this, () -> {
            applySettings();
            modules.reloadAll();
            Log.info("config.yml changed on disk and was reloaded");
        });
        messages.file().watch(this, () -> {
            CommandMessages.load(messages.file().getSection("commands"));
            Log.info("messages.yml changed on disk and was reloaded");
        });

        // ---- storage: SQLite by default, MySQL/MariaDB by changing config.yml
        database = Database.fromConfig(this, settings, "database");
        // Startup is the one moment blocking is fine; run on a database thread and wait.
        database.async(db -> db.migrations()
                .add(1, "create players table",
                        "CREATE TABLE IF NOT EXISTS template_players ("
                                + "uuid CHAR(36) PRIMARY KEY, "
                                + "name VARCHAR(16) NOT NULL, "
                                + "joins INTEGER NOT NULL DEFAULT 0, "
                                + "blocks_broken BIGINT NOT NULL DEFAULT 0, "
                                + "first_join BIGINT NOT NULL, "
                                + "last_seen BIGINT NOT NULL)")
                .run()).join();

        PlayerRepository repository = new PlayerRepository(database);
        players = PlayerCache.<PlayerData>builder(this)
                .loader(repository::load)
                .saver(repository::save)
                .autosave(settings.getInt("storage.autosave-minutes", 5), TimeUnit.MINUTES)
                .failedLoadMessage(messages.get("data-load-failed"))
                .build();

        // ---- features
        modules = new ModuleManager(this)
                .toggles(settings, "modules")
                .register(new StatsModule(this), new WelcomeModule(this), new SidebarModule(this));
        modules.enableAll();

        // ---- commands: one annotated class, no plugin.yml entries needed
        registerCommands(new TemplateCommand(this));

        // ---- optional extras
        String modrinth = settings.getString("update-check.modrinth-project", "");
        if (!modrinth.isEmpty()) {
            UpdateChecker.modrinth(this, modrinth).notifyPermission("template.admin").checkEvery(6, TimeUnit.HOURS).start();
        }
        DevMode.startIfEnabled(this);
    }

    @Override
    protected void disable() {
        // Nothing to do: MoonLib disables the modules, saves every player, finishes queued
        // database writes and closes the database, in that order, after this returns.
    }

    private void applySettings() {
        Log.setDebug(settings.getBoolean("debug", false));
        CommandMessages.load(messages.file().getSection("commands"));
    }

    /** Reloads config, messages and every module, for {@code /template reload}. */
    public void reloadAll() {
        settings.reload();
        messages.reload();
        applySettings();
        modules.reloadAll();
    }

    public ConfigFile settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Database database() {
        return database;
    }

    public PlayerCache<PlayerData> players() {
        return players;
    }

    public ModuleManager modules() {
        return modules;
    }
}
