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

package org.screamingsandals.bedwars.commands;

import cloud.commandframework.Command;
import cloud.commandframework.CommandManager;
import gs.mclo.api.MclogsClient;
import org.screamingsandals.bedwars.VersionInfo;
import org.screamingsandals.bedwars.lang.LangKeys;
import org.screamingsandals.lib.Server;
import org.screamingsandals.lib.lang.Message;
import org.screamingsandals.lib.sender.CommandSender;
import org.screamingsandals.lib.spectator.Color;
import org.screamingsandals.lib.spectator.Component;
import org.screamingsandals.lib.spectator.event.ClickEvent;
import org.screamingsandals.lib.spectator.event.HoverEvent;
import org.screamingsandals.lib.tasker.Tasker;
import org.screamingsandals.lib.utils.annotations.Service;
import java.nio.file.Path;

@Service
public class DumpLogsCommand extends BaseCommand {
    public DumpLogsCommand() {
        super("dumplogs", BedWarsPermission.ADMIN_PERMISSION, true);
    }

    @Override
    protected void construct(Command.Builder<CommandSender> commandSenderWrapperBuilder, CommandManager<CommandSender> manager) {
        manager.command(
                commandSenderWrapperBuilder
                        .handler(commandContext -> {
                            var sender = commandContext.getSender();

                            Tasker.runAsync(() -> {
                                try {
                                    var response = new MclogsClient("ScreamingBedWars", VersionInfo.VERSION, Server.getVersion())
                                            .uploadLog(Path.of("./logs/latest.log"))
                                            .get();

                                    if (response.isSuccess()) {
                                        Message.of(LangKeys.DUMP_SUCCESS_LOGS)
                                                .defaultPrefix()
                                                .placeholder("dump", Component
                                                        .text()
                                                        .content(response.getUrl())
                                                        .color(Color.GRAY)
                                                        .clickEvent(ClickEvent.openUrl(response.getUrl()))
                                                        .hoverEvent(HoverEvent.showText(Component.text("Open this link"))))
                                                .send(sender);
                                    }
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                    Message.of(LangKeys.DUMP_FAILED).defaultPrefix().send(sender);
                                }
                            });
                        })
        );
    }
}
