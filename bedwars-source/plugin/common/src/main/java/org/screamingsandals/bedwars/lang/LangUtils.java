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

package org.screamingsandals.bedwars.lang;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.screamingsandals.lib.tasker.TaskerTime;

@UtilityClass
public class LangUtils {
    public static @NotNull String @NotNull [] toUnitLangKey(@NotNull TaskerTime time, boolean plural) {
        switch (time) {
            case TICKS:
                return plural ? LangKeys.UNIT_TICKS : LangKeys.UNIT_TICK;
            case SECONDS:
                return plural ? LangKeys.UNIT_SECONDS : LangKeys.UNIT_SECOND;
            case MINUTES:
                return plural ? LangKeys.UNIT_MINUTES : LangKeys.UNIT_MINUTE;
            case HOURS:
                return plural ? LangKeys.UNIT_HOURS : LangKeys.UNIT_HOUR;
            default:
                return LangKeys.UNIT_TICKS; // how??
        }
    }
}
