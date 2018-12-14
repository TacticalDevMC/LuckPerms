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

package me.lucko.luckperms.bukkit.inject.server;

import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableMap;

import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.treeview.PermissionRegistry;
import me.lucko.luckperms.common.util.LoadingMap;

import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginManager;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A replacement map for the 'permissions' instance in Bukkit's SimplePluginManager.
 *
 * This instance allows LuckPerms to intercept calls to
 * {@link PluginManager#addPermission(Permission)} and record permissions in the
 * {@link PermissionRegistry}.
 *
 * It also allows us to pre-determine child permission relationships.
 *
 * Injected by {@link InjectorPermissionMap}.
 */
public final class LPPermissionMap extends ForwardingMap<String, Permission> {

    // Uses perm.getName().toLowerCase(java.util.Locale.ENGLISH); to determine the key
    private final Map<String, Permission> delegate = new ConcurrentHashMap<>();

    // cache from permission --> children
    private final Map<String, Map<String, Boolean>> trueChildPermissions = LoadingMap.of(new ChildPermissionResolver(true));
    private final Map<String, Map<String, Boolean>> falseChildPermissions = LoadingMap.of(new ChildPermissionResolver(false));

    /**
     * The plugin instance
     */
    final LuckPermsPlugin plugin;

    public LPPermissionMap(LuckPermsPlugin plugin, Map<String, Permission> existingData) {
        this.plugin = plugin;
        putAll(existingData);
    }

    public Map<String, Boolean> getChildPermissions(String permission, boolean value) {
        return value ? this.trueChildPermissions.get(permission) : this.falseChildPermissions.get(permission);
    }

    private void update() {
        this.trueChildPermissions.clear();
        this.falseChildPermissions.clear();
    }

    @Override
    protected Map<String, Permission> delegate() {
        return this.delegate;
    }

    @Override
    public Permission put(@NonNull String key, @NonNull Permission value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        this.plugin.getPermissionRegistry().insert(key);
        Permission ret = super.put(key, value);
        update();
        return ret;
    }

    @Override
    public void putAll(@NonNull Map<? extends String, ? extends Permission> m) {
        for (Map.Entry<? extends String, ? extends Permission> ent : m.entrySet()) {
            put(ent.getKey(), ent.getValue());
        }
    }

    @Override
    public Permission putIfAbsent(String key, Permission value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        this.plugin.getPermissionRegistry().insert(key);
        Permission ret = super.putIfAbsent(key, value);
        update();
        return ret;
    }

    // null-safe - the plugin manager uses hashmap

    @Override
    public Permission remove(@Nullable Object object) {
        if (object == null) {
            return null;
        }
        return super.remove(object);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return key != null && value != null && super.remove(key, value);
    }

    @Override
    public boolean containsKey(@Nullable Object key) {
        return key != null && super.containsKey(key);
    }

    @Override
    public boolean containsValue(@Nullable Object value) {
        return value != null && super.containsValue(value);
    }

    @Override
    public Permission get(@Nullable Object key) {
        if (key == null) {
            return null;
        }
        return super.get(key);
    }

    private final class ChildPermissionResolver implements Function<String, Map<String, Boolean>> {
        private final boolean value;

        private ChildPermissionResolver(boolean value) {
            this.value = value;
        }

        @Override
        public Map<String, Boolean> apply(@NonNull String key) {
            Map<String, Boolean> children = new HashMap<>();
            resolveChildren(children, Collections.singletonMap(key, this.value), false);
            children.remove(key, this.value);
            return ImmutableMap.copyOf(children);
        }
    }

    private void resolveChildren(Map<String, Boolean> accumulator, Map<String, Boolean> children, boolean invert) {
        // iterate through the current known children.
        // the first time this method is called for a given permission, the children map will contain only the permission itself.
        for (Map.Entry<String, Boolean> e : children.entrySet()) {
            if (accumulator.containsKey(e.getKey())) {
                continue; // Prevent infinite loops
            }

            // xor the value using the parent (bukkit logic, not mine)
            boolean value = e.getValue() ^ invert;
            accumulator.put(e.getKey().toLowerCase(), value);

            // lookup any deeper children & resolve if present
            Permission perm = this.delegate.get(e.getKey());
            if (perm != null) {
                resolveChildren(accumulator, perm.getChildren(), !value);
            }
        }
    }

}