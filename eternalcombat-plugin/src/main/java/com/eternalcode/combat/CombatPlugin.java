package com.eternalcode.combat;

import com.eternalcode.combat.border.BorderTriggerController;
import com.eternalcode.combat.border.BorderService;
import com.eternalcode.combat.border.BorderServiceImpl;
import com.eternalcode.combat.border.animation.block.BorderBlockController;
import com.eternalcode.combat.border.animation.particle.ParticleController;
import com.eternalcode.combat.bridge.BridgeService;
import com.eternalcode.combat.fight.drop.DropKeepInventoryService;
import com.eternalcode.combat.fight.FightManager;
import com.eternalcode.combat.fight.drop.DropService;
import com.eternalcode.combat.fight.effect.FightEffectService;
import com.eternalcode.combat.fight.knockback.KnockbackService;
import com.eternalcode.combat.fight.tagout.FightTagOutService;
import com.eternalcode.combat.fight.pearl.FightPearlService;
import com.eternalcode.combat.handler.InvalidUsageHandlerImpl;
import com.eternalcode.combat.handler.MissingPermissionHandlerImpl;
import com.eternalcode.combat.config.ConfigService;
import com.eternalcode.combat.config.implementation.PluginConfig;
import com.eternalcode.combat.fight.drop.DropController;
import com.eternalcode.combat.fight.drop.DropKeepInventoryServiceImpl;
import com.eternalcode.combat.fight.drop.DropServiceImpl;
import com.eternalcode.combat.fight.drop.impl.PercentDropModifier;
import com.eternalcode.combat.fight.drop.impl.PlayersHealthDropModifier;
import com.eternalcode.combat.fight.FightTagCommand;
import com.eternalcode.combat.fight.controller.FightActionBlockerController;
import com.eternalcode.combat.fight.controller.FightMessageController;
import com.eternalcode.combat.fight.controller.FightTagController;
import com.eternalcode.combat.fight.controller.FightUnTagController;
import com.eternalcode.combat.fight.effect.FightEffectController;
import com.eternalcode.combat.event.EventManager;
import com.eternalcode.combat.fight.FightManagerImpl;
import com.eternalcode.combat.fight.FightTask;
import com.eternalcode.combat.fight.effect.FightEffectServiceImpl;
import com.eternalcode.combat.fight.logout.LogoutController;
import com.eternalcode.combat.fight.logout.LogoutService;
import com.eternalcode.combat.fight.pearl.FightPearlController;
import com.eternalcode.combat.fight.pearl.FightPearlServiceImpl;
import com.eternalcode.combat.fight.tagout.FightTagOutController;
import com.eternalcode.combat.fight.tagout.FightTagOutServiceImpl;
import com.eternalcode.combat.fight.tagout.FightTagOutCommand;
import com.eternalcode.combat.notification.NoticeService;
import com.eternalcode.combat.fight.knockback.KnockbackRegionController;
import com.eternalcode.combat.region.RegionProvider;
import com.eternalcode.combat.updater.UpdaterNotificationController;
import com.eternalcode.combat.updater.UpdaterService;
import com.eternalcode.commons.adventure.AdventureLegacyColorPostProcessor;
import com.eternalcode.commons.adventure.AdventureLegacyColorPreProcessor;
import com.eternalcode.commons.bukkit.scheduler.BukkitSchedulerImpl;
import com.eternalcode.commons.scheduler.Scheduler;
import com.eternalcode.multification.notice.Notice;
import com.github.retrooper.packetevents.PacketEvents;
import com.google.common.base.Stopwatch;
import dev.rollczi.litecommands.LiteCommands;
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory;
import dev.rollczi.litecommands.bukkit.LiteBukkitMessages;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import net.kyori.adventure.platform.AudienceProvider;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bstats.bukkit.Metrics;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class CombatPlugin extends JavaPlugin implements EternalCombatApi {

    private static final String FALLBACK_PREFIX = "eternalcombat";
    private static final int BSTATS_METRICS_ID = 17803;

    private FightManager fightManager;
    private FightPearlService fightPearlService;
    private FightTagOutService fightTagOutService;
    private FightEffectService fightEffectService;

    private LogoutService logoutService;

    private DropService dropService;
    private DropKeepInventoryService dropKeepInventoryService;

    private RegionProvider regionProvider;

    private PluginConfig pluginConfig;

    private AudienceProvider audienceProvider;
    private LiteCommands<CommandSender> liteCommands;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        Stopwatch started = Stopwatch.createStarted();
        Server server = this.getServer();

        File dataFolder = this.getDataFolder();

        ConfigService configService = new ConfigService();

        EventManager eventManager = new EventManager(this);
        Scheduler scheduler = new BukkitSchedulerImpl(this);

        PacketEvents.getAPI().init();

        this.pluginConfig = configService.create(PluginConfig.class, new File(dataFolder, "config.yml"));

        this.fightManager = new FightManagerImpl(eventManager);
        this.fightPearlService = new FightPearlServiceImpl(this.pluginConfig.pearl);
        this.fightTagOutService = new FightTagOutServiceImpl();
        this.fightEffectService = new FightEffectServiceImpl();
        this.logoutService = new LogoutService();
        this.dropService = new DropServiceImpl();
        this.dropKeepInventoryService = new DropKeepInventoryServiceImpl();

        UpdaterService updaterService = new UpdaterService(this.getDescription());

        this.audienceProvider = BukkitAudiences.create(this);
        MiniMessage miniMessage = MiniMessage.builder()
            .postProcessor(new AdventureLegacyColorPostProcessor())
            .preProcessor(new AdventureLegacyColorPreProcessor())
            .build();

        BridgeService bridgeService = new BridgeService(this.pluginConfig, server.getPluginManager(), this.getLogger(), this);
        bridgeService.init(this.fightManager, server);
        this.regionProvider = bridgeService.getRegionProvider();
        BorderService borderService = new BorderServiceImpl(scheduler, server, regionProvider, eventManager, pluginConfig.border);
        KnockbackService knockbackService = new KnockbackService(this.pluginConfig, scheduler);

        NoticeService noticeService = new NoticeService(this.audienceProvider, this.pluginConfig, miniMessage);

        this.liteCommands = LiteBukkitFactory.builder(FALLBACK_PREFIX, this, server)
            .message(LiteBukkitMessages.PLAYER_NOT_FOUND, this.pluginConfig.messagesSettings.playerNotFound)
            .message(LiteBukkitMessages.PLAYER_ONLY, this.pluginConfig.messagesSettings.admin.onlyForPlayers)

            .invalidUsage(new InvalidUsageHandlerImpl(this.pluginConfig, noticeService))
            .missingPermission(new MissingPermissionHandlerImpl(this.pluginConfig, noticeService))

            .commands(
                new FightTagCommand(this.fightManager, noticeService, this.pluginConfig),
                new FightTagOutCommand(this.fightTagOutService, noticeService, this.pluginConfig),
                new EternalCombatReloadCommand(configService, noticeService)
            )

            .result(Notice.class, (invocation, result, chain) -> noticeService.create()
                .viewer(invocation.sender())
                .notice(result)
                .send())

            .build();

        FightTask fightTask = new FightTask(server, this.pluginConfig, this.fightManager, noticeService);
        this.getServer().getScheduler().runTaskTimer(this, fightTask, 20L, 20L);

        new Metrics(this, BSTATS_METRICS_ID);

        Stream.of(
            new PercentDropModifier(this.pluginConfig.drop),
            new PlayersHealthDropModifier(this.pluginConfig.drop, this.logoutService)
        ).forEach(this.dropService::registerModifier);

        eventManager.subscribe(
            new FightTagController(this.fightManager, this.pluginConfig),
            new FightUnTagController(this.fightManager, this.pluginConfig, this.logoutService),
            new FightActionBlockerController(this.fightManager, noticeService, this.pluginConfig, server),
            new FightPearlController(this.pluginConfig.pearl, noticeService, this.fightManager, this.fightPearlService),
            new UpdaterNotificationController(updaterService, this.pluginConfig, this.audienceProvider, miniMessage),
            new KnockbackRegionController(noticeService, this.regionProvider, this.fightManager, knockbackService, server),
            new FightEffectController(this.pluginConfig.effect, this.fightEffectService, this.fightManager, this.getServer()),
            new FightTagOutController(this.fightTagOutService),
            new FightMessageController(this.fightManager, noticeService, this.pluginConfig, this.getServer()),
            new BorderTriggerController(borderService, pluginConfig.border, fightManager, server),
            new ParticleController(borderService, pluginConfig.border.particle, scheduler, server),
            new BorderBlockController(borderService, pluginConfig.border.block, scheduler, server)
        );

        eventManager.subscribe(
            PlayerDeathEvent.class,
            this.pluginConfig.drop.dropEventPriority,
            new DropController(dropService, dropKeepInventoryService, pluginConfig.drop, fightManager)
        );

        eventManager.subscribe(
            PlayerQuitEvent.class,
            this.pluginConfig.combat.quitPunishmentEventPriority,
            new LogoutController(this.fightManager, this.logoutService, noticeService, this.pluginConfig)
        );

        EternalCombatProvider.initialize(this);

        long millis = started.elapsed(TimeUnit.MILLISECONDS);
        this.getLogger().info("Successfully loaded EternalCombat in " + millis + "ms");
    }

    @Override
    public void onDisable() {
        EternalCombatProvider.deinitialize();

        if (this.liteCommands != null) {
            this.liteCommands.unregister();
        }

        if (this.audienceProvider != null) {
            this.audienceProvider.close();
        }

        this.fightManager.untagAll();

        PacketEvents.getAPI().terminate();
    }

    @Override
    public FightManager getFightManager() {
        return this.fightManager;
    }

    @Override
    public RegionProvider getRegionProvider() {
        return this.regionProvider;
    }

    @Override
    public FightPearlService getFightPearlService() {
        return this.fightPearlService;
    }

    @Override
    public FightTagOutService getFightTagOutService() {
        return this.fightTagOutService;
    }

    @Override
    public FightEffectService getFightEffectService() {
        return this.fightEffectService;
    }

    @Override
    public DropService getDropService() {
        return this.dropService;
    }

    @Override
    public DropKeepInventoryService getDropKeepInventoryService() {
        return this.dropKeepInventoryService;
    }
}
