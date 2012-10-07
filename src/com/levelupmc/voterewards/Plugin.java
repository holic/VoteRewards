package com.levelupmc.voterewards;

import java.util.logging.Level;
import java.text.MessageFormat;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;


public class Plugin extends JavaPlugin {
    // TODO: configurable Redis host
    protected static JedisPool pool;
    
    // TODO: use REST API instead of a direct connection to Redis?
    
    @Override
    public void onEnable() {
        // load config and copy plugin's default config values to it
        getConfig().options().copyDefaults(true);
        
        // create Jedis pool from configured URI
        // pool = new JedisPool(getConfig().getString("redis"));
        pool = new JedisPool(new JedisPoolConfig(),
                getConfig().getString("redis.host", "localhost"),
                getConfig().getInt("redis.port", Protocol.DEFAULT_PORT),
                getConfig().getInt("redis.timeout", Protocol.DEFAULT_TIMEOUT),
                getConfig().getString("redis.password", null),
                getConfig().getInt("redis.database", Protocol.DEFAULT_DATABASE));
        
        
        getCommand("claim").setExecutor(new CommandExecutor() {
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if(sender instanceof Player) {
                    rewardPlayer((Player) sender);
                }
                else {
                    sender.sendMessage("You must be a Player to execute this command.");
                }
                return true;
            }
        });
        
        // TODO: trigger reminder on login and at interval after login instead of global interval
        int remindEvery = getConfig().getInt("remindEvery");
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            public void run() {
                for(Player player : Bukkit.getOnlinePlayers()) {
                    remindPlayer(player);
                }
            }
        }, remindEvery, remindEvery);
        
    }
    
    @Override
    public void onDisable() {
        // nothing to do!
    }
    
    
    public void rewardPlayer(Player player) {
        Jedis redis = pool.getResource();
        try {
            // TODO: configurable redis key
            String key = "votifier:credits:" + player.getName().toLowerCase();

            if(redis.lpop(key) == null) {
                player.sendMessage(getConfig().getString("messages.noCredits"));
                return;
            }
            
            
            // reward experience
            player.giveExp(getConfig().getInt("rewards.experience"));
            
            // reward items
            List<Map<?, ?>> items = getConfig().getMapList("rewards.items");
            if(items != null) {
                for(Map<?, ?> map : items) {
                    Map<String, Object> serialized = new HashMap<String, Object>();
                    for(Map.Entry<?, ?> entry : map.entrySet()) {
                        if(entry.getKey() instanceof String) {
                            serialized.put((String) entry.getKey(), entry.getValue());
                        }
                    }
                    ItemStack item = ItemStack.deserialize(serialized);
                    player.getInventory().addItem(item);

                }
            }
            
            // TODO: reward currency?


            Long credits = redis.llen(key);
            player.sendMessage(credits > 0
                    ? String.format(getConfig().getString("messages.claimedWithCredits"), credits)
                    : getConfig().getString("messages.claimedWithoutCredits"));
            
            Bukkit.broadcastMessage(String.format(getConfig().getString("messages.broadcastClaim"), player.getName()));
        }
        finally {
            pool.returnResource(redis);
            log("{0} claimed a reward.", player.getName());
        }
    }
    
    
    public void remindPlayer(Player player) {
        Jedis redis = pool.getResource();
        try {
            // TODO: configurable key
            Double lastClaim = redis.zscore("votifier:claims", player.getName().toLowerCase());

            if(lastClaim == null || (System.currentTimeMillis() / 1000L) - lastClaim > getConfig().getInt("canVoteEvery")) {
                player.sendMessage(getConfig().getString("messages.reminder"));
            }
        }
        finally {
            pool.returnResource(redis);
        }
    }
    
    
    public void log(String message) {
        log(Level.INFO, message);
    }
    public void log(Level level, String message) {
        getLogger().log(level, message);
    }
    public void log(String message, Object... args) {
        log(MessageFormat.format(message, args));
    }
    public void log(Level level, String message, Object... args) {
        log(level, MessageFormat.format(message, args));
    }
    
    public void debug(String message) {
        log(Level.FINE, message);
    }
    public void debug(String message, Object... args) {
        debug(MessageFormat.format(message, args));
    }
}