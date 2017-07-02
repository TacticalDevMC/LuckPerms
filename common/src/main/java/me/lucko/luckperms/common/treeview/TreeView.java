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

package me.lucko.luckperms.common.treeview;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import me.lucko.luckperms.api.Tristate;
import me.lucko.luckperms.api.caching.PermissionData;
import me.lucko.luckperms.common.utils.PasteUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class TreeView {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

    private final String rootPosition;
    private final int maxLevels;

    private final ImmutableTreeNode view;

    public TreeView(PermissionVault source, String rootPosition, int maxLevels) {
        this.rootPosition = rootPosition;
        this.maxLevels = maxLevels;

        Optional<TreeNode> root = findRoot(source);
        this.view = root.map(TreeNode::makeImmutableCopy).orElse(null);
    }

    public boolean hasData() {
        return view != null;
    }

    public String uploadPasteData(String version) {
        if (!hasData()) {
            throw new IllegalStateException();
        }

        List<Map.Entry<String, String>> ret = asTreeList();
        ImmutableList.Builder<String> builder = getPasteHeader(version, "none", ret.size());
        builder.add("```");

        for (Map.Entry<String, String> e : ret) {
            builder.add(e.getKey() + e.getValue());
        }

        builder.add("```");
        ret.clear();

        return PasteUtils.paste("LuckPerms Permission Tree", ImmutableList.of(Maps.immutableEntry("luckperms-tree.md", builder.build().stream().collect(Collectors.joining("\n")))));
    }

    public String uploadPasteData(String version, String username, PermissionData checker) {
        if (!hasData()) {
            throw new IllegalStateException();
        }

        List<Map.Entry<String, String>> ret = asTreeList();
        ImmutableList.Builder<String> builder = getPasteHeader(version, username, ret.size());
        builder.add("```diff");

        for (Map.Entry<String, String> e : ret) {
            Tristate tristate = checker.getPermissionValue(e.getValue());
            builder.add(getTristateDiffPrefix(tristate) + e.getKey() + e.getValue());
        }

        builder.add("```");
        ret.clear();

        return PasteUtils.paste("LuckPerms Permission Tree", ImmutableList.of(Maps.immutableEntry("luckperms-tree.md", builder.build().stream().collect(Collectors.joining("\n")))));
    }

    private static String getTristateDiffPrefix(Tristate t) {
        switch (t) {
            case TRUE:
                return "+ ";
            case FALSE:
                return "- ";
            default:
                return "# ";
        }
    }

    private ImmutableList.Builder<String> getPasteHeader(String version, String referenceUser, int size) {
        String date = DATE_FORMAT.format(new Date(System.currentTimeMillis()));
        String selection = rootPosition.equals(".") ? "any" : "`" + rootPosition + "`";

        return ImmutableList.<String>builder()
                .add("## Permission Tree")
                .add("#### This file was automatically generated by [LuckPerms](https://github.com/lucko/LuckPerms) v" + version)
                .add("")
                .add("### Metadata")
                .add("| Selection | Max Recursion | Reference User | Size | Produced at |")
                .add("|-----------|---------------|----------------|------|-------------|")
                .add("| " + selection + " | " + maxLevels + " | " + referenceUser + " | **" + size + "** | " + date + " |")
                .add("")
                .add("### Output");
    }

    private Optional<TreeNode> findRoot(PermissionVault source) {
        TreeNode root = source.getRootNode();

        if (rootPosition.equals(".")) {
            return Optional.of(root);
        }

        List<String> parts = Splitter.on('.').omitEmptyStrings().splitToList(rootPosition);
        for (String part : parts) {

            if (!root.getChildren().isPresent()) {
                return Optional.empty();
            }

            Map<String, TreeNode> branch = root.getChildren().get();

            root = branch.get(part);
            if (root == null) {
                return Optional.empty();
            }
        }

        return Optional.of(root);
    }

    private List<Map.Entry<String, String>> asTreeList() {
        String prefix = rootPosition.equals(".") ? "" : (rootPosition + ".");
        List<Map.Entry<String, String>> ret = new ArrayList<>();

        for (Map.Entry<Integer, String> s : view.getNodeEndings()) {
            if (s.getKey() >= maxLevels) {
                continue;
            }

            String treeStructure = Strings.repeat("│  ", s.getKey()) + "├── ";
            String node = prefix + s.getValue();

            ret.add(Maps.immutableEntry(treeStructure, node));
        }

        return ret;
    }

}
