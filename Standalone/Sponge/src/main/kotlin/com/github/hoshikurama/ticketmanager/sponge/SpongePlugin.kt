package com.github.hoshikurama.ticketmanager.sponge



//@Plugin("ticketmanager")
class SpongePlugin {
//ConfigDir annotation, either with sharedRoot set to false
}

/*
/**
 * The main class of your Sponge plugin.
 *
 * <p>All methods are optional -- some common event registrations are included as a jumping-off point.</p>
 */
@Plugin("test")
public class Test {

    private final PluginContainer container;
    private final Logger logger;

    @Inject
    Test(final PluginContainer container, final Logger logger) {
        this.container = container;
        this.logger = logger;
    }

    @Listener
    public void onConstructPlugin(final ConstructPluginEvent event) {
        // Perform any one-time setup
        this.logger.info("Constructing test");
    }

    @Listener
    public void onServerStarting(final StartingEngineEvent<Server> event) {
        // Any setup per-game instance. This can run multiple times when
        // using the integrated (singleplayer) server.
    }

    @Listener
    public void onServerStopping(final StoppingEngineEvent<Server> event) {
        // Any tear down per-game instance. This can run multiple times when
        // using the integrated (singleplayer) server.
    }

    @Listener
    public void onRegisterCommands(final RegisterCommandEvent<Command.Parameterized> event) {
        // Register a simple command
        // When possible, all commands should be registered within a command register event
        final Parameter.Value<String> nameParam = Parameter.string().key("name").build();
        event.register(this.container, Command.builder()
                .addParameter(nameParam)
                .permission("test.command.greet")
                .executor(ctx -> {
                    final String name = ctx.requireOne(nameParam);
                    ctx.sendMessage(Identity.nil(), LinearComponents.linear(
                            NamedTextColor.AQUA,
                            Component.text("Hello "),
                            Component.text(name, Style.style(TextDecoration.BOLD)),
                            Component.text("!")
                    ));

                    return CommandResult.success();
                })
                .build(), "greet", "wave");
    }
}
 */