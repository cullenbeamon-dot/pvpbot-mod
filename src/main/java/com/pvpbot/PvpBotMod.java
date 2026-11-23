package com.pvpbot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

public class PvpBotMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(BotManager::tick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("pvpbot")
                .then(Commands.literal("spawn")
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                            Arrays.stream(BotMode.values()).map(m -> m.name().toLowerCase()), b))
                        .executes(c -> spawn(c.getSource().getPlayerOrException(),
                            StringArgumentType.getString(c, "mode"), 5))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                            .executes(c -> spawn(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "mode"),
                                IntegerArgumentType.getInteger(c, "level"))))))
                .then(Commands.literal("clear").executes(c -> {
                    int n = BotManager.clear();
                    c.getSource().sendSuccess(() -> Component.literal("Removed " + n + " bot(s)"), false);
                    return n;
                }))));
    }

    private static int spawn(ServerPlayer p, String modeName, int level) {
        BotMode mode = BotMode.parse(modeName);
        if (mode == null) {
            p.sendSystemMessage(Component.literal("Modes: sword, pot, mace, cart, smp"));
            return 0;
        }
        BotManager.spawn(p, mode, level);
        p.sendSystemMessage(Component.literal("Spawned " + mode + " bot, level " + level));
        return 1;
    }
}
