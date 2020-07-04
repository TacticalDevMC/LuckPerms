/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.commands.log;

import me.lucko.luckperms.common.actionlog.Log;
import me.lucko.luckperms.common.actionlog.LoggedAction;
import me.lucko.luckperms.common.command.CommandResult;
import me.lucko.luckperms.common.command.abstraction.ChildCommand;
import me.lucko.luckperms.common.command.access.CommandPermission;
import me.lucko.luckperms.common.command.tabcomplete.TabCompleter;
import me.lucko.luckperms.common.command.tabcomplete.TabCompletions;
import me.lucko.luckperms.common.command.utils.ArgumentList;
import me.lucko.luckperms.common.command.spec.CommandSpec;
import me.lucko.luckperms.common.locale.message.Message;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.sender.Sender;
import me.lucko.luckperms.common.storage.misc.DataConstraints;
import me.lucko.luckperms.common.util.Paginated;
import me.lucko.luckperms.common.util.Predicates;

import java.util.List;

public class LogTrackHistory extends ChildCommand<Log> {
    private static final int ENTRIES_PER_PAGE = 10;

    public LogTrackHistory() {
        super(CommandSpec.LOG_TRACK_HISTORY, "trackhistory", CommandPermission.LOG_TRACK_HISTORY, Predicates.notInRange(1, 2));
    }

    @Override
    public CommandResult execute(LuckPermsPlugin plugin, Sender sender, Log log, ArgumentList args, String label) {
        String track = args.get(0).toLowerCase();
        if (!DataConstraints.TRACK_NAME_TEST.test(track)) {
            Message.TRACK_INVALID_ENTRY.send(sender, track);
            return CommandResult.INVALID_ARGS;
        }

        Paginated<LoggedAction> content = new Paginated<>(log.getTrackHistory(track));

        int page = args.getIntOrDefault(1, Integer.MIN_VALUE);
        if (page != Integer.MIN_VALUE) {
            return showLog(page, sender, content);
        } else {
            return showLog(content.getMaxPages(ENTRIES_PER_PAGE), sender, content);
        }
    }

    private static CommandResult showLog(int page, Sender sender, Paginated<LoggedAction> log) {
        int maxPage = log.getMaxPages(ENTRIES_PER_PAGE);
        if (maxPage == 0) {
            Message.LOG_NO_ENTRIES.send(sender);
            return CommandResult.STATE_ERROR;
        }

        if (page == Integer.MIN_VALUE) {
            page = maxPage;
        }

        if (page < 1 || page > maxPage) {
            Message.LOG_INVALID_PAGE_RANGE.send(sender, maxPage);
            return CommandResult.INVALID_ARGS;
        }

        List<Paginated.Entry<LoggedAction>> entries = log.getPage(page, ENTRIES_PER_PAGE);
        String name = entries.stream().findAny().get().value().getTarget().getName();
        Message.LOG_HISTORY_TRACK_HEADER.send(sender, name, page, maxPage);

        for (Paginated.Entry<LoggedAction> e : entries) {
            Message.LOG_ENTRY.send(sender, e.position(), e.value());
        }

        return CommandResult.SUCCESS;
    }

    @Override
    public List<String> tabComplete(LuckPermsPlugin plugin, Sender sender, ArgumentList args) {
        return TabCompleter.create()
                .at(0, TabCompletions.tracks(plugin))
                .complete(args);
    }
}
