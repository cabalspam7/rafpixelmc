/*
 * Copyright (C) 2025 ScreamingSandals
 *
 * This file is part of Screaming BedWars.
 *
 * Screaming BedWars is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Screaming BedWars is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Screaming BedWars. If not, see <https://www.gnu.org/licenses/>.
 */

package org.screamingsandals.bedwars.commands.admin;

import cloud.commandframework.Command;
import cloud.commandframework.CommandManager;
import cloud.commandframework.arguments.standard.IntegerArgument;
import cloud.commandframework.arguments.standard.StringArgument;
import org.screamingsandals.bedwars.lang.LangKeys;
import org.screamingsandals.lib.lang.Message;
import org.screamingsandals.lib.sender.CommandSender;
import org.screamingsandals.lib.utils.annotations.Service;

import java.util.HashMap;

@Service
public class LobbyCountdownCommand extends BaseAdminSubCommand {
    public LobbyCountdownCommand() {
        super("lobby-countdown");
    }

    @Override
    public void construct(CommandManager<CommandSender> manager, Command.Builder<CommandSender> commandSenderWrapperBuilder) {
        manager.command(
                commandSenderWrapperBuilder
                        .argument(IntegerArgument
                                .<CommandSender>newBuilder("countdown")
                                .withMin(10)
                                .withMax(600)
                        )
                        .argument(StringArgument.optional("dynamic-countdown"))
                        .handler(commandContext -> editMode(commandContext, (sender, game) -> {
                            int countdown = commandContext.get("countdown");
                            String dynamicCountdown = commandContext.getOrDefault("dynamic-countdown", null);

                            if (countdown >= 10 && countdown <= 600) {
                                game.setPauseCountdown(countdown);
                                if (dynamicCountdown != null) {
                                    var split = dynamicCountdown.split(",");
                                    var map = new HashMap<Integer, Integer>();
                                    for (var spl : split) {
                                        var spl2 = spl.split(":", 2);
                                        if (spl2.length != 2) {
                                            sender.sendMessage(Message
                                                    .of(LangKeys.ADMIN_ARENA_EDIT_ERRORS_INVALID_COUNTDOWN)
                                                    .defaultPrefix()
                                                    .placeholder("lowest", 1)
                                                    .placeholder("highest", 600)
                                            );
                                            return;
                                        }

                                        try {
                                            int players = Integer.parseInt(spl2[0]);
                                            int time = Integer.parseInt(spl2[1]);
                                            if (time <= 0 || time > 600) {
                                                sender.sendMessage(Message
                                                        .of(LangKeys.ADMIN_ARENA_EDIT_ERRORS_INVALID_COUNTDOWN)
                                                        .defaultPrefix()
                                                        .placeholder("lowest", 1)
                                                        .placeholder("highest", 600)
                                                );
                                                return;
                                            }

                                            map.put(players, time);
                                        } catch (NumberFormatException e) {
                                            sender.sendMessage(Message
                                                    .of(LangKeys.ADMIN_ARENA_EDIT_ERRORS_INVALID_COUNTDOWN)
                                                    .defaultPrefix()
                                                    .placeholder("lowest", 1)
                                                    .placeholder("highest", 600)
                                            );
                                            return;
                                        }
                                    }

                                    if (!map.isEmpty()) {
                                        game.setDynamicPauseCountdown(map);

                                        sender.sendMessage(Message
                                                .of(LangKeys.ADMIN_ARENA_EDIT_SUCCESS_LOBBY_COUNTDOWN_SET_DYNAMIC)
                                                .defaultPrefix()
                                                .placeholder("lowest", 10)
                                                .placeholder("highest", 600)
                                                .placeholder("dynamic", dynamicCountdown)
                                        );
                                        return;
                                    }
                                }
                                game.setDynamicPauseCountdown(null);
                                sender.sendMessage(Message.of(LangKeys.ADMIN_ARENA_EDIT_SUCCESS_LOBBY_COUNTDOWN_SET).placeholder("countdown", countdown).defaultPrefix());
                                return;
                            }

                            sender.sendMessage(Message
                                    .of(LangKeys.ADMIN_ARENA_EDIT_ERRORS_INVALID_COUNTDOWN)
                                    .defaultPrefix()
                                    .placeholder("lowest", 10)
                                    .placeholder("highest", 600)
                            );
                        }))
        );
    }
}
