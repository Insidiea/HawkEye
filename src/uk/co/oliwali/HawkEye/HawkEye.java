package uk.co.oliwali.HawkEye;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Type;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;

import uk.co.oliwali.HawkEye.commands.PreviewApplyCommand;
import uk.co.oliwali.HawkEye.commands.BaseCommand;
import uk.co.oliwali.HawkEye.commands.PreviewCancelCommand;
import uk.co.oliwali.HawkEye.commands.HelpCommand;
import uk.co.oliwali.HawkEye.commands.HereCommand;
import uk.co.oliwali.HawkEye.commands.PageCommand;
import uk.co.oliwali.HawkEye.commands.PreviewCommand;
import uk.co.oliwali.HawkEye.commands.RollbackCommand;
import uk.co.oliwali.HawkEye.commands.SearchCommand;
import uk.co.oliwali.HawkEye.commands.ToolBindCommand;
import uk.co.oliwali.HawkEye.commands.ToolCommand;
import uk.co.oliwali.HawkEye.commands.ToolResetCommand;
import uk.co.oliwali.HawkEye.commands.TptoCommand;
import uk.co.oliwali.HawkEye.commands.UndoCommand;
import uk.co.oliwali.HawkEye.commands.WorldEditRollbackCommand;
import uk.co.oliwali.HawkEye.database.DataManager;
import uk.co.oliwali.HawkEye.listeners.MonitorBlockListener;
import uk.co.oliwali.HawkEye.listeners.MonitorEntityListener;
import uk.co.oliwali.HawkEye.listeners.MonitorPlayerListener;
import uk.co.oliwali.HawkEye.listeners.ToolBlockListener;
import uk.co.oliwali.HawkEye.listeners.ToolPlayerListener;
import uk.co.oliwali.HawkEye.util.Config;
import uk.co.oliwali.HawkEye.util.Permission;
import uk.co.oliwali.HawkEye.util.Util;

public class HawkEye extends JavaPlugin {
	
	public String name;
	public String version;
	public Config config;
	public static Server server;
	public MonitorBlockListener monitorBlockListener = new MonitorBlockListener(this);
	public MonitorEntityListener monitorEntityListener = new MonitorEntityListener(this);
	public MonitorPlayerListener monitorPlayerListener = new MonitorPlayerListener(this);
	public ToolBlockListener toolBlockListener = new ToolBlockListener();
	public ToolPlayerListener toolPlayerListener = new ToolPlayerListener();
	public static List<BaseCommand> commands = new ArrayList<BaseCommand>();
	public WorldEditPlugin worldEdit = null;
	public static ContainerAccessManager containerManager;
	
	/**
	 * Safely shuts down HawkEye
	 */
	public void onDisable() {
		DataManager.close();
		Util.info("Version " + version + " disabled!");
	}
	
	/**
	 * Starts up HawkEye initiation process
	 */
	public void onEnable() {
		
		Util.info("Starting HawkEye initiation process...");

		//Set up config and permissions
        PluginManager pm = getServer().getPluginManager();
		server = getServer();
		name = this.getDescription().getName();
        version = this.getDescription().getVersion();
        config = new Config(this);
        new Permission(this);
        
        datalogCheck(pm);
        
        versionCheck();
        
        new SessionManager();
        
        //Initiate database connection
        try {
			new DataManager(this);
		} catch (Exception e) {
			Util.severe("Error initiating HawkEye database connection, disabling plugin");
			pm.disablePlugin(this);
			return;
		}
		
		checkDependencies(pm);
		
		containerManager = new ContainerAccessManager();
        
	    registerListeners(pm);
        
	    registerCommands();
        
        Util.info("Version " + version + " enabled!");
        
	}
	
	/**
	 * Checks if HawkEye needs to update config files from existing DataLog installation
	 * @param pm PluginManager
	 */
	private void datalogCheck(PluginManager pm) {
		
        //Check if we need to update from DataLog
        Plugin dl = pm.getPlugin("DataLog");
        if (dl != null) {
        	Util.warning("DataLog found, disabling it. Please remove DataLog.jar!");
        	Util.info("Importing DataLog configuration");
        	Config.importOldConfig(dl.getConfiguration());
        	config = new Config(this);
        	pm.disablePlugin(dl);
        }
        
	}
	
	/**
	 * Checks if any updates are available for HawkEye
	 * Outputs console warning if updates are needed
	 */
	private void versionCheck() {
		
		//Check if update checking enabled
		if (!Config.CheckUpdates) {
			Util.info("Update checking is disabled, this is not recommended!");
			return;
		}
		
        //Perform version check
        Util.info("Performing update check...");
        try {
        	
        	//Values
        	int updateVer;
        	int curVer;
        	int updateHot = 0;
        	int curHot = 0;
        	int updateBuild;
        	int curBuild;
        	String info;
        	
        	//Get version file
        	URLConnection yc = new URL("https://raw.github.com/oliverw92/HawkEye/master/version.txt").openConnection();
    		BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
    		
    		//Get version number
    		String updateVersion = in.readLine().replace(".", "");
    		
    		//Check for hot fixes on new version
    		if (Character.isLetter(updateVersion.charAt(updateVersion.length() - 1))) {
    			updateHot = Character.getNumericValue(updateVersion.charAt(updateVersion.length() - 1));
    			updateVer = Integer.parseInt(updateVersion.substring(0, updateVersion.length() - 2));
    		}
    		else updateVer = Integer.parseInt(updateVersion);
    		
    		//Check for hot fixes on current version
    		if (Character.isLetter(version.charAt(version.length() - 1))) {
    			curHot = Character.getNumericValue(version.charAt(version.length() - 1));
    			curVer = Integer.parseInt(version.substring(0, version.length() - 2));
    		}
    		else curVer = Integer.parseInt(version);
    		curVer = Integer.parseInt(version.replace(".", ""));
    		
    		//Extract Bukkit build from server versions
    		Pattern pattern = Pattern.compile("-b(\\d*?)jnks", Pattern.CASE_INSENSITIVE);
			Matcher matcher = pattern.matcher(server.getVersion());
			if (!matcher.find() || matcher.group(1) == null) throw new Exception();
			curBuild = Integer.parseInt(matcher.group(1));
    		updateBuild = Integer.parseInt(in.readLine());
    		
    		//Get custom info string
    		info = in.readLine();
    		
    		//Check versions
    		if (updateVer > curVer || updateVer == curVer && updateHot > curHot) {
				Util.warning("New version of HawkEye available: " + updateVersion);
    			if (updateBuild > curBuild)	Util.warning("Update recommended of CraftBukkit from build " + curBuild + " to " + updateBuild + " to ensure compatibility");
    			else Util.warning("Compatible with your current version of CraftBukkit");
    			Util.warning("New version info: " + info);
    		}
    		else Util.info("No updates available for HawkEye");
    		in.close();
    		
		} catch (Exception e) {
			Util.warning("Unable to perform update check!");
		}
	}
	
	/**
	 * Checks if required plugins are loaded
	 * @param pm PluginManager
	 */
	private void checkDependencies(PluginManager pm) {
		
        //Check if WorldEdit is loaded
        Plugin we = pm.getPlugin("WorldEdit");
        if (we != null) {
        	worldEdit = (WorldEditPlugin)we;
        	Util.info("WorldEdit found, selection rollbacks enabled");
        }
        else Util.info("WARNING! WorldEdit not found, WorldEdit selection rollbacks disabled until WorldEdit is available");
	    
	}
	
	/**
	 * Registers event listeners
	 * @param pm PluginManager
	 */
	private void registerListeners(PluginManager pm) {
		
        // Register monitor events
        if (Config.isLogged(DataType.BLOCK_BREAK)) pm.registerEvent(Type.BLOCK_BREAK, monitorBlockListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.BLOCK_PLACE)) pm.registerEvent(Type.BLOCK_PLACE, monitorBlockListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.BLOCK_BURN)) pm.registerEvent(Type.BLOCK_BURN, monitorBlockListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.LEAF_DECAY)) pm.registerEvent(Type.LEAVES_DECAY, monitorBlockListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.BLOCK_FORM)) pm.registerEvent(Type.BLOCK_FORM, monitorBlockListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.LAVA_FLOW) || Config.isLogged(DataType.WATER_FLOW)) pm.registerEvent(Type.BLOCK_FROMTO, monitorBlockListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.SIGN_PLACE)) pm.registerEvent(Type.SIGN_CHANGE, monitorBlockListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.BLOCK_FADE)) pm.registerEvent(Type.BLOCK_FADE, monitorBlockListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.COMMAND)) pm.registerEvent(Type.PLAYER_COMMAND_PREPROCESS, monitorPlayerListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.CHAT)) pm.registerEvent(Type.PLAYER_CHAT, monitorPlayerListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.JOIN)) pm.registerEvent(Type.PLAYER_JOIN, monitorPlayerListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.QUIT)) pm.registerEvent(Type.PLAYER_QUIT, monitorPlayerListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.TELEPORT)) pm.registerEvent(Type.PLAYER_TELEPORT, monitorPlayerListener, Event.Priority.Monitor, this);
        pm.registerEvent(Type.PLAYER_INTERACT, monitorPlayerListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.ITEM_DROP)) pm.registerEvent(Type.PLAYER_DROP_ITEM, monitorPlayerListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.ITEM_PICKUP)) pm.registerEvent(Type.PLAYER_PICKUP_ITEM, monitorPlayerListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.PVP_DEATH) || Config.isLogged(DataType.MOB_DEATH) || Config.isLogged(DataType.OTHER_DEATH)) pm.registerEvent(Type.ENTITY_DEATH, monitorEntityListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.EXPLOSION)) pm.registerEvent(Type.ENTITY_EXPLODE, monitorEntityListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.PAINTING_BREAK)) pm.registerEvent(Type.PAINTING_BREAK, monitorEntityListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.PAINTING_BREAK)) pm.registerEvent(Type.PAINTING_PLACE, monitorEntityListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.ENDERMAN_PICKUP)) pm.registerEvent(Type.ENDERMAN_PICKUP, monitorEntityListener, Event.Priority.Monitor, this);
        if (Config.isLogged(DataType.ENDERMAN_PLACE)) pm.registerEvent(Type.ENDERMAN_PLACE, monitorEntityListener, Event.Priority.Monitor, this);
        
        //Register tool events
        pm.registerEvent(Type.BLOCK_PLACE, toolBlockListener, Event.Priority.Highest, this);
        pm.registerEvent(Type.PLAYER_INTERACT, toolPlayerListener, Event.Priority.Highest, this);
		
	}
	
	/**
	 * Registers commands for use by the command manager
	 */
	private void registerCommands() {
		
        //Add commands
        commands.add(new HelpCommand());
        commands.add(new ToolBindCommand());
        commands.add(new ToolResetCommand());
        commands.add(new ToolCommand());
        commands.add(new SearchCommand());
        commands.add(new PageCommand());
        commands.add(new TptoCommand());
        commands.add(new HereCommand());
        commands.add(new PreviewApplyCommand());
        commands.add(new PreviewCancelCommand());
        commands.add(new PreviewCommand());
        commands.add(new RollbackCommand());
        if (worldEdit != null) commands.add(new WorldEditRollbackCommand());
        commands.add(new UndoCommand());
        
	}
	
	/**
	 * Command manager for HawkEye
	 * @param sender - {@link CommandSender}
	 * @param cmd - {@link Command}
	 * @param commandLabel - String
	 * @param args[] - String[]
	 */
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String args[]) {
		if (cmd.getName().equalsIgnoreCase("hawk")) {
			if (args.length == 0)
				args = new String[]{"help"};
			outer:
			for (BaseCommand command : commands.toArray(new BaseCommand[0])) {
				String[] cmds = command.name.split(" ");
				for (int i = 0; i < cmds.length; i++)
					if (i >= args.length || !cmds[i].equalsIgnoreCase(args[i])) continue outer;
				return command.run(this, sender, args, commandLabel);
			}
			new HelpCommand().run(this, sender, args, commandLabel);
			return true;
		}
		return false;
	}

}
