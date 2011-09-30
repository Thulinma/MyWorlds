package com.bergerkiller.bukkit.mw;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.CreatureType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import com.bergerkiller.bukkit.mw.SpawnControl.SpawnRestriction;


public class MyWorlds extends JavaPlugin {
	public static boolean usePermissions = false;
	public static int teleportInterval = 2000;
	public static boolean useWaterTeleport = true;
	public static int timeLockInterval = 20;
	public static boolean useWorldEnterPermissions = false;
	public static boolean usePortalEnterPermissions = false;
	
	public static MyWorlds plugin;
	private static Logger logger = Logger.getLogger("Minecraft");
	public static void log(Level level, String message) {
		logger.log(level, "[MyWorlds] " + message);
	}
	
	private final MWEntityListener entityListener = new MWEntityListener();
	private final MWBlockListener blockListener = new MWBlockListener();
	private final MWWorldListener worldListener = new MWWorldListener();
	private final MWPlayerListener playerListener = new MWPlayerListener();
	private final MWWeatherListener weatherListener = new MWWeatherListener();
	
	public String root() {
		return getDataFolder() + File.separator;
	}
	
	public void onEnable() {
		plugin = this;

		//Event registering
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvent(Event.Type.ENTITY_PORTAL_ENTER, entityListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.CREATURE_SPAWN, entityListener, Priority.Highest, this);
        pm.registerEvent(Event.Type.BLOCK_BREAK, blockListener, Priority.Highest, this);   
        pm.registerEvent(Event.Type.BLOCK_PHYSICS, blockListener, Priority.Highest, this); 
        pm.registerEvent(Event.Type.PLAYER_MOVE, playerListener, Priority.Highest, this);
        pm.registerEvent(Event.Type.PLAYER_TELEPORT, playerListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.SIGN_CHANGE, blockListener, Priority.Highest, this);  
        pm.registerEvent(Event.Type.CHUNK_LOAD, worldListener, Priority.Monitor, this);  
        pm.registerEvent(Event.Type.WORLD_LOAD, worldListener, Priority.Monitor, this);  
        pm.registerEvent(Event.Type.WORLD_UNLOAD, worldListener, Priority.Monitor, this);  
        pm.registerEvent(Event.Type.WEATHER_CHANGE, weatherListener, Priority.Highest, this);  
        
        String locale = "default";
        
        Configuration config = getConfiguration();
        usePermissions = config.getBoolean("usePermissions", usePermissions);
        teleportInterval = config.getInt("teleportInterval", teleportInterval);
        useWaterTeleport = config.getBoolean("useWaterTeleport", useWaterTeleport);
        timeLockInterval = config.getInt("timeLockInterval", timeLockInterval);
        useWorldEnterPermissions = config.getBoolean("useWorldEnterPermissions", useWorldEnterPermissions);
        usePortalEnterPermissions = config.getBoolean("usePortalEnterPermissions", usePortalEnterPermissions);
        locale = config.getString("locale", locale);
        
        config.setProperty("usePermissions", usePermissions);
        config.setProperty("teleportInterval", teleportInterval);
        config.setProperty("useWaterTeleport", useWaterTeleport);
        config.setProperty("timeLockInterval", timeLockInterval);
        config.setProperty("useWorldEnterPermissions", useWorldEnterPermissions);
        config.setProperty("usePortalEnterPermissions", usePortalEnterPermissions);
        config.setProperty("locale", locale);
        config.save();
        
        //Localization
        Localization.init(this, locale);
        
        //Permissions
		Permission.init(this);
		
		//Portals
		Portal.loadPortals(root() + "portals.txt");

		//World info
		WorldConfig.load(root() + "worlds.yml");
		
        //Commands
        getCommand("tpp").setExecutor(this);
        getCommand("world").setExecutor(this);  
        
        //Chunk cache
        WorldManager.initRegionFiles();
        
        //final msg
        PluginDescriptionFile pdfFile = this.getDescription();
        System.out.println("[MyWorlds] version " + pdfFile.getVersion() + " is enabled!" );
	}
	public void onDisable() {
		//Portals
		Portal.savePortals(root() + "portals.txt");
		
		//World info
		WorldConfig.save(root() + "worlds.yml");
		
		System.out.println("My Worlds disabled!");
	}
	
	public boolean showInv(CommandSender sender, String command) {
		message(sender, ChatColor.RED + "Invalid arguments for this command!");
		return showUsage(sender, command);
	}
	
	public boolean showUsage(CommandSender sender, String command) {
		if (Permission.has(sender, command)) {
			String msg = Localization.get("help." + command, "");
			if (msg == null) return true;
			message(sender, msg);
			return true;
		} else {
			return false;
		}
	}
	public static void message(Object sender, String message) {
		if (message != null && message.length() > 0) {
			if (sender instanceof CommandSender) {
				if (!(sender instanceof Player)) {
					message = ChatColor.stripColor(message);
				}
				for (String line : message.split("\n")) {
					((CommandSender) sender).sendMessage(line);
				}
			}
		}
	}
	public static void notifyConsole(CommandSender sender, String message) {
		if (sender instanceof Player) {
			log(Level.INFO, ((Player) sender).getName() + " " + message);
		}
	}
	private void listPortals(CommandSender sender, String[] portals) {
		if (sender instanceof Player) {
			message(sender, ChatColor.GREEN + "[Very near] " + 
		            ChatColor.DARK_GREEN + "[Near] " + 
					ChatColor.YELLOW + "[Far] " + 
		            ChatColor.RED + "[Other world] " + 
					ChatColor.DARK_RED + "[Unavailable]");
		}
		message(sender, ChatColor.YELLOW + "Available portals: " + 
				portals.length + " Portal" + ((portals.length == 1) ? "" : "s"));
		if (portals.length > 0) {
			String msgpart = "";
			for (String portal : portals) {
				Location loc = Portal.getPortalLocation(portal);
				ChatColor color = ChatColor.DARK_RED;
				if (loc != null) {
					if (sender instanceof Player) {
						Location ploc = ((Player) sender).getLocation();
						if (ploc.getWorld() == loc.getWorld()) {
							double d = ploc.distance(loc);
							if (d <= 10) {
								color = ChatColor.GREEN;
							} else if (d <= 100) {
								color = ChatColor.DARK_GREEN;
							} else {
								color = ChatColor.YELLOW;
							}
						} else {
							color = ChatColor.RED;
						}
					}
				}
				
				//display it
				if (msgpart.length() + portal.length() < 70) {
					if (msgpart != "") msgpart += ChatColor.WHITE + ", ";
					msgpart += color + portal;
				} else {
					message(sender, msgpart);
					msgpart = color + portal;
				}
			}
			//possibly forgot one?
			if (msgpart != "") message(sender, msgpart);
		}
	}
	
	public static String[] convertArgs(String[] args) {
		ArrayList<String> tmpargs = new ArrayList<String>();
		boolean isCommenting = false;
		for (String arg : args) {
			if (!isCommenting && (arg.startsWith("\"") || arg.startsWith("'"))) {
				if (arg.endsWith("\"") && arg.length() > 1) {
					tmpargs.add(arg.substring(1, arg.length() - 1));
				} else {
					isCommenting = true;
					tmpargs.add(arg.substring(1));
				}
			} else if (isCommenting && (arg.endsWith("\"") || arg.endsWith("'"))) {
				arg = arg.substring(0, arg.length() - 1);
				arg = tmpargs.get(tmpargs.size() - 1) + " " + arg;
				tmpargs.set(tmpargs.size() - 1, arg);
				isCommenting = false;
			} else if (isCommenting) {
				arg = tmpargs.get(tmpargs.size() - 1) + " " + arg;
				tmpargs.set(tmpargs.size() - 1, arg);
			} else {
				tmpargs.add(arg);
			}
		}
		return tmpargs.toArray(new String[0]);
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String cmdLabel, String[] args) {
		//First of all, convert the args so " " is in one arg.
		args = convertArgs(args);
		
		String node = null; //generate a permission node from this command
		if (cmdLabel.equalsIgnoreCase("world")
				|| cmdLabel.equalsIgnoreCase("myworlds")
				|| cmdLabel.equalsIgnoreCase("worlds")
				|| cmdLabel.equalsIgnoreCase("mw")) {
			if (args.length >= 1) {
				if (args[0].equalsIgnoreCase("list")) {
					node = "world.list";
				} else if (args[0].equalsIgnoreCase("info")) {
					node = "world.info";
				} else if (args[0].equalsIgnoreCase("i")) {
					node = "world.info";
				} else if (args[0].equalsIgnoreCase("portals")) {
					node = "world.portals";
				} else if (args[0].equalsIgnoreCase("portal")) {
					node = "world.portals";
				} else if (args[0].equalsIgnoreCase("load")) {
					node = "world.load";
				} else if (args[0].equalsIgnoreCase("unload")) {
					node = "world.unload";
				} else if (args[0].equalsIgnoreCase("create")) {
					node = "world.create";
				} else if (args[0].equalsIgnoreCase("spawn")) {
					node = "world.spawn";
				} else if (args[0].equalsIgnoreCase("evacuate")) {
					node = "world.evacuate";
				} else if (args[0].equalsIgnoreCase("evac")) {
					node = "world.evacuate";
				} else if (args[0].equalsIgnoreCase("repair")) {
					node = "world.repair";
				} else if (args[0].equalsIgnoreCase("rep")) {
					node = "world.repair";
				} else if (args[0].equalsIgnoreCase("save")) {
					node = "world.save";
				} else if (args[0].equalsIgnoreCase("delete")) {
					node = "world.delete";
				} else if (args[0].equalsIgnoreCase("del")) {
					node = "world.delete";
				} else if (args[0].equalsIgnoreCase("copy")) {
					node = "world.copy";
				} else if (args[0].equalsIgnoreCase("togglepvp")) {
					node = "world.togglepvp";
				} else if (args[0].equalsIgnoreCase("tpvp")) {
					node = "world.togglepvp";	
				} else if (args[0].equalsIgnoreCase("pvp")) {
					node = "world.togglepvp";			
				} else if (args[0].equalsIgnoreCase("weather")) {
				    node = "world.weather";
				} else if (args[0].equalsIgnoreCase("w")) {
				    node = "world.weather";
				} else if (args[0].equalsIgnoreCase("time")) {
				    node = "world.time";
				} else if (args[0].equalsIgnoreCase("t")) {
				    node = "world.time";
				} else if (args[0].equalsIgnoreCase("allowspawn")) {
				    node = "world.allowspawn";
				} else if (args[0].equalsIgnoreCase("denyspawn")) {
				    node = "world.denyspawn";
				} else if (args[0].equalsIgnoreCase("spawnallow")) {
				    node = "world.allowspawn";
				} else if (args[0].equalsIgnoreCase("spawndeny")) {
				    node = "world.denyspawn";
				} else if (args[0].equalsIgnoreCase("allowspawning")) {
				    node = "world.allowspawn";
				} else if (args[0].equalsIgnoreCase("denyspawning")) {
				    node = "world.denyspawn";
				} else if (args[0].equalsIgnoreCase("setportal")) {
					node = "world.setportal";
				} else if (args[0].equalsIgnoreCase("setdefaultportal")) {
					node = "world.setportal";
				} else if (args[0].equalsIgnoreCase("setdefportal")) {
					node = "world.setportal";
				} else if (args[0].equalsIgnoreCase("setdefport")) {
					node = "world.setportal";
				} else if (args[0].equalsIgnoreCase("setspawn")) {
					node = "world.setspawn";
				} else if (args[0].equalsIgnoreCase("gamemode")) {
					node = "world.gamemode";
				} else if (args[0].equalsIgnoreCase("setgamemode")) {
					node = "world.gamemode";
				} else if (args[0].equalsIgnoreCase("gm")) {
					node = "world.gamemode";
				} else if (args[0].equalsIgnoreCase("setgm")) {
					node = "world.gamemode";
				} else if (args[0].equalsIgnoreCase("generators")) {
					node = "world.listgenerators";
				} else if (args[0].equalsIgnoreCase("gen")) {
					node = "world.listgenerators";
				} else if (args[0].equalsIgnoreCase("listgenerators")) {
					node = "world.listgenerators";
				} else if (args[0].equalsIgnoreCase("listgen")) {
					node = "world.listgenerators";
				}
			}
			if (node == null) {
				//show default usage for /world
				boolean hac = false; //has available commands
				if (showUsage(sender, "world.repair")) hac = true;
				if (showUsage(sender, "world.delete")) hac = true;
				if (showUsage(sender, "world.rename")) hac = true;
				if (showUsage(sender, "world.copy")) hac = true;
				if (showUsage(sender, "world.save")) hac = true;
				if (showUsage(sender, "world.load")) hac = true;
				if (showUsage(sender, "world.unload")) hac = true;
				if (showUsage(sender, "world.create")) hac = true;
				if (showUsage(sender, "world.listgenerators")) hac = true;
				if (showUsage(sender, "world.weather")) hac = true;		
				if (showUsage(sender, "world.time")) hac = true;			
				if (showUsage(sender, "world.spawn")) hac = true;
				if (showUsage(sender, "world.setspawn")) hac = true;
				if (showUsage(sender, "world.list")) hac = true;
				if (showUsage(sender, "world.info")) hac = true;
				if (showUsage(sender, "world.portals")) hac = true;
				if (showUsage(sender, "world.gamemode")) hac = true;
				if (showUsage(sender, "world.togglepvp")) hac = true;
				if (showUsage(sender, "world.allowspawn")) hac = true;
				if (showUsage(sender, "world.denyspawn")) hac = true;		
				if (hac) {
					if (args.length >= 1) message(sender, ChatColor.RED + "Unknown command: " + args[0]);
				} else {
					Localization.message(sender, "command.nopermission");
				}
			}
		} else if (cmdLabel.equalsIgnoreCase("tpp")) {
			node = "tpp";
		}
		if (node != null) {
			if (Permission.has(sender, node)) {
				//nodes made, commands can now be executed (finally!)
				if (node == "world.list") {
					//==========================================
					//===============LIST COMMAND===============
					//==========================================
					if (sender instanceof Player) {
						//perform some nice layout coloring
						sender.sendMessage("");
						sender.sendMessage(ChatColor.GREEN + "[Loaded/Online] " + ChatColor.RED + "[Unloaded/Offline] " + ChatColor.DARK_RED + "[Broken/Dead]");
						sender.sendMessage(ChatColor.YELLOW + "Available worlds: ");
						String msgpart = "";
						for (String world : WorldManager.getWorlds()) {
							//prepare it
							if (WorldManager.isLoaded(world)) {
								world = ChatColor.GREEN + world;
							} else if (WorldManager.getData(world) == null) {
								world = ChatColor.DARK_RED + world;
							} else {
								world = ChatColor.RED + world;
							}
							//display it
							if (msgpart.length() + world.length() < 70) {
								if (msgpart != "") msgpart += ChatColor.WHITE + " / ";
								msgpart += world;
							} else {
								sender.sendMessage(msgpart);
								msgpart = world;
							}
						}
						//possibly forgot one?
						if (msgpart != "") sender.sendMessage(msgpart);
					} else {
						//plain world per line
						sender.sendMessage("Available worlds:");
						for (String world : WorldManager.getWorlds()) {
							String status = "[Unloaded]";
							if (WorldManager.isLoaded(world)) {
								status = "[Loaded]";
							} else if (WorldManager.getData(world) == null) {
								status = "[Broken]";
							}
							sender.sendMessage("    " + world + " " + status);
						}
					}
				} else if (node == "world.portals") {
					//==========================================
					//===============PORTALS COMMAND============
					//==========================================
					String[] portals;
					if (args.length == 2) {
						World w = WorldManager.getWorld(WorldManager.matchWorld(args[1]));
						if (w != null) {
							portals = Portal.getPortals(w);
						} else {
							message(sender, ChatColor.RED + "World not found!");
							return true;
						}
					} else {
						portals = Portal.getPortals();
					}
					listPortals(sender, portals);
				} else if (node == "world.listgenerators") {
					//==================================================
					//===============LIST GENERATORS COMMAND============
					//==================================================
					message(sender, ChatColor.YELLOW + "Available chunk generators:");
					String msgpart = "";
					for (String plugin : WorldManager.getGeneratorPlugins()) {
						plugin = ChatColor.GREEN + plugin;
						//display it
						if (msgpart.length() + plugin.length() < 70) {
							if (msgpart != "") msgpart += ChatColor.WHITE + " / ";
							msgpart += plugin;
						} else {
							sender.sendMessage(msgpart);
							msgpart = plugin;
						}
					}
					message(sender, msgpart);
				} else if (node == "world.setportal") {
					//==============================================
					//===============SET DEFAULT PORTAL COMMAND=====
					//==============================================
					if (args.length > 1) {
						String dest = args[1];
						String worldname = WorldManager.getWorldName(sender, args, args.length == 3);
						if (worldname != null) {
							if (dest.equals("")) {
								Portal.setDefault(worldname, null);
								message(sender, ChatColor.GREEN + "Default destination of world '" + worldname + "' cleared!");
							} else if (Portal.getPortalLocation(dest) != null) {
								Portal.setDefault(worldname, dest);
								message(sender, ChatColor.GREEN + "Default destination of world '" + worldname + "' set to portal: '" + dest + "'!");
							} else if ((dest = WorldManager.matchWorld(dest)) != null) {
								Portal.setDefault(worldname, dest);
								message(sender, ChatColor.GREEN + "Default destination of world '" + worldname + "' set to world: '" + dest + "'!");
								if (!WorldManager.isLoaded(dest)) {
									message(sender, ChatColor.YELLOW + "Note that this world is not loaded, so nothing happens yet!");
								}
							} else {
								message(sender, ChatColor.RED + "Destination is not a world or portal!");
							}
						} else {
							message(sender, ChatColor.RED + "World not found!");
						}
					} else {
						showInv(sender, node);
					}
				} else if (node == "world.info") {
					//==========================================
					//===============INFO COMMAND===============
					//==========================================
					String worldname = null;
					if (args.length == 2) {
						worldname = WorldManager.matchWorld(args[1]);
					} else if (sender instanceof Player) {
						worldname = ((Player) sender).getWorld().getName();
					} else {
						for (World w : getServer().getWorlds()) {
							worldname = w.getName();
							break;
						}
					}
					if (worldname != null) {
						WorldInfo info = WorldManager.getInfo(worldname);
						if (info == null) {
							message(sender, ChatColor.RED + "' " + worldname + "' is broken, no information can be shown!");
						} else {
							message(sender, ChatColor.YELLOW + "Information about the world: " + worldname);
							message(sender, ChatColor.WHITE + "Internal name: " + ChatColor.YELLOW + info.name);
							message(sender, ChatColor.WHITE + "Environment: " + ChatColor.YELLOW + info.environment.name().toLowerCase());
							if (info.chunkGenerator == null) {
								message(sender, ChatColor.WHITE + "Chunk generator: " + ChatColor.YELLOW + "Default");
							} else {
								message(sender, ChatColor.WHITE + "Chunk generator: " + ChatColor.YELLOW + info.chunkGenerator);
							}
							message(sender, ChatColor.WHITE + "World seed: " + ChatColor.YELLOW + info.seed);
							if (info.size > 1000000) {
								message(sender, ChatColor.WHITE + "World size: " + ChatColor.YELLOW + (info.size / 1000000) + " Megabytes");
							} else if (info.size > 1000) {
								message(sender, ChatColor.WHITE + "World size: " + ChatColor.YELLOW + (info.size / 1000) + " Kilobytes");
							} else {
								message(sender, ChatColor.WHITE + "World size: " + ChatColor.YELLOW + (info.size) + " Bytes");
							}
							//PvP
							if (PvPData.isPvP(worldname)) { 
								message(sender, ChatColor.WHITE + "PvP: " + ChatColor.GREEN + "Enabled");
							} else {
								message(sender, ChatColor.WHITE + "PvP: " + ChatColor.YELLOW + "Disabled");
							}
							//Game mode
							GameMode mode = GamemodeHandler.get(worldname, null);
							if (mode == null) {
								message(sender, ChatColor.WHITE + "Game mode: " + ChatColor.YELLOW + "Not set");
							} else {
								message(sender, ChatColor.WHITE + "Game mode: " + ChatColor.YELLOW + mode.name().toLowerCase());
							}
							//Time
							String timestr = TimeControl.getTimeString(worldname, info.time);
							message(sender, ChatColor.WHITE + "Time: " + ChatColor.YELLOW + timestr);
							//Weather
							if (MWWeatherListener.isHolding(worldname)) {
								if (info.raining) {
									if (info.thundering) {
										message(sender, ChatColor.WHITE + "Weather: " + ChatColor.YELLOW + "Endless storm with lightning");
									} else {
										message(sender, ChatColor.WHITE + "Weather: " + ChatColor.YELLOW + "Endless rain and snow");
									}
								} else {
									message(sender, ChatColor.WHITE + "Weather: " + ChatColor.YELLOW + "No bad weather expected");
								}
							} else {
								if (info.raining) {
									if (info.thundering) {
										message(sender, ChatColor.WHITE + "Weather: " + ChatColor.YELLOW + "Stormy with lightning");
									} else {
										message(sender, ChatColor.WHITE + "Weather: " + ChatColor.YELLOW + "Rain and snow");
									}
								} else {
									message(sender, ChatColor.WHITE + "Weather: " + ChatColor.YELLOW + "The sky is clear");
								}
							}							
							World w = Bukkit.getServer().getWorld(worldname);
							if (w != null) {
								int playercount = w.getPlayers().size();
								if (playercount > 0) {
									String msg = ChatColor.WHITE + "Status: " + ChatColor.GREEN + "Loaded" + ChatColor.WHITE + " with ";
									msg += playercount + ((playercount <= 1) ? " player" : " players");
									message(sender, msg);
								} else {
									message(sender, ChatColor.WHITE + "Status: " + ChatColor.YELLOW + "Stand-by");
								}
							} else {
								message(sender, ChatColor.WHITE + "Status: " + ChatColor.RED + "Unloaded");
							}
						}
					} else {
						message(sender, ChatColor.RED + "World not found!");
					}
				} else if (node == "world.gamemode") {
					String worldname = WorldManager.getWorldName(sender, args, args.length == 3);
					if (worldname != null) {
						if (args.length == 1) {
							//display
							GameMode mode = GamemodeHandler.get(worldname, null);
							String msg = ChatColor.YELLOW + "Current game mode of world '" + worldname + "': ";
							if (mode == null) {
								message(sender, msg + ChatColor.YELLOW + "Not set");
							} else {
								message(sender, msg + ChatColor.YELLOW + mode.name().toLowerCase());
							}
						} else {
							//Parse the gamemode
							GameMode mode = GamemodeHandler.getMode(args[1], null);
							GamemodeHandler.set(worldname, mode);
							if (mode == null) {
								message(sender, ChatColor.YELLOW + "Game mode of World '" + worldname + "' cleared!");
							} else {
								World w = WorldManager.getWorld(worldname);
								if (w != null) {
									GamemodeHandler.updatePlayers(w);
								}
								message(sender, ChatColor.YELLOW + "Game mode of World '" + worldname + "' set to " + mode.name().toLowerCase() + "!");
							}
						}
					} else {
						message(sender, ChatColor.RED + "World not found!");
					}
				} else if (node == "world.togglepvp") {
					//==========================================
					//===============TOGGLE PVP COMMAND=========
					//==========================================
					String worldname = WorldManager.getWorldName(sender, args, args.length == 2);
					if (worldname != null) {
						PvPData.setPvP(worldname, !PvPData.isPvP(worldname));
						if (PvPData.isPvP(worldname)) {
							message(sender, ChatColor.GREEN + "PvP on World: '" + worldname + "' enabled!");
						} else {
							message(sender, ChatColor.YELLOW + "PvP on World: '" + worldname + "' disabled!");
						}
						if (!WorldManager.isLoaded(worldname)) {
							message(sender, ChatColor.YELLOW + "Please note that this world is not loaded!");
						}
					} else {
						message(sender, ChatColor.RED + "World not found!");
					}
				} else if (node == "world.allowspawn" || node == "world.denyspawn") {
					//==========================================
					//===============TOGGLE ANIMAL SPAWNING=====
					//==========================================
					String worldname = WorldManager.getWorldName(sender, args, args.length == 3);
					if (worldname != null) {
						if (args.length >= 2) {
							SpawnControl.SpawnRestriction r = SpawnControl.get(worldname);
							//Get the type to set
							String type = null;
							if (args[1].equalsIgnoreCase("animal")) {
								type = "animal";
							} else if (args[1].equalsIgnoreCase("animals")) {
								type = "animal";
							} else if (args[1].equalsIgnoreCase("monster")) {
								type = "monster";
							} else if (args[1].equalsIgnoreCase("monsters")) {
								type = "monster";
							} else if (args[1].equalsIgnoreCase("mob")) {
								type = "mob";
							} else if (args[1].equalsIgnoreCase("mobs")) {
								type = "mob";	
							} else if (args[1].equalsIgnoreCase("creature")) {
								type = "mob";
							} else if (args[1].equalsIgnoreCase("creatures")) {
								type = "mob";
							} else if (args[1].equalsIgnoreCase("all")) {
								type = "mob";
							} else {
								type = args[1].toUpperCase();
								CreatureType ctype = null;
								try {
									ctype = CreatureType.valueOf(type);
								} catch (Exception e) {}
								if (ctype == null && type.endsWith("S")) {
									try {
										ctype = CreatureType.valueOf(type.substring(0, type.length() - 2));
									} catch (Exception e) {}
								}
								if (ctype != null) {
									type = ctype.name().toLowerCase();
								} else {
									type = null;
								}
							}
							//Set it, of course
							if (type != null) {
								if (node == "world.allowspawn") {
									if (type.equals("animal")) {
										r.setAnimals(false);
									} else if (type.equals("monster")) {
										r.setMonsters(false);
									} else if (type.equals("mob")) {
										r.deniedCreatures.clear();
									} else {
										r.deniedCreatures.remove(CreatureType.valueOf(type.toUpperCase()));
									}
									if (WorldManager.isLoaded(worldname)) {
										message(sender, ChatColor.GREEN + type + "s are now allowed to spawn on world: '" + worldname + "'!");
									} else {
										message(sender, ChatColor.GREEN + type + "s are allowed to spawn on world: '" + worldname + "' once it is loaded!");
									}
								} else {
									if (type.equals("animal")) {
										r.setAnimals(true);
									} else if (type.equals("monster")) {
										r.setMonsters(true);
									} else if (type.equals("mob")) {
										r.setAnimals(true);
										r.setMonsters(true);
									} else {
										r.deniedCreatures.add(CreatureType.valueOf(type.toUpperCase()));
									}
									//Capitalize
									type = Character.toUpperCase(type.charAt(0)) + type.substring(1);
									if (WorldManager.isLoaded(worldname)) {
										message(sender, ChatColor.YELLOW + type + "s can no longer spawn on world: '" + worldname + "'!");
									} else {
										message(sender, ChatColor.YELLOW + type + "s can no longer spawn on world: '" + worldname + "' once it is loaded!");
									}
								}
								World w = WorldManager.getWorld(worldname);
								if (w != null) {
									SpawnRestriction restriction = SpawnControl.get(worldname);
									for (Entity e : w.getEntities()) {
										if (restriction.isDenied(e)) {
											e.remove();
										}
									}
								}
							} else {
								message(sender, ChatColor.RED + "Invalid creature type!");
							}
						}
						//Mobs
						SpawnRestriction restr = SpawnControl.get(worldname);
						if (restr.deniedCreatures.size() == 0) {
							message(sender, ChatColor.WHITE + "All mobs are allowed to spawn right now.");
						} else {
							message(sender, ChatColor.WHITE + "The following mobs are denied from spawning:");
							String message = ChatColor.YELLOW.toString();
							boolean first = true;
							for (CreatureType type : restr.deniedCreatures) {
								if (first) {
									message += type.getName();
									first = false;
								} else {
									message += ", " + type.getName();
								}
							}
							message(sender, message);
						}
					} else {
						message(sender, ChatColor.RED + "World not found!");
					}					
				} else if (node == "world.weather") {
					//=======================================
					//===============WEATHER COMMAND=========
					//=======================================
					if (args.length > 1) {
						boolean setStorm = false;
						boolean setSun = false;
						boolean setHold = false;
						boolean useWorld = true;
						boolean setThunder = false;
						for (String command : args) {
							if (command.equalsIgnoreCase("hold")) {
								setHold = true;
							} else if (command.equalsIgnoreCase("always")) {
								setHold = true;
							} else if (command.equalsIgnoreCase("endless")) { 
								setHold = true;
							} else if (command.equalsIgnoreCase("sun")) {
								setSun = true;
							} else if (command.equalsIgnoreCase("sunny")) { 
								setSun = true;
							} else if (command.equalsIgnoreCase("endless")) {
								setSun = true;
							} else if (command.equalsIgnoreCase("storm")) {
								setStorm = true;
							} else if (command.equalsIgnoreCase("stormy")) {
								setStorm = true;
							} else if (command.equalsIgnoreCase("rain")) {
								setStorm = true;
							} else if (command.equalsIgnoreCase("rainy")) {
								setStorm = true;
							} else if (command.equalsIgnoreCase("snow")) {
								setStorm = true;
							} else if (command.equalsIgnoreCase("snowy")) {
								setStorm = true;
							} else if (command.equalsIgnoreCase("thunder")) {
								setThunder = true;
							} else if (command.equalsIgnoreCase("lightning")) {
								setThunder = true;
							} else if (command.equalsIgnoreCase("heavy")) {
								setThunder = true;
							} else if (command.equalsIgnoreCase("big")) {
								setThunder = true;
							} else if (command.equalsIgnoreCase("huge")) {
								setThunder = true;
							} else {
								continue;
							}
							//Used the last argument as command?
							if (command == args[args.length - 1]) useWorld = false;
						}
						String worldname = WorldManager.getWorldName(sender, args, useWorld);
						if (worldname != null) {
							MWWeatherListener.holdWorld(worldname, setHold);
							World w = WorldManager.getWorld(worldname);
							if (w != null) {
								boolean holdchange = MWWeatherListener.isHolding(worldname) != setHold;
								if (setStorm && ((!w.hasStorm()) || (setThunder && !w.isThundering()) || holdchange)) {
									MWWeatherListener.setWeather(w, true, setHold);
									if (setThunder) {
										 w.setThundering(true);
									}
									String a = "";
									if (setThunder) a = "rumbling ";
									if (setHold) {
										if (setThunder) w.setThunderDuration(Integer.MAX_VALUE);
										message(sender, ChatColor.GREEN + "You started an endless " +  a + "storm on world: '" + worldname + "'!");
									} else {
										message(sender, ChatColor.GREEN + "You started a " +  a + "storm on world: '" + worldname + "'!");
									}
								} else if (setSun && (w.hasStorm() || holdchange)) {
									MWWeatherListener.setWeather(w, false, setHold);
									if (setHold) {
										message(sender, ChatColor.GREEN + "You stopped the formation of storms on world: '" + worldname + "'!");
									} else {
										message(sender, ChatColor.GREEN + "You stopped a storm on world: '" + worldname + "'!");
									}
								} else if (setHold) {
									message(sender, ChatColor.GREEN + "Weather changes on world: '" + worldname + "' are now being prevented!");
								} else {
									message(sender, ChatColor.YELLOW + "Unknown syntax or the settings were already applied!");
								}
							} else {
								message(sender, ChatColor.YELLOW + "World: '" + worldname + "' is not loaded, only hold settings are applied!");
							}
						} else {
							message(sender, ChatColor.RED + "World not found!");
						}
					} else {
						showInv(sender, node);
					}
				} else if (node == "world.setspawn") {
					//====================================
					//===============SET SPAWN COMMAND====
					//====================================
					if (sender instanceof Player) {
						Location loc = ((Player) sender).getLocation();
						loc.getWorld().setSpawnLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
						sender.sendMessage(ChatColor.GREEN + "Spawn location of world '" + loc.getWorld().getName() + "' set!");
					} else {
						sender.sendMessage("This command is only for players!");
					}
				} else if (node == "world.time") {
					//====================================
					//===============TIME COMMAND=========
					//====================================
					boolean lock = false;
					boolean useWorld = false;
					long time = -1;
					boolean firstcheck = true;
					for (String command : args) {
						if (firstcheck) {
							firstcheck = false;
							continue;
						}
						//Time reading
						if (time == -1) {
							time = TimeControl.getTime(command);
						}
						if (command.equalsIgnoreCase("lock")) {
							lock = true;
						} else if (command.equalsIgnoreCase("locked")) {
							lock = true;
						} else if (command.equalsIgnoreCase("always")) {
							lock = true;
						} else if (command.equalsIgnoreCase("endless")) {
							lock = true;
						} else if (command.equalsIgnoreCase("l")) {
							lock = true;
						} else if (command.equalsIgnoreCase("-l")) {
							lock = true;	
						} else if (command.equalsIgnoreCase("stop")) {
							lock = true;
						} else if (command.equalsIgnoreCase("freeze")) {
							lock = true;
						} else {
							time = TimeControl.getTime(command);
							if (time == -1) {
								//Used the last argument as command?
								if (command == args[args.length - 1]) useWorld = true;
							}
						}
					}
					String worldname = WorldManager.getWorldName(sender, args, useWorld);
					if (worldname != null) {
						if (time == -1) {
							World w = WorldManager.getWorld(worldname);
							if (w == null) {
								WorldInfo i = WorldManager.getInfo(worldname);
								if (i == null) {
									time = 0;
								} else {
									time = i.time;
								}
							} else {
								time = w.getFullTime();
							}
						}
						if (args.length == 1) {
							message(sender, ChatColor.YELLOW + "The current time of world '" + 
									worldname + "' is " + TimeControl.getTimeString(time));
						} else {
							if (lock) {
								TimeControl.lockTime(worldname, time);
								if (!WorldManager.isLoaded(worldname)) {
									TimeControl.setLocking(worldname, false);
									message(sender, ChatColor.YELLOW + "World '" + worldname + 
											"' is not loaded, time will be locked to " + 
											TimeControl.getTimeString(time) + " as soon it is loaded!");
								} else {
									TimeControl.setLocking(worldname, true);
									message(sender, ChatColor.GREEN + "Time of world '" + worldname + "' locked to " + 
									        TimeControl.getTimeString(time) + "!");
								}
							} else {
								World w = WorldManager.getWorld(worldname);
								if (w != null) {
									if (TimeControl.isLocked(worldname)) {
										TimeControl.unlockTime(worldname);
										w.setFullTime(time);
										message(sender, ChatColor.GREEN + "Time of world '" + worldname + "' unlocked and set to " + 
										        TimeControl.getTimeString(time) + "!");
									} else {
										w.setFullTime(time);
										message(sender, ChatColor.GREEN + "Time of world '" + worldname + "' set to " + 
										        TimeControl.getTimeString(time) + "!");
									}
								} else {
									message(sender, ChatColor.RED + "World '" + worldname + "' is not loaded, time is not changed!");
								}
							}
						}
					} else {
						message(sender, ChatColor.RED + "World not found!");
					}
				} else if (node == "world.load") {
					//==========================================
					//===============LOAD COMMAND===============
					//==========================================
					if (args.length == 2) {
						String worldname = WorldManager.matchWorld(args[1]);
						if (worldname != null) {
							if (WorldManager.isLoaded(worldname)) {
								message(sender, ChatColor.YELLOW + "World '" + worldname + "' is already loaded!");
							} else {
								notifyConsole(sender, "Issued a load command for world: " + worldname);
								message(sender, ChatColor.YELLOW + "Loading world: '" + worldname + "'...");
								if (WorldManager.createWorld(worldname, null) != null) {
									message(sender, ChatColor.GREEN + "World loaded!");
								} else {
									message(sender, ChatColor.RED + "Failed to load world, it is probably broken!");
								}
							}
						} else {
							message(sender, ChatColor.RED + "World not found!");
						}
					} else {
						showInv(sender, node);
					}
				} else if (node == "world.unload") {
					//============================================
					//===============UNLOAD COMMAND===============
					//============================================
					if (args.length == 2) {
						String worldname = WorldManager.matchWorld(args[1]);
						if (worldname != null) {
							World w = Bukkit.getServer().getWorld(worldname);
							if (w != null) {
								notifyConsole(sender, "Issued an unload command for world: " + worldname);
								if (WorldManager.unload(w)) {
									message(sender, ChatColor.GREEN + "World '" + worldname + "' has been unloaded!");
								} else {
									message(sender, ChatColor.RED + "Failed to unload the world (main world or online players?)");
								}
							} else {
								message(sender, ChatColor.YELLOW + "World '" + worldname + "' is already unloaded!");
							}
						} else {
							message(sender, ChatColor.RED + "World not found!");
						}
					} else {
						showInv(sender, node);
					}
				} else if (node == "world.evacuate") {
					//==============================================
					//===============EVACUATE COMMAND===============
					//==============================================
					if (args.length >= 2) {
						String worldname = WorldManager.matchWorld(args[1]);
						if (worldname != null) {
							World w = Bukkit.getServer().getWorld(worldname);
							if (w != null) {
								String message = "";
								for (int i = 2;i < args.length;i++) {
									message += args[i] + " ";
								}
								if (message == "") {
									message = "Your world has been closed down!";
								} else {
									message = message.trim();
								}
								notifyConsole(sender, "Evacuated world: " + worldname +  " ('" + message + "')");
								WorldManager.evacuate(w, message);
							} else {
								message(sender, ChatColor.YELLOW + "World '" + worldname + "' is not loaded!");
							}
						} else {
							message(sender, ChatColor.RED + "World not found!");
						}
					} else {
						showInv(sender, node);
					}
				} else if (node == "world.create") {
					//============================================
					//===============CREATE COMMAND===============
					//============================================
					if (args.length >= 2) {
						String worldname = args[1];
						String gen = null;
						if (worldname.contains(":")) {
							String[] parts = worldname.split(":");
							if (parts.length == 2) {
								worldname = parts[0];
								gen = parts[1];
							} else {
								worldname = parts[0];
								gen = parts[1] + ":" + parts[2];
							}
						}
						if (!WorldManager.worldExists(worldname)) {
							String seed = "";
							for (int i = 2;i < args.length;i++) {
								seed += args[i] + " ";
							}
							notifyConsole(sender, "Issued a world creation command for world: " + worldname);
							if (gen == null) {
								message(sender, ChatColor.YELLOW + "Creating world '" + worldname + "' (this can take a while) ...");
							} else {
								gen = WorldManager.fixGeneratorName(gen);
								if (gen == null) {
									message(sender, ChatColor.RED + "Failed to create world because the generator '" + gen + "' is missing!");
								} else {
									WorldManager.setGenerator(worldname, gen);
									message(sender, ChatColor.YELLOW + "Creating world '" + worldname + "' using generator '" + gen + "' (this can take a while) ...");
								}
							}
							if (WorldManager.createWorld(worldname, seed) != null) {
								MyWorlds.message(sender, ChatColor.GREEN + "World '" + worldname + "' has been created and is ready for use!");
							} else {
								MyWorlds.message(sender, ChatColor.RED + "World creation failed!");
							}
						} else {
							message(sender, ChatColor.RED + "World already exists!");
						}
					} else {
						showInv(sender, node);
					}
				} else if (node == "world.repair") {
					//============================================
					//===============REPAIR COMMAND===============
					//============================================
					if (args.length >= 2) {
						String worldname = WorldManager.matchWorld(args[1]);
						if (worldname == null) worldname = args[1];
						if (WorldManager.getDataFolder(worldname).exists()) {
							if (!WorldManager.isLoaded(worldname)) {
								AsyncHandler.repair(sender, worldname);
							} else {
								message(sender, ChatColor.YELLOW + "Can't repair a loaded world!");
							}
						} else {
							message(sender, ChatColor.RED + "World not found!");
						}
					} else {
						showInv(sender, node);
					}
				} else if (node == "world.spawn") {
					//===========================================
					//===============SPAWN COMMAND===============
					//===========================================
					String worldname = WorldManager.getWorldName(sender, args, args.length >= 2);
					if (worldname != null) {
						World w = Bukkit.getServer().getWorld(worldname);
						if (w != null) {
							if (sender instanceof Player) {
								if (Permission.handleTeleport((Player) sender, w.getSpawnLocation())) {
									//Success
								}
							} else {
								sender.sendMessage("A player is expected!");
							}
						} else {
							message(sender, ChatColor.YELLOW + "World '" + worldname + "' is not loaded!");
						}
					} else {
						message(sender, ChatColor.RED + "World not found!");
					}
				} else if (node == "world.save") {
					//==========================================
					//===============SAVE COMMAND===============
					//==========================================
					String worldname;
					if (args.length >= 2) {
						worldname = WorldManager.matchWorld(args[1]);
						if (worldname == null) worldname = args[1];
					} else if (sender instanceof Player) {
						worldname = ((Player) sender).getWorld().getName();
					} else {
						worldname = "*";
					}
					World w = WorldManager.getWorld(worldname);
					if (w != null) {
						message(sender, ChatColor.YELLOW + "Saving world '" + worldname + "'...");
						w.save();
						message(sender, ChatColor.GREEN + "World saved!");
					} else if (worldname.equalsIgnoreCase("all") || worldname.equals("*")) {
						message(sender, ChatColor.YELLOW + "Forcing a global world save...");	
						for (World ww : getServer().getWorlds()) {
							ww.save();
						}
						message(sender, ChatColor.GREEN + "All worlds have been saved!");			
					} else {
						message(sender, ChatColor.RED + "World not found!");
					}
				} else if (node == "world.delete") {
					//============================================
					//===============DELETE COMMAND===============
					//============================================
					if (args.length == 2) {
						String worldname = args[1];
						if (WorldManager.worldExists(worldname)) {
							if (!WorldManager.isLoaded(worldname)) {
								notifyConsole(sender, "Issued a world deletion command for world: " + worldname);
								AsyncHandler.delete(sender, worldname);
							} else {
								message(sender, ChatColor.RED + "World is loaded, please unload the world first!");
							}
						} else {
							message(sender, ChatColor.RED + "World not found!");
						}
					} else {
						showInv(sender, node);
					}
				} else if (node == "world.copy") {
					//============================================
					//===============COPY COMMAND=================
					//============================================
					if (args.length == 3) {
						String worldname = args[1];
						if (WorldManager.worldExists(worldname)) {
							String newname = args[2];
							if (!WorldManager.worldExists(newname)) {
								notifyConsole(sender, "Issued a world copy command for world: " + worldname + " to '" + newname + "'!");
								message(sender, ChatColor.YELLOW + "Copying world '" + worldname + "' to '" + newname + "'...");
								AsyncHandler.copy(sender, worldname, newname);
							} else {
								message(sender, ChatColor.RED + "Can not copy to an existing world!");
							}
						} else {
							message(sender, ChatColor.RED + "World not found!");
						}
					} else {
						showInv(sender, node);
					}
				} else if (node == "tpp") {
					//================================================
					//===============TELEPORT PORTAL COMMAND==========
					//================================================
					if (sender instanceof Player) {
						Player player = (Player) sender;
						if (args.length == 1) {
							Location tele = Portal.getPortalLocation(args[0], true);
							if (tele != null) {
								if (Permission.handleTeleport(player, args[0], tele)) {
									//Success
								}
							} else {
								//Match world
								String worldname = WorldManager.matchWorld(args[0]);
								if (worldname != null) {
									World w = WorldManager.getWorld(worldname);
									if (w != null) {
										if (Permission.handleTeleport(player, w.getSpawnLocation())) {
											//Success
										}
									} else {
										message(sender, ChatColor.YELLOW + "World '" + worldname + "' is not loaded!");
									}
								} else {
									Localization.message(sender, "portal.notfound");
									listPortals(sender, Portal.getPortals());
								}
							}
						} else {
							showInv(sender, node);
						}	
					} else {
						sender.sendMessage("This command is only for players!");
					}		
				}
			} else {
				Localization.message(sender, "command.nopermission");
			}	
		}
		return true;
	}

}
