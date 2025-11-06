/*
 * Copyright (c) 2025 AzSuawa. All rights reserved. 
 * 版权所有 (c) 2025 AzSuawa 保留所有权利.
 *
 * Licensed under MPL 2.0. See: http://mozilla.org/MPL/2.0/
 * 基于Mozilla公共许可证2.0版开源。许可证: http://mozilla.org/MPL/2.0/
 *
 * Project/项目: https://github.com/AzSuawa/AzSuPlugin
 */

package top.azsu;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

public class AzSuPlugin extends JavaPlugin implements PluginMessageListener, TabExecutor {

    private FileConfiguration config;
    private FileConfiguration licenseConfig;
    private FileConfiguration localeConfig;
    private final int CURRENT_CONFIG_VERSION = 1;
    
    // 通信通道
    private static final String AZSU_CHANNEL = "azsu:main";

    @Override
    public void onEnable() {
        // 注册BungeeCord通道
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        // 注册自定义AzSu通道（发送和接收）
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, AZSU_CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, AZSU_CHANNEL, this);
        
        getLogger().info("已注册消息通道: " + AZSU_CHANNEL);
        
        // 设置命令执行器
        getCommand("azsu").setExecutor(this);
        getCommand("azsu").setTabCompleter(this);
        getCommand("xcmd").setExecutor(this);
        getCommand("xcmd").setTabCompleter(this);
        
        // 加载配置
        if (!loadConfig()) {
            getLogger().severe("配置加载失败，插件已禁用！");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // 显示著作权信息
        displayLicenseInfo();
        
        getLogger().info("插件已启用！");
    }

    @Override
    public void onDisable() {
        getLogger().info("插件已禁用！");
    }

    /**
     * 显示著作权信息
     */
    private void displayLicenseInfo() {
        String locale = config.getString("locale", "zh_cn");
        java.util.List<String> infoLines = licenseConfig.getStringList(locale + ".info");
        if (infoLines.isEmpty()) {
            infoLines = licenseConfig.getStringList(licenseConfig.getString("default", "zh_cn") + ".info");
        }
        
        for (String line : infoLines) {
            String formattedLine = line.replace("{version}", getDescription().getVersion());
            getLogger().info(formattedLine.replace("§", "&")); // 控制台使用&颜色代码
        }
    }

    /**
     * 发送著作权信息给玩家
     */
    private void sendLicenseInfo(CommandSender sender) {
        // 权限检查 - 所有人都可以使用info命令
        if (!sender.hasPermission("azsu.info")) {
            sender.sendMessage(getMessage("no-permission"));
            return;
        }

        String locale = config.getString("locale", "zh_cn");
        java.util.List<String> infoLines = licenseConfig.getStringList(locale + ".info");
        if (infoLines.isEmpty()) {
            infoLines = licenseConfig.getStringList(licenseConfig.getString("default", "zh_cn") + ".info");
        }
        
        for (String line : infoLines) {
            String formattedLine = line.replace("{version}", getDescription().getVersion());
            sender.sendMessage(formattedLine); // 直接发送传统颜色代码
        }
    }

    /**
     * 接收来自Velocity的插件消息
     */
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!AZSU_CHANNEL.equals(channel)) {
            return;
        }
        
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            
            // 解析消息格式
            String command = in.readUTF();
            String executorName = in.readUTF();
            String executorUUID = in.readUTF();
            boolean executeAsConsole = in.readBoolean();
            
            getLogger().info("接收来自Velocity的命令: " + command + " (执行者: " + executorName + ", 控制台: " + executeAsConsole + ")");
            
            // 执行命令
            executeReceivedCommand(command, executorName, executorUUID, executeAsConsole);
            
        } catch (IOException e) {
            getLogger().warning("处理接收的插件消息失败: " + e.getMessage());
        }
    }

    /**
     * 执行接收到的命令
     */
    private void executeReceivedCommand(String command, String executorName, String executorUUID, boolean executeAsConsole) {
        try {
            CommandSender executor;
            
            if (executeAsConsole) {
                // 以控制台身份执行
                executor = Bukkit.getConsoleSender();
                getLogger().info("以控制台身份执行命令: " + command);
            } else {
                // 对于玩家命令，尝试查找玩家
                Player player = Bukkit.getPlayer(executorUUID);
                if (player != null && player.isOnline()) {
                    executor = player;
                    getLogger().info("以玩家身份执行命令: " + command + " (玩家: " + executorName + ")");
                } else {
                    // 如果玩家不在线，回退到控制台执行
                    executor = Bukkit.getConsoleSender();
                    getLogger().warning("玩家 " + executorName + " 不在线，以控制台身份执行命令: " + command);
                }
            }
            
            // 执行命令
            boolean success = Bukkit.dispatchCommand(executor, command);
            
            if (success) {
                getLogger().info("命令执行成功: " + command);
            } else {
                getLogger().warning("命令执行失败: " + command);
            }
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "执行接收的命令失败: " + command, e);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "azsu":
                return handleAzSuCommand(sender, args);
            case "xcmd":
                return handleXcmdCommand(sender, args);
            case "testreceive":
                return handleTestReceiveCommand(sender);
            default:
                return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        
        switch (cmd.getName().toLowerCase()) {
            case "azsu":
                if (args.length == 1) {
                    completions.add("help"); // 添加 help 补全
                    if (sender.hasPermission("azsu.admin")) {
                        completions.add("reload");
                    }
                    if (sender.hasPermission("azsu.info")) {
                        completions.add("info");
                    }
                }
                break;
                
            case "xcmd":
                if (args.length == 1) {
                    // 服务器名称补全
                    completions.addAll(getAllowedServers());
                } else if (args.length == 2) {
                    // 命令补全提示
                    completions.add("say");
                    completions.add("gamemode");
                    completions.add("tp");
                    completions.add("give");
                }
                break;
        }
        
        // 过滤匹配的补全项
        if (args.length > 0) {
            String input = args[args.length - 1].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        }
        
        return completions;
    }

    /**
     * 处理 /azsu 命令
     */
    private boolean handleAzSuCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendAzSuHelp(sender);
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("azsu.admin")) {
                sender.sendMessage(getMessage("no-permission"));
                return true;
            }

            if (loadConfig()) {
                sender.sendMessage(getMessage("reload-success"));
            } else {
                sender.sendMessage(getMessage("reload-fail"));
            }
            return true;
        }

        if ("info".equalsIgnoreCase(args[0])) {
            sendLicenseInfo(sender);
            return true;
        }

        // 如果输入了未知子命令，显示帮助
        sender.sendMessage("§c未知子命令: " + args[0]);
        sendAzSuHelp(sender);
        return true;
    }

    /**
     * 发送 /azsu 命令的帮助信息
     */
    private void sendAzSuHelp(CommandSender sender) {
        sender.sendMessage("§6AzSuPlugin v" + getDescription().getVersion());
        sender.sendMessage("§e/azsu help §7- 显示此帮助信息");
        
        if (sender.hasPermission("azsu.admin")) {
            sender.sendMessage("§e/azsu reload §7- 重载插件配置");
        }
        
        if (sender.hasPermission("azsu.info")) {
            sender.sendMessage("§e/azsu info §7- 显示插件信息");
        }
        
        if (sender.hasPermission("azsu.xcmd.proxy")) {
            sender.sendMessage("§e/xcmd <服务器> <命令> §7- 跨服执行命令");
        }
         
    }

    /**
     * 处理 /xcmd 命令
     */
    private boolean handleXcmdCommand(CommandSender sender, String[] args) {
        // 基础权限检查 - 至少需要proxy权限
        if (!sender.hasPermission("azsu.xcmd.proxy")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        // 功能开关检查
        if (!isXcmdEnabled()) {
            sender.sendMessage(getMessage("xcmd-disabled"));
            return true;
        }

        // 参数检查
        if (args.length < 2) {
            sender.sendMessage("§c用法: /xcmd <服务器> <命令>");
            sender.sendMessage("§6可用服务器: " + String.join(", ", getAllowedServers()));
            return true;
        }

        String targetServer = args[0];
        String command = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        // 服务器检查
        if (!isServerAllowed(targetServer)) {
            sender.sendMessage(getMessage("server-not-allowed").replace("{server}", targetServer));
            return true;
        }

        // 命令检查
        if (!isCommandAllowed(command)) {
            sender.sendMessage(getMessage("command-not-allowed").replace("{command}", command));
            return true;
        }

        // 权限检查
        if (!checkTargetServerPermission(sender, targetServer, command)) {
            return true;
        }

        // 执行命令转发
        if (forwardCommand(sender, targetServer, command)) {
            sender.sendMessage(getMessage("command-sent").replace("{server}", targetServer));
        } else {
            sender.sendMessage(getMessage("command-failed"));
        }

        return true;
    }

    /**
     * 检查目标服务器权限
     */
    private boolean checkTargetServerPermission(CommandSender sender, String targetServer, String command) {
        // 检查是否需要控制台权限
        boolean requiresConsole = requiresConsolePermission(targetServer, command);
        
        if (requiresConsole && !sender.hasPermission("azsu.xcmd.console")) {
            sender.sendMessage(getMessage("no-console-permission"));
            return false;
        }
        
        if ("all".equalsIgnoreCase(targetServer)) {
            if (!sender.hasPermission("azsu.xcmd.all")) {
                sender.sendMessage(getMessage("no-all-permission"));
                return false;
            }
        } else if ("proxy".equalsIgnoreCase(targetServer) || "velocity".equalsIgnoreCase(targetServer)) {
            // proxy命令不需要额外权限，基础azsu.xcmd.proxy已足够
        } else {
            // 子服转子服命令
            if (!requiresConsole && !sender.hasPermission("azsu.xcmd.server")) {
                sender.sendMessage(getMessage("no-server-permission"));
                return false;
            }
        }
        return true;
    }

    /**
     * 检查是否需要控制台权限
     */
    private boolean requiresConsolePermission(String targetServer, String command) {
        if ("all".equalsIgnoreCase(targetServer)) {
            return true; // all命令总是需要控制台权限
        }
        
        if ("proxy".equalsIgnoreCase(targetServer) || "velocity".equalsIgnoreCase(targetServer)) {
            return isConsoleCommand(command); // proxy命令根据命令类型决定
        }
        
        // 子服转子服：根据配置决定
        String serverToServerMode = config.getString("xcmd.server-to-server-mode", "console");
        return !"player".equalsIgnoreCase(serverToServerMode);
    }

    /**
     * 处理测试接收命令
     */
    private boolean handleTestReceiveCommand(CommandSender sender) {
        sender.sendMessage("§6AzSu插件状态检查 v" + getDescription().getVersion());
        sender.sendMessage("§7- 配置版本: " + CURRENT_CONFIG_VERSION);
        sender.sendMessage("§7- 注册的通道: " + AZSU_CHANNEL);
        sender.sendMessage("§7- 消息监听器: " + (this instanceof PluginMessageListener ? "已注册" : "未注册"));
        sender.sendMessage("§a权限检查:");
        sender.sendMessage("§7- azsu.xcmd.proxy: " + (sender.hasPermission("azsu.xcmd.proxy") ? "§a有" : "§c无"));
        sender.sendMessage("§7- azsu.xcmd.server: " + (sender.hasPermission("azsu.xcmd.server") ? "§a有" : "§c无"));
        sender.sendMessage("§7- azsu.xcmd.console: " + (sender.hasPermission("azsu.xcmd.console") ? "§a有" : "§c无"));
        sender.sendMessage("§7- azsu.xcmd.all: " + (sender.hasPermission("azsu.xcmd.all") ? "§a有" : "§c无"));
        return true;
    }

    /**
     * 加载配置文件
     */
    private boolean loadConfig() {
        try {
            // 检查配置文件版本
            File configFile = new File(getDataFolder(), "config.yml");
            if (configFile.exists()) {
                YamlConfiguration existingConfig = YamlConfiguration.loadConfiguration(configFile);
                int existingVersion = existingConfig.getInt("config-version", -1);
                
                if (existingVersion != CURRENT_CONFIG_VERSION) {
                    // 备份旧配置
                    File backupDir = new File(getDataFolder(), "backup");
                    if (!backupDir.exists()) {
                        backupDir.mkdirs();
                    }
                    File backupFile = new File(backupDir, "config-v" + existingVersion + "-" + System.currentTimeMillis() + ".yml");
                    Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    getLogger().info("已备份旧配置文件: " + backupFile.getName());
                    
                    // 删除旧配置，让saveDefaultConfig生成新配置
                    configFile.delete();
                }
            }

            // 保存默认配置（如果不存在）
            saveDefaultConfig();
            
            // 重新加载配置
            reloadConfig();
            config = getConfig();

            // 强制覆盖LICENSE.yml（每次重载都使用resources中的版本）
            File licenseFile = new File(getDataFolder(), "LICENSE.yml");
            if (licenseFile.exists()) {
                licenseFile.delete(); // 删除旧文件
            }
            saveResource("LICENSE.yml", false); // 从resources复制
            licenseConfig = YamlConfiguration.loadConfiguration(licenseFile);

            // 加载语言文件
            loadLocaleConfig();

            getLogger().info("配置文件加载成功！版本: " + CURRENT_CONFIG_VERSION);
            return true;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "加载配置文件失败", e);
            return false;
        }
    }

    /**
     * 加载语言配置
     */
    private void loadLocaleConfig() {
        try {
            String locale = config.getString("locale", "zh_cn");
            File localeFile = new File(getDataFolder(), "locale/" + locale + ".yml");
            
            if (!localeFile.exists()) {
                // 如果指定语言文件不存在，尝试从jar中提取
                saveResource("locale/" + locale + ".yml", false);
            }
            
            if (localeFile.exists()) {
                localeConfig = YamlConfiguration.loadConfiguration(localeFile);
                
                // 检查语言文件版本
                int localeVersion = localeConfig.getInt("config-version", -1);
                if (localeVersion < 1) {
                    getLogger().warning("语言文件版本过时: " + localeFile.getPath());
                }
                
                getLogger().info("已加载语言文件: " + locale);
            } else {
                getLogger().warning("语言文件不存在: " + localeFile.getPath() + "，使用默认消息");
                localeConfig = new YamlConfiguration();
            }
        } catch (Exception e) {
            getLogger().warning("加载语言文件失败: " + e.getMessage());
            localeConfig = new YamlConfiguration();
        }
    }

    /**
     * 获取消息
     */
    private String getMessage(String key) {
        String message = localeConfig.getString(key);
        if (message == null) {
            // 如果当前语言中没有该消息，使用硬编码的默认消息
            switch (key) {
                case "no-permission": return "§c你没有权限使用此命令！";
                case "reload-success": return "§a配置重载成功！";
                case "reload-fail": return "§c配置重载失败，请检查控制台日志";
                default: return "§c消息配置缺失: " + key;
            }
        }
        return message;
    }

    /**
     * 检查跨服命令功能是否启用
     */
    private boolean isXcmdEnabled() {
        return config.getBoolean("features.xcmd-forward", true);
    }

    /**
     * 检查服务器是否允许
     */
    private boolean isServerAllowed(String server) {
        return config.getStringList("xcmd.allowed-servers").contains(server);
    }

    /**
     * 获取允许的服务器列表
     */
    private java.util.List<String> getAllowedServers() {
        return config.getStringList("xcmd.allowed-servers");
    }

    /**
     * 检查命令是否允许
     */
    private boolean isCommandAllowed(String command) {
        String baseCommand = command.split(" ")[0].toLowerCase();
        String filterMode = config.getString("xcmd.filter-mode", "blacklist");
        java.util.List<String> commandList = config.getStringList("xcmd.commands");
        
        if ("whitelist".equalsIgnoreCase(filterMode)) {
            // 白名单模式：只有在列表中的命令才允许
            return commandList.contains(baseCommand);
        } else {
            // 黑名单模式：只有在列表中的命令才禁止
            return !commandList.contains(baseCommand);
        }
    }

    /**
     * 检查是否是控制台命令
     */
    private boolean isConsoleCommand(String command) {
        String baseCommand = command.split(" ")[0].toLowerCase();
        return config.getStringList("xcmd.console-commands").contains(baseCommand);
    }

    /**
     * 转发命令到目标服务器
     */
    private boolean forwardCommand(CommandSender sender, String targetServer, String command) {
        try {
            // 确定执行者身份和UUID
            String executorName;
            String executorUUID;
            boolean isConsole;
            
            if (sender instanceof Player) {
                Player player = (Player) sender;
                executorName = player.getName();
                executorUUID = player.getUniqueId().toString();
                
                // 判断执行模式
                if ("proxy".equalsIgnoreCase(targetServer) || "velocity".equalsIgnoreCase(targetServer)) {
                    // 代理端命令：根据命令类型决定模式
                    isConsole = isConsoleCommand(command);
                } else if ("all".equalsIgnoreCase(targetServer)) {
                    // all命令：使用控制台身份执行
                    isConsole = true;
                } else {
                    // 子服转子服：根据配置决定模式
                    String serverToServerMode = config.getString("xcmd.server-to-server-mode", "console");
                    if ("player".equalsIgnoreCase(serverToServerMode)) {
                        // player模式：使用玩家身份
                        isConsole = false;
                    } else {
                        // console模式：使用控制台身份
                        isConsole = true;
                    }
                }
            } else {
                // 控制台发送：总是以控制台身份执行
                executorName = "CONSOLE";
                executorUUID = "CONSOLE";
                isConsole = true;
            }
            
            getLogger().info("转发命令: " + command + " -> " + targetServer + 
                " (执行者: " + executorName + ", 模式: " + (isConsole ? "控制台" : "玩家") + ")");
            
            // 发送插件消息
            return sendPluginMessage(sender, targetServer, command, executorName, executorUUID, isConsole);
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "转发命令失败", e);
            return false;
        }
    }

    /**
     * 发送插件消息到Velocity
     */
    private boolean sendPluginMessage(CommandSender sender, String targetServer, String command, 
                                    String executorName, String executorUUID, boolean executeAsConsole) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            
            // 消息格式：
            // 1. 目标服务器
            // 2. 要执行的命令
            // 3. 执行者名称
            // 4. 执行者UUID
            // 5. 是否以控制台身份执行
            out.writeUTF(targetServer);
            out.writeUTF(command);
            out.writeUTF(executorName);
            out.writeUTF(executorUUID);
            out.writeBoolean(executeAsConsole);
            
            // 发送到自定义通道
            if (sender instanceof Player) {
                // 玩家执行：通过该玩家发送
                Player player = (Player) sender;
                player.sendPluginMessage(this, AZSU_CHANNEL, bytes.toByteArray());
            } else {
                // 控制台执行：通过任意在线玩家发送
                Player onlinePlayer = getRandomOnlinePlayer();
                if (onlinePlayer != null) {
                    onlinePlayer.sendPluginMessage(this, AZSU_CHANNEL, bytes.toByteArray());
                } else {
                    getLogger().warning("没有在线玩家，无法发送插件消息");
                    return false;
                }
            }
            
            getLogger().info("插件消息发送成功: " + command + " -> " + targetServer);
            return true;
            
        } catch (IOException e) {
            getLogger().warning("发送插件消息失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取随机在线玩家（仅用于控制台发送消息）
     */
    private Player getRandomOnlinePlayer() {
        return Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
    }
}