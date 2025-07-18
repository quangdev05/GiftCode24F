package com.quangdev.giftCode24F;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Statistic;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.Sound;
import org.bstats.bukkit.Metrics;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.*;

import org.json.JSONObject;

public class GiftCode24F extends JavaPlugin implements Listener {

    private FileConfiguration giftCodesConfig;
    private File giftCodesFile;
    private Map<String, GiftCode> giftCodes = new LinkedHashMap<>();
    private File dataplayerFile;
    private FileConfiguration dataplayerConfig;
    private String currentVersion = getDescription().getVersion();
    private String latestVersion = null;

    private static final int ITEMS_PER_PAGE = 45; // 5 hàng
    private static final int PREV_BUTTON_SLOT = 45; // Ô đầu hàng cuối
    private static final int NEXT_BUTTON_SLOT = 53; // Ô cuối hàng cuối
    private static final int PAGE_INFO_SLOT = 49;   // Giữa hàng cuối

    private ItemStack createNavigationButton(String displayName, Material material, String lore) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();

        meta.setDisplayName(ChatColor.GREEN + displayName);

        List<String> lores = new ArrayList<>();
        lores.add(ChatColor.GRAY + lore);
        meta.setLore(lores);

        button.setItemMeta(meta);
        return button;
    }

    private ItemStack createGiftCodeItem(String code, GiftCode giftCode) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + code);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Thông tin mã quà tặng:");
        lore.add(ChatColor.WHITE + "• " + ChatColor.GRAY + "Số lượng: " + ChatColor.YELLOW + giftCode.getMaxUses());

        int usedCount = calculateUsedCount(code);
        lore.add(ChatColor.WHITE + "• " + ChatColor.GRAY + "Đã sử dụng: " + ChatColor.YELLOW + usedCount);

        String expiry = giftCode.getExpiry().isEmpty() ?
                ChatColor.GREEN + "Vô thời hạn" :
                ChatColor.YELLOW + giftCode.getExpiry();
        lore.add(ChatColor.WHITE + "• " + ChatColor.GRAY + "Hạn dùng: " + expiry);

        String status = giftCode.isEnabled() ?
                ChatColor.GREEN + "Đang kích hoạt" :
                ChatColor.RED + "Đã vô hiệu";
        lore.add(ChatColor.WHITE + "• " + ChatColor.GRAY + "Trạng thái: " + status);

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPageInfoItem(int currentPage, int totalPages) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + "Trang " + (currentPage + 1) + "/" + totalPages);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Sử dụng các nút để chuyển trang");
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onEnable() {
        createConfigFiles();
        loadGiftCodes();
        loadDataplayerConfig();
        updateConfig();
        getCommand("giftcode").setExecutor(this);
        getCommand("code").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        sendFancyMessage();
        checkForUpdates();

        int pluginId = 24198;
        Metrics metrics = new Metrics(this, pluginId);
    }

    private void createConfigFiles() {
        createFile("config.yml");
        createFile("giftcode.yml");
        createFile("dataplayer.yml");
    }

    private void createFile(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            saveResource(fileName, false);
        }
        if (fileName.equals("config.yml")) {
            getConfig().options().copyDefaults(true);
            saveConfig();
        } else if (fileName.equals("giftcode.yml")) {
            giftCodesFile = file;
            giftCodesConfig = YamlConfiguration.loadConfiguration(giftCodesFile);
        } else if (fileName.equals("dataplayer.yml")) {
            dataplayerFile = file;
            dataplayerConfig = YamlConfiguration.loadConfiguration(dataplayerFile);
        }
    }

    private void loadDataplayerConfig() {
        dataplayerConfig = YamlConfiguration.loadConfiguration(dataplayerFile);
    }

    private void sendFancyMessage() {
        getLogger().info(" ");
        getLogger().info(" ██████╗ ██╗███████╗████████╗ ██████╗ ██████╗ ██████╗ ███████╗██████╗ ██╗  ██╗");
        getLogger().info("██╔════╝ ██║██╔════╝╚══██╔══╝██╔════╝██╔═══██╗██╔══██╗██╔════╝╚════██╗██║  ██║");
        getLogger().info("██║  ███╗██║█████╗     ██║   ██║     ██║   ██║██║  ██║█████╗   █████╔╝███████║");
        getLogger().info("██║   ██║██║██╔══╝     ██║   ██║     ██║   ██║██║  ██║██╔══╝  ██╔═══╝ ╚════██║");
        getLogger().info("╚██████╔╝██║██║        ██║   ╚██████╗╚██████╔╝██████╔╝███████╗███████╗     ██║");
        getLogger().info(" ╚═════╝ ╚═╝╚═╝        ╚═╝    ╚═════╝ ╚═════╝ ╚═════╝ ╚══════╝╚══════╝     ╚═╝");
        getLogger().info(" ");
        getLogger().info("  Tác giả: QuangDev05");
        getLogger().info("  Phiên bản hiện tại: v" + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        saveGiftCodes();
    }

    private void updateConfig() {
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    private void loadGiftCodes() {
        giftCodes.clear();
        for (String key : new ArrayList<>(giftCodesConfig.getKeys(false))) {
            Object messageObj = giftCodesConfig.get(key + ".message");
            List<String> commands = giftCodesConfig.getStringList(key + ".commands");
            int maxUses = giftCodesConfig.getInt(key + ".max-uses");
            String expiry = giftCodesConfig.getString(key + ".expiry");
            boolean enabled = giftCodesConfig.getBoolean(key + ".enabled");
            int playerMaxUses = giftCodesConfig.getInt(key + ".player-max-uses");
            int maxUsesPerIP = giftCodesConfig.getInt(key + ".player-max-uses-perip");
            int requiredPlaytime = giftCodesConfig.getInt(key + ".required-playtime");
            GiftCode giftCode = new GiftCode(commands, messageObj, maxUses, expiry, enabled, playerMaxUses, maxUsesPerIP, requiredPlaytime);
            if (giftCodesConfig.isConfigurationSection(key + ".ip-usage-counts")) {
                ConfigurationSection section = giftCodesConfig.getConfigurationSection(key + ".ip-usage-counts");
                for (String ip : section.getKeys(false)) {
                    int usage = section.getInt(ip);
                    giftCode.ipUsageCounts.put(ip, usage);
                }
            }
            giftCodes.put(key, giftCode);
        }
    }

    private void saveGiftCodes() {
        for (Map.Entry<String, GiftCode> entry : giftCodes.entrySet()) {
            GiftCode giftCode = entry.getValue();
            giftCodesConfig.set(entry.getKey() + ".commands", giftCode.getCommands());
            giftCodesConfig.set(entry.getKey() + ".message", giftCode.getMessages());
            giftCodesConfig.set(entry.getKey() + ".max-uses", giftCode.getMaxUses());
            giftCodesConfig.set(entry.getKey() + ".expiry", giftCode.getExpiry());
            giftCodesConfig.set(entry.getKey() + ".enabled", giftCode.isEnabled());
            giftCodesConfig.set(entry.getKey() + ".player-max-uses", giftCode.getPlayerMaxUses());
            giftCodesConfig.set(entry.getKey() + ".player-max-uses-perip", giftCode.getMaxUsesPerIP());
            giftCodesConfig.set(entry.getKey() + ".required-playtime", giftCode.getRequiredPlaytime());
            String ipUsageCountsPath = entry.getKey() + ".ip-usage-counts";
            giftCodesConfig.set(ipUsageCountsPath, null);
            for (Map.Entry<String, Integer> ipEntry : giftCode.ipUsageCounts.entrySet()) {
                giftCodesConfig.set(ipUsageCountsPath + "." + ipEntry.getKey(), ipEntry.getValue());
            }
        }
        try {
            giftCodesConfig.save(giftCodesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createGiftCode(String code, List<String> commands, List<String> message, int maxUses, String expiry,
                                boolean enabled, int playerMaxUses, int maxUsesPerIP, int requiredPlaytime) {
        if (giftCodes.containsKey(code)) {
            getLogger().warning("Mã quà tặng \"" + code + "\" đã tồn tại. Vui lòng tạo mã khác.");
            return;
        }

        GiftCode giftCode = new GiftCode(commands, message, maxUses, expiry, enabled, playerMaxUses, maxUsesPerIP, requiredPlaytime);
        giftCodes.put(code, giftCode);
        giftCodesConfig.set(code + ".commands", commands);
        giftCodesConfig.set(code + ".message", message);
        giftCodesConfig.set(code + ".max-uses", maxUses);
        giftCodesConfig.set(code + ".expiry", expiry);
        giftCodesConfig.set(code + ".enabled", enabled);
        giftCodesConfig.set(code + ".player-max-uses", playerMaxUses);
        giftCodesConfig.set(code + ".player-max-uses-perip", maxUsesPerIP);
        giftCodesConfig.set(code + ".required-playtime", requiredPlaytime);
        saveGiftCodes();
        getLogger().info("Mã quà tặng \"" + code + "\" đã được tạo thành công!");
    }

    private void deleteGiftCode(String code) {
        giftCodes.remove(code);
        giftCodesConfig.set(code, null);
        saveGiftCodes();
    }

    private List<String> listGiftCodes() {
        return new ArrayList<>(giftCodes.keySet());
    }

    private void assignGiftCodeToPlayer(CommandSender sender, String code, Player player) {
        if (!giftCodes.containsKey(code)) {
            sender.sendMessage(ChatColor.RED + "Mã quà tặng " + ChatColor.YELLOW + "\"" + code + "\"" + ChatColor.RED + " không tồn tại!");
            return;
        }
        GiftCode giftCode = giftCodes.get(code);
        List<String> assignedCodes = dataplayerConfig.getStringList("players." + player.getUniqueId() + ".assignedCodes");
        if (assignedCodes == null) assignedCodes = new ArrayList<>();

        assignedCodes.add(code);
        dataplayerConfig.set("players." + player.getUniqueId() + ".assignedCodes", assignedCodes);
        saveDataplayerConfig();

        Bukkit.getGlobalRegionScheduler().run(this, task -> {
            for (String cmd : giftCode.getCommands()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
            }

            for (String msg : giftCode.getMessages()) {
                player.sendMessage(ChatColor.GREEN + msg);
            }

            giftCode.setMaxUses(giftCode.getMaxUses() - 1);
            addPlayerUsedCode(player, code);
            saveGiftCodes();
        });

        sender.sendMessage(ChatColor.GREEN + "Đã gán mã quà tặng \"" +
                ChatColor.YELLOW + code + ChatColor.GREEN + "\" cho người chơi \"" +
                ChatColor.AQUA + player.getName() + ChatColor.GREEN + "\".");
    }

    private void createRandomGiftCodes(String baseName, int amount) {
        for (int i = 0; i < amount; i++) {
            String code = baseName + "_" + ThreadLocalRandom.current().nextInt(1_000_000);
            createGiftCode(code, Collections.singletonList("give %player% diamond 1"),
                    Collections.singletonList("Bạn đã nhận được một viên kim cương!"), 99, "2029-12-31T23:59:59", true, 1, 1, 8);
        }
    }

    private boolean checkPlayerHasUsedCode(Player player, String code) {
        List<String> usedCodes = dataplayerConfig.getStringList("players." + player.getUniqueId() + ".usedCodes");
        int playerMaxUses = getPlayerMaxUsesForCode(code);
        if (playerMaxUses == -1) {
            return false;
        }
        return Collections.frequency(usedCodes, code) >= playerMaxUses;
    }

    private void addPlayerUsedCode(Player player, String code) {
        List<String> usedCodes = dataplayerConfig.getStringList("players." + player.getUniqueId() + ".usedCodes");
        String playerIP = player.getAddress().getAddress().getHostAddress();
        if (usedCodes == null) {
            usedCodes = new ArrayList<>();
        }
        usedCodes.add(code);
        dataplayerConfig.set("players." + player.getUniqueId() + ".ip", playerIP);

        dataplayerConfig.set("players." + player.getUniqueId() + ".usedCodes", usedCodes);
        saveDataplayerConfig();
    }

    private void saveDataplayerConfig() {
        try {
            dataplayerConfig.save(dataplayerFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openGiftCodeList(Player player, int page) {
        List<String> codeList = new ArrayList<>(giftCodes.keySet());
        int totalCodes = codeList.size();
        int totalPages = (int) Math.ceil((double) totalCodes / ITEMS_PER_PAGE);

        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(
                null,
                54,
                ChatColor.GOLD + "Danh sách mã quà tặng"
        );

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, totalCodes);

        for (int i = start; i < end; i++) {
            String code = codeList.get(i);
            GiftCode giftCode = giftCodes.get(code);
            ItemStack item = createGiftCodeItem(code, giftCode);
            inv.addItem(item);
        }

        if (totalPages > 1) {
            if (page > 0) {
                ItemStack prevButton = createNavigationButton(
                        "« Trang Trước",
                        Material.ARROW,
                        "Nhấn để về trang " + page
                );
                inv.setItem(PREV_BUTTON_SLOT, prevButton);
            }

            ItemStack pageInfo = createPageInfoItem(page, totalPages);
            inv.setItem(PAGE_INFO_SLOT, pageInfo);

            if (page < totalPages - 1) {
                ItemStack nextButton = createNavigationButton(
                        "Trang Sau »",
                        Material.ARROW,
                        "Nhấn đến trang " + (page + 2)
                );
                inv.setItem(NEXT_BUTTON_SLOT, nextButton);
            }
        }

        player.openInventory(inv);
    }

    private int calculateUsedCount(String code) {
        int count = 0;
        if (dataplayerConfig.getConfigurationSection("players") != null) {
            for (String uuid : dataplayerConfig.getConfigurationSection("players").getKeys(false)) {
                List<String> usedCodes = dataplayerConfig.getStringList("players." + uuid + ".usedCodes");
                count += Collections.frequency(usedCodes, code);
            }
        }
        return count;
    }

    private void checkForUpdates() {
        boolean checkUpdate = getConfig().getBoolean("check-update", true);
        if (!checkUpdate) return;

        String url = "https://api.github.com/repos/quangdev05/GiftCode24F/releases/latest";

        Bukkit.getAsyncScheduler().runNow(this, scheduledTask -> {
            HttpURLConnection connection = null;
            try {
                URL updateUrl = new URL(url);
                connection = (HttpURLConnection) updateUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (InputStream inputStream = connection.getInputStream();
                         Scanner scanner = new Scanner(inputStream)) {
                        String response = scanner.useDelimiter("\\A").next();
                        JSONObject jsonResponse = new JSONObject(response);
                        String fetchedVersion = jsonResponse.getString("tag_name");
                        latestVersion = fetchedVersion;

                        Bukkit.getGlobalRegionScheduler().run(this, task -> {
                            if (!latestVersion.equals(currentVersion)) {
                                getLogger().info("Plugin đã có phiên bản mới: v" + latestVersion);
                            } else {
                                getLogger().info("Plugin đang ở phiên bản mới nhất v" + currentVersion);
                            }
                        });
                    }
                } else {
                    Bukkit.getGlobalRegionScheduler().run(this, task -> {
                        getLogger().warning("Không thể kết nối để kiểm tra bản cập nhật. Mã lỗi: " + responseCode);
                    });
                }
            } catch (IOException e) {
                Bukkit.getGlobalRegionScheduler().run(this, task -> {
                    getLogger().warning("Không thể kết nối để kiểm tra bản cập nhật: " + e.getMessage());
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private int getPlayerMaxUsesForCode(String code) {
        if (giftCodes.containsKey(code)) {
            return giftCodes.get(code).getPlayerMaxUses();
        }
        return 1;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("giftcode") || label.equalsIgnoreCase("gc")) {
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                sender.sendMessage(ChatColor.GOLD + "Danh sách lệnh");
                sender.sendMessage(ChatColor.GREEN + " /gc create <code> - Tạo mã quà tặng");
                sender.sendMessage(ChatColor.GREEN + " /gc create <name> random - Tạo ngẫu nhiên 10 mã quà tặng");
                sender.sendMessage(ChatColor.GREEN + " /gc del <code> - Xóa mã quà tặng");
                sender.sendMessage(ChatColor.GREEN + " /gc reload - Tải lại plugin");
                sender.sendMessage(ChatColor.GREEN + " /gc enable <code> - Kích hoạt mã quà tặng");
                sender.sendMessage(ChatColor.GREEN + " /gc disable <code> - Vô hiệu hóa mã quà tặng");
                sender.sendMessage(ChatColor.GREEN + " /gc list - Danh sách mã quà tặng");
                sender.sendMessage(ChatColor.GREEN + " /gc assign <code> <player> - Gán mã quà tặng cho người chơi ");
                sender.sendMessage("");
                sender.sendMessage(ChatColor.GOLD + "Thông Tin:");
                sender.sendMessage(ChatColor.YELLOW + " Tác giả: QuangDev05");
                sender.sendMessage(ChatColor.YELLOW + " Phiên bản hiện tại: v" + currentVersion);
                sender.sendMessage(ChatColor.YELLOW + " Phiên bản mới nhất: " +
                        (latestVersion != null ? "v" + latestVersion : "Không xác định"));
                return true;
            }

            if (!sender.hasPermission("giftcode.admin")) {
                sender.sendMessage(ChatColor.RED + "Bạn không có quyền sử dụng lệnh này.");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "create":
                    if (args.length == 2) {
                        if (giftCodes.containsKey(args[1])) {
                            sender.sendMessage(
                                    ChatColor.RED + "Mã quà tặng \"" + ChatColor.YELLOW + args[1] + ChatColor.RED + "\" đã tồn tại. Vui lòng tạo mã khác.");
                        } else {
                            createGiftCode(args[1], Collections.singletonList("give %player% diamond 1"),
                                    Collections.singletonList("Bạn đã nhận 1 viên kim cương!"), 99, "2029-12-31T23:59:59", true, 1, 1, 8);
                            sender.sendMessage(ChatColor.GREEN + "Mã quà tặng \"" + ChatColor.YELLOW + args[1] + ChatColor.GREEN + "\" tạo thành công!");
                        }
                    } else if (args.length == 3 && args[2].equalsIgnoreCase("random")) {
                        createRandomGiftCodes(args[1], 10);
                        sender.sendMessage(ChatColor.GREEN + "Đã tạo 10 mã quà tặng ngẫu nhiên với tên cơ sở \"" + ChatColor.YELLOW + args[1] + "\"");
                    } else {
                        sender.sendMessage(
                                ChatColor.RED + "Sử dụng: /giftcode create <code> hoặc /giftcode create <name> random");
                    }
                    break;
                case "del":
                    if (args.length == 2) {
                        deleteGiftCode(args[1]);
                        sender.sendMessage(ChatColor.GREEN + "Mã quà tặng \"" + ChatColor.YELLOW + args[1] + ChatColor.GREEN + "\" đã bị xóa!");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Sử dụng: /giftcode del <code>");
                    }
                    break;
                case "reload":
                    reloadConfig();
                    createConfigFiles();
                    loadGiftCodes();
                    loadDataplayerConfig();
                    sender.sendMessage(ChatColor.GREEN + "Đã tải lại tất cả các file cấu hình!");
                    break;
                case "enable":
                    if (args.length == 2) {
                        GiftCode codeToEnable = giftCodes.get(args[1]);
                        if (codeToEnable != null) {
                            codeToEnable.setEnabled(true);
                            saveGiftCodes();
                            sender.sendMessage(ChatColor.GREEN + "Mã quà tặng \"" + ChatColor.YELLOW + args[1] + ChatColor.GREEN + "\" Đã kích hoạt!");
                        } else {
                            sender.sendMessage(ChatColor.RED + "Mã quà tặng không tồn tại!");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Sử dụng: /giftcode enable <code>");
                    }
                    break;
                case "disable":
                    if (args.length == 2) {
                        GiftCode codeToDisable = giftCodes.get(args[1]);
                        if (codeToDisable != null) {
                            codeToDisable.setEnabled(false);
                            saveGiftCodes();
                            sender.sendMessage(ChatColor.GREEN + "Mã quà tặng \"" + ChatColor.YELLOW + args[1] + ChatColor.GREEN + "\" Đã bị vô hiệu hóa!");
                        } else {
                            sender.sendMessage(ChatColor.RED + "Không tồn tài!");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Sử dụng: \"/giftcode disable <code>\"");
                    }
                    break;
                case "list":
                    if (sender instanceof Player) {
                        openGiftCodeList((Player) sender, 0);
                    } else {
                        sender.sendMessage(ChatColor.GOLD + "Danh sách mã quà tặng:");
                        for (String code : listGiftCodes()) {
                            sender.sendMessage(ChatColor.GREEN + " " + code);
                        }
                    }
                    break;
                case "assign":
                    if (args.length == 3) {
                        String code = args[1];
                        Player targetPlayer = Bukkit.getPlayer(args[2]);
                        if (targetPlayer != null) {
                            if (!giftCodes.containsKey(code)) {
                                sender.sendMessage(ChatColor.RED + "Mã quà tặng \"" + ChatColor.YELLOW + code + ChatColor.RED + "\" không tồn tại!");
                                return true;
                            }
                            assignGiftCodeToPlayer(sender, code, targetPlayer);
                        } else {
                            sender.sendMessage(ChatColor.RED + "Không tìm thấy người chơi: " + args[2]);
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Sử dụng: /giftcode assign <code> <player>");
                    }
                    break;

            }
            return true;
        } else if (label.equalsIgnoreCase("code")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (args.length == 1) {
                    String code = args[0];
                    GiftCode giftCode = giftCodes.get(code);

                    if (giftCode == null) {
                        player.sendMessage(ChatColor.RED + getConfig().getString("messages.invalid-code"));
                        return true;
                    }

                    if (!giftCode.isEnabled()) {
                        player.sendMessage(ChatColor.RED + getConfig().getString("messages.code-disabled"));
                        return true;
                    }

                    if (giftCode.getRequiredPlaytime() > 0) {
                        int playerPlaytime = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60);
                        if (playerPlaytime < giftCode.getRequiredPlaytime()) {
                            String message = getConfig().getString("messages.not-enough-playtime")
                                    .replace("{required}", String.valueOf(giftCode.getRequiredPlaytime()))
                                    .replace("{current}", String.valueOf(playerPlaytime));
                            player.sendMessage(ChatColor.RED + message);
                            return true;
                        }
                    }

                    if (!giftCode.getExpiry().isEmpty()) {
                        try {
                            Date expiryDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(giftCode.getExpiry());
                            if (new Date().after(expiryDate)) {
                                player.sendMessage(ChatColor.RED + getConfig().getString("messages.code-expired"));
                                return true;
                            }
                        } catch (ParseException e) {
                            e.printStackTrace();
                            player.sendMessage(ChatColor.RED + "Đã xảy ra lỗi khi kiểm tra thời gian hết hạn.");
                            return true;
                        }
                    }

                    if (giftCode.getMaxUses() <= 0) {
                        player.sendMessage(ChatColor.RED + getConfig().getString("messages.max-uses-reached"));
                        return true;
                    }

                    if (giftCode.getMaxUsesPerIP() > 0) {
                        String playerIP = player.getAddress().getAddress().getHostAddress();
                        List<String> usedCodesByIP = new ArrayList<>();

                        for (String uuid : dataplayerConfig.getConfigurationSection("players").getKeys(false)) {
                            String ip = dataplayerConfig.getString("players." + uuid + ".ip");
                            if (playerIP.equals(ip)) {
                                usedCodesByIP.addAll(dataplayerConfig.getStringList("players." + uuid + ".usedCodes"));
                            }
                        }

                        int ipUsageCount = Collections.frequency(usedCodesByIP, code);
                        if (ipUsageCount >= giftCode.getMaxUsesPerIP()) {
                            player.sendMessage(ChatColor.RED + getConfig().getString("messages.max-uses-perip"));
                            return true;
                        }
                    }

                    if (checkPlayerHasUsedCode(player, code)) {
                        player.sendMessage(ChatColor.RED + getConfig().getString("messages.code-already-redeemed"));
                        return true;
                    }

                    for (String cmd : giftCode.getCommands()) {
                        Bukkit.getGlobalRegionScheduler().execute(this, () ->
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()))
                        );
                    }

                    for (String msg : giftCode.getMessages()) {
                        player.sendMessage(ChatColor.GREEN + msg);
                    }

                    giftCode.setMaxUses(giftCode.getMaxUses() - 1);

                    addPlayerUsedCode(player, code);

                    saveGiftCodes();
                } else {
                    player.sendMessage(ChatColor.RED + "Sử dụng: /code <code>");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Chỉ người chơi mới có thể sử dụng lệnh này.");
            }
            return true;
        }
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (title.equals(ChatColor.GOLD + "Danh sách mã quà tặng")) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null) return;
            if (!(event.getWhoClicked() instanceof Player)) return;

            Player player = (Player) event.getWhoClicked();
            ItemStack clicked = event.getCurrentItem();

            int currentPage = 0;
            ItemStack pageInfo = event.getInventory().getItem(PAGE_INFO_SLOT);
            if (pageInfo != null && pageInfo.hasItemMeta()) {
                String displayName = pageInfo.getItemMeta().getDisplayName();
                if (displayName.contains("/")) {
                    try {
                        String[] parts = displayName.replace(ChatColor.GOLD + "Trang ", "").split("/");
                        currentPage = Integer.parseInt(parts[0].trim()) - 1;
                    } catch (NumberFormatException ignored) {}
                }
            }

            if (clicked.getType() == Material.ARROW) {
                int totalGiftCodes = giftCodes.size();
                int totalPages = (int) Math.ceil((double) totalGiftCodes / ITEMS_PER_PAGE);
                if (event.getSlot() == PREV_BUTTON_SLOT && currentPage > 0) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    openGiftCodeList(player, currentPage - 1);
                }
                else if (event.getSlot() == NEXT_BUTTON_SLOT && currentPage < totalPages - 1) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    openGiftCodeList(player, currentPage + 1);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(ChatColor.GOLD + "Danh sách mã quà tặng")) {
            event.setCancelled(true);
        }
    }

    public class GiftCode {
        public Map<String, Integer> ipUsageCounts = new HashMap<>();

        private int maxUsesPerIP;
        private List<String> commands;
        private List<String> messages;
        private int maxUses;
        private String expiry;
        private boolean enabled;
        private int playerMaxUses;
        private int requiredPlaytime;

        public GiftCode(List<String> commands, Object messageObj, int maxUses, String expiry, boolean enabled,
                        int playerMaxUses, int maxUsesPerIP, int requiredPlaytime) {
            this.commands = commands;
            this.messages = new ArrayList<>();
            if (messageObj instanceof String) {
                this.messages.add((String) messageObj);
            } else if (messageObj instanceof List) {
                this.messages.addAll((List<String>) messageObj);
            }
            this.maxUses = maxUses;
            this.expiry = expiry;
            this.enabled = enabled;
            this.playerMaxUses = playerMaxUses;
            this.maxUsesPerIP = maxUsesPerIP;
            this.ipUsageCounts = new HashMap<>();
            this.requiredPlaytime = requiredPlaytime;
        }

        public List<String> getCommands() {
            return commands;
        }

        public void setCommands(List<String> commands) {
            this.commands = commands;
        }

        public List<String> getMessages() {
            return messages;
        }

        public void setMessage(List<String> messages) {
            this.messages = messages;
        }

        public int getMaxUses() {
            return maxUses;
        }

        public void setMaxUses(int maxUses) {
            this.maxUses = maxUses;
        }

        public String getExpiry() {
            return expiry;
        }

        public void setExpiry(String expiry) {
            this.expiry = expiry;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPlayerMaxUses() {
            return playerMaxUses;
        }

        public void setPlayerMaxUses(int playerMaxUses) {
            this.playerMaxUses = playerMaxUses;
        }

        public int getMaxUsesPerIP() {
            return maxUsesPerIP;
        }

        public void setMaxUsesPerIP(int maxUsesPerIP) {
            this.maxUsesPerIP = maxUsesPerIP;
        }

        public int getRequiredPlaytime() {
            return requiredPlaytime;}
    }
}