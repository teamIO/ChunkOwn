package com.codisimus.plugins.chunkown;

import com.codisimus.plugins.chunkown.ChunkOwner.AddOn;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Executes Player Commands
 * 
 * @author Codisimus
 */
public class ChunkOwnCommand implements CommandExecutor {
    public static String command;
    private static enum Action { HELP, BUY, SELL, LIST, INFO, COOWNER, CLEAR, PREVIEW }
    public static long cooldown;
    public static int cornerID;
    private Map<String, Long> playerLastPreview;
    private static LinkedList<Block> previewBlocks = new LinkedList<Block>();
    
    public ChunkOwnCommand() {
        playerLastPreview = new HashMap<String, Long>();
    }
    
    /**
     * Listens for ChunkOwn commands to execute them
     * 
     * @param sender The CommandSender who may not be a Player
     * @param command The command that was executed
     * @param alias The alias that the sender used
     * @param args The arguments for the command
     * @return true always
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        //Cancel if the command is not from a Player
        if (!(sender instanceof Player))
            return true;
        
        Player player = (Player)sender;
        
        //Cancel if the Player is in a disabled World
        if (!ChunkOwn.enabledInWorld(player.getWorld())) {
            player.sendMessage("ChunkOwn is disabled in your current World");
            return true;
        }
        
        //Display help page if the Player did not add any arguments
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        
        Action action;
        
        try {
            action = Action.valueOf(args[0].toUpperCase());
        }
        catch (IllegalArgumentException notEnum) {
            sendHelp(player);
            return true;
        }
        
        //Execute the correct command
        switch (action) {
            case BUY:
                switch (args.length) {
                    case 1: buy(player); return true;
                        
                    case 2:
                        try {
                            buyAddOn(player, AddOn.valueOf(args[1].toUpperCase()));
                        }
                        catch (IllegalArgumentException notAddOn) {
                            player.sendMessage(args[1]+" is not a valid Add-on");
                        }
                        return true;
                        
                    default: sendHelp(player); return true;
                } 
                
            case SELL:
                switch (args.length) {
                    case 1: sell(player); return true;
                        
                    case 2:
                        try {
                            sellAddOn(player, AddOn.valueOf(args[1].toUpperCase()));
                        }
                        catch (IllegalArgumentException notAddOn) {
                            player.sendMessage(args[1]+" is not a valid Add-on");
                        }
                        return true;
                        
                    default: sendHelp(player); return true;
                } 
                
            case LIST:
                switch (args.length) {
                    case 1: list(player); return true;
                    
                    case 2:
                        if (args[1].equals("addons"))
                            listAddOns(player);
                        else
                            sendAddOnHelp(player);
                        return true;
                    
                    default: sendHelp(player); return true;
                }
                
            case INFO: info(player); return true;
                
            case COOWNER:
                switch (args.length) {
                    case 4: chunkCoowner(player, args[2], args[1], args[3]); return true;
                        
                    case 5:
                        if (args[1].equals("all"))
                            coowner(player, args[3], args[2], args[4]);
                        else
                            sendHelp(player);
                        return true;
                        
                    default: sendHelp(player); return true;
                }
                
            case CLEAR: clear(player); return true;
                
            case PREVIEW: preview(player); return true;
            
            case HELP:
                if (args.length == 2 && args[1].equals("addons"))
                    sendAddOnHelp(player);
                else
                    sendHelp(player);
                return true;
                
            default: sendHelp(player); return true;
        }
    }
    
    /**
     * Gives ownership of the current Chunk to the Player
     * 
     * @param player The Player buying the Chunk
     */
    public static void buy(Player player) {
        //Cancel if the Player does not have permission to use the command
        if (!ChunkOwn.hasPermission(player, "own")) {
            player.sendMessage(ChunkOwnMessages.permission);
            return;
        }
        
        //Retrieve the OwnedChunk that the Player is in
        Chunk chunk = player.getLocation().getBlock().getChunk();
        OwnedChunk ownedChunk = ChunkOwn.findOwnedChunk(chunk);

        //If the owner of the OwnedChunk is not blank then the Chunk is already claimed
        if (ownedChunk != null) {
            player.sendMessage(ChunkOwnMessages.claimed);
            return;
        }
        
        String name = player.getName();
        
        //Check if the Player is limited
        int limit = ChunkOwn.getOwnLimit(player);
        if (limit != -1)
            //Cancel if the Player owns their maximum limit
            if (ChunkOwn.getChunkCounter(name) >= limit) {
                player.sendMessage(ChunkOwnMessages.limit);
                return;
            }
        
        
        //Check if a group size is required
        if (ChunkOwn.groupSize > 1) {
            //Check if the Chunk is a loner (not connected to other owned Chunks
            if (isLoner(chunk, name))
                if (!ChunkOwn.canBuyLoner(ChunkOwn.getOwnedChunks(name))) {
                    player.sendMessage(ChunkOwnMessages.groupLand.replace("<MinimumGroupSize>", String.valueOf(ChunkOwn.groupSize)));
                    return;
                }
        }
        
        //Charge the Player only if they don't have the 'chunkown.free' node
        if (ChunkOwn.hasPermission(player, "free"))
            player.sendMessage(ChunkOwnMessages.buyFree);
        else if(!Econ.buy(player))
            return;
        
        markCorners(chunk);
        ChunkOwn.addOwnedChunk(new OwnedChunk(chunk, name));
    }
    
    /**
     * Gives the Player the Given Add-on
     * 
     * @param player The Player buying the Chunk
     */
    public static void buyAddOn(Player player, AddOn addOn) {
        //Cancel if the Player does not have permission to buy the Add-on
        if (!ChunkOwn.hasPermission(player, addOn)) {
            player.sendMessage(ChunkOwnMessages.permission);
            return;
        }
        
        //Retrieve the ChunkOwner for the Player
        ChunkOwner owner = ChunkOwn.getOwner(player.getName());
        
        //Cancel if the Player already has the Add-on
        if (owner.hasAddOn(addOn)) {
            player.sendMessage("You already have that Add-on enabled");
            return;
        }
        
        //Cancel if the Player could not afford the transaction
        if(!Econ.charge(player, Econ.getBuyPrice(addOn)))
            return;
        
        owner.setAddOn(addOn, true);
    }
    
    /**
     * Removes the given Add-on from the Player's ChunkOwner
     * 
     * @param player The Player buying the Chunk
     */
    public static void sellAddOn(Player player, AddOn addOn) {
        //Retrieve the ChunkOwner for the Player
        ChunkOwner owner = ChunkOwn.getOwner(player.getName());
        
        //Cancel if the Player already has the Add-on
        if (!owner.hasAddOn(addOn)) {
            player.sendMessage("You already have that Add-on disabled");
            return;
        }
        
        
        Econ.refund(player, Econ.getSellPrice(addOn));
        owner.setAddOn(addOn, true);
    }
    
    /**
     * Previews the boundaries of the current Chunk
     * 
     * @param player The Player previewing the Chunk
     */
    public void preview(Player player) {
        //Cancel if the Player does not have permission to use the command
        if (!ChunkOwn.hasPermission(player, "preview")) {
            player.sendMessage(ChunkOwnMessages.permission);
            return;
        }
        
        // If not admin, enforce a cooldown period for this command
        if (!ChunkOwn.hasPermission(player, "admin") && playerLastPreview.containsKey(player.getName())) {
            long lastPreviewTime = playerLastPreview.get(player.getName());
            long currentTime = System.currentTimeMillis() / 1000;
            long delta = currentTime - lastPreviewTime;
            
            if (delta < cooldown) {
                player.sendMessage("You must wait " + (cooldown - delta) + " seconds before previewing another chunk.");
                return;
            }
        }
        
        //Retrieve the OwnedChunk that the Player is in
        Chunk chunk = player.getLocation().getBlock().getChunk();
        OwnedChunk ownedChunk = ChunkOwn.findOwnedChunk(chunk);

        //If the owner of the OwnedChunk is not blank then the Chunk is already claimed
        if (ownedChunk != null) {
            player.sendMessage(ChunkOwnMessages.claimed);
            return;
        }

        String name = player.getName();
        
        //Check if the Player is limited
        int limit = ChunkOwn.getOwnLimit(player);
        if (limit != -1)
            //Cancel if the Player owns their maximum limit
            if (ChunkOwn.getChunkCounter(name) >= limit) {
                player.sendMessage(ChunkOwnMessages.limit);
                return;
            }
        
        //Check if a group size is required
        if (ChunkOwn.groupSize > 1) {
            //Check if the Chunk is a loner (not connected to other owned Chunks
            if (isLoner(chunk, name))
                if (!ChunkOwn.canBuyLoner(ChunkOwn.getOwnedChunks(name))) {
                    player.sendMessage(ChunkOwnMessages.groupLand.replace("<MinimumGroupSize>", String.valueOf(ChunkOwn.groupSize)));
                    return;
                }
        }
        
        markCorners(chunk);
        playerLastPreview.put(player.getName(), System.currentTimeMillis() / 1000);
    }
    
    /**
     * Removes ownership of the current Chunk from the Player
     * 
     * @param player The Player selling the Chunk
     */
    public static void sell(Player player) {
        //Cancel if the Player does not have permission to use the command
        if (!ChunkOwn.hasPermission(player, "own")) {
            player.sendMessage(ChunkOwnMessages.permission);
            return;
        }
        
        //Retrieve the OwnedChunk that the Player is in
        Chunk chunk = player.getLocation().getBlock().getChunk();
        OwnedChunk ownedChunk = ChunkOwn.findOwnedChunk(chunk);

        //Cancel if the Chunk is not owned
        if (ownedChunk == null) {
            player.sendMessage(ChunkOwnMessages.doNotOwn);
            return;
        }
        
        //Cancel if the OwnedChunk is owned by someone else
        if (!ownedChunk.owner.name.equals(player.getName()))
            if (ChunkOwn.hasPermission(player, "admin"))
                Econ.sell(player, ownedChunk.owner.name);
            else {
                player.sendMessage(ChunkOwnMessages.doNotOwn);
                return;
            }
        else
            Econ.sell(player);
        
        ChunkOwn.removeOwnedChunk(chunk);
    }
    
    /**
     * Display to the Player all of the Chunks that they own
     * 
     * @param player The Player requesting the list
     */
    public static void list(Player player) {
        //Cancel if the Player does not have permission to use the command
        if (!ChunkOwn.hasPermission(player, "own")) {
            player.sendMessage(ChunkOwnMessages.permission);
            return;
        }
        
        String name = player.getName();
        
        player.sendMessage("Number of Chunks owned: "+ChunkOwn.getChunkCounter(name));

        //Retrieve the ownLimit to display to the Player
        int ownLimit = ChunkOwn.getOwnLimit(player);
        if (ownLimit > -1)
            player.sendMessage("Total amount you may own: "+ownLimit);
        
        for (OwnedChunk ownedChunk: ChunkOwn.getOwnedChunks())
            if (ownedChunk.owner.name.equals(name))
                player.sendMessage(ownedChunk.toString());
    }
    
    /**
     * Display to the Player all of the Add-ons that they own
     * 
     * @param player The Player requesting the list
     */
    public static void listAddOns(Player player) {
        ChunkOwner owner = ChunkOwn.getOwner(player.getName());
        
        String list = "Enabled Add-ons: ";
        if (Econ.blockPvP != -2 && owner.blockPvP)
            list = list.concat("BlockPvP ,");
        if (Econ.blockPvE != -2 && owner.blockPvE)
            list = list.concat("BlockPvE ,");
        if (Econ.blockExplosions != -2 && owner.blockExplosions)
            list = list.concat("BlockExplosions ,");
        if (Econ.lockChests != -2 && owner.lockChests)
            list = list.concat("LockChests ,");
        if (Econ.lockDoors != -2 && owner.lockDoors)
            list = list.concat("LockDoors ,");
        if (Econ.disableButtons != -2 && owner.disableButtons)
            list = list.concat("DisableButtons ,");
        if (Econ.disablePistons != -2 && owner.disablePistons)
            list = list.concat("DisablePistons ,");
        if (Econ.alarm != -2 && owner.alarm)
            list = list.concat("AlarmSystem ,");
        if (Econ.heal != -2 && owner.heal)
            list = list.concat("Heal ,");
        if (Econ.feed != -2 && owner.feed)
            list = list.concat("Feed ,");
        if (Econ.notify != -2 && owner.notify)
            list = list.concat("Notify ,");
        
        player.sendMessage(list.substring(0, list.length()-2));
        
        if (ChunkOwn.defaultAutoOwnBlock != -1) {
            Material material = Material.getMaterial(owner.autoOwnBlock);
            player.sendMessage("You will automattically buy a Chunk if you place a "+material.toString()+" in it");
        }
    }
    
    /**
     * Display to the Player the info of the current Chunk
     * Info displayed is the Location of the Chunk and the current CoOwners
     * 
     * @param player The Player requesting the info
     */
    public static void info(Player player) {
        //Cancel if the Player does not have permission to use the command
        if (!ChunkOwn.hasPermission(player, "info")) {
            player.sendMessage(ChunkOwnMessages.permission);
            return;
        }
        
        //Retrieve the OwnedChunk that the Player is in
        Chunk chunk = player.getLocation().getBlock().getChunk();
        OwnedChunk ownedChunk = ChunkOwn.findOwnedChunk(chunk);

        //Cancel if the OwnedChunk does not exist
        if (ownedChunk == null) {
            player.sendMessage(ChunkOwnMessages.unclaimed);
            return;
        }

        //Display the world and x/y-coordinates of the center of the OwnedChunk to the Player
        player.sendMessage(ownedChunk.toString()+" belongs to "+ownedChunk.owner);

        //Display CoOwners of OwnedChunk to Player
        String coOwners = "CoOwners:  ";
        for (String coOwner: ownedChunk.coOwners)
            coOwners = coOwners.concat(coOwner.concat(", "));
        player.sendMessage(coOwners.substring(0, coOwners.length() - 2));

        //Display CoOwner Groups of OwnedChunk to Player
        String groups = "CoOwner Groups:  ";
        for (String group: ownedChunk.groups)
            groups = groups.concat(group.concat(", "));
        player.sendMessage(groups.substring(0, groups.length() - 2));
    }
    
    /**
     * Manages Co-Ownership of the given Chunk if the Player is the Owner
     * 
     * @param player The given Player who may be the Owner
     * @param type The given type: 'player' or 'group'
     * @param action The given action: 'add' or 'remove'
     * @param coOwner The given Co-Owner
     */
    public static void chunkCoowner(Player player, String type, String action, String coOwner) {
        //Cancel if the Player does not have permission to use the command
        if (!ChunkOwn.hasPermission(player, "coowner")) {
            player.sendMessage(ChunkOwnMessages.permission);
            return;
        }
        
        //Retrieve the OwnedChunk that the Player is in
        Chunk chunk = player.getLocation().getBlock().getChunk();
        OwnedChunk ownedChunk = ChunkOwn.findOwnedChunk(chunk);

        //Cancel if the OwnedChunk does not exist
        if (ownedChunk == null) {
            player.sendMessage(ChunkOwnMessages.unclaimed);
            return;
        }

        //Cancel if the OwnedChunk is owned by someone else
        if (!ownedChunk.owner.name.equals(player.getName())) {
            player.sendMessage(ChunkOwnMessages.doNotOwn);
            return;
        }

        //Determine the command to execute
        if (type.equals("player"))
            if (action.equals("add")) {
                //Cancel if the Player is already a CoOwner
                if (ownedChunk.coOwners.contains(coOwner)) {
                    player.sendMessage(coOwner+" is already a CoOwner");
                    return;
                }
                
                ownedChunk.coOwners.add(coOwner);
                player.sendMessage(coOwner+" added as a CoOwner");
            }
            else if (action.equals("remove"))
                ownedChunk.coOwners.remove(coOwner);
            else {
                sendHelp(player);
                return;
            }
        else if (type.equals("group"))
            if (action.equals("add")) {
                //Cancel if the Group is already a CoOwner
                if (ownedChunk.groups.contains(coOwner)) {
                    player.sendMessage(coOwner+" is already a CoOwner");
                    return;
                }
                
                ownedChunk.groups.add(coOwner);
                player.sendMessage(coOwner+" added as a CoOwner");
            }
            else if (action.equals("remove"))
                ownedChunk.groups.remove(coOwner);
            else {
                sendHelp(player);
                return;
            }
        else {
            sendHelp(player);
            return;
        }
        
        ChunkOwn.save(chunk.getWorld());
    }
    
    /**
     * Manages Co-Ownership of the ChunkOwner of the Player
     * 
     * @param player The given Player who may be the Owner
     * @param type The given type: 'player' or 'group'
     * @param action The given action: 'add' or 'remove'
     * @param coOwner The given Co-Owner
     */
    public static void coowner(Player player, String type, String action, String coOwner) {
        //Cancel if the Player does not have permission to use the command
        if (!ChunkOwn.hasPermission(player, "coowner")) {
            player.sendMessage(ChunkOwnMessages.permission);
            return;
        }
        
        ChunkOwner owner = ChunkOwn.getOwner(player.getName());

        //Determine the command to execute
        if (type.equals("player"))
            if (action.equals("add")) {
                //Cancel if the Player is already a CoOwner
                if (owner.coOwners.contains(coOwner)) {
                    player.sendMessage(coOwner+" is already a CoOwner");
                    return;
                }
                
                owner.coOwners.add(coOwner);
                player.sendMessage(coOwner+" added as a CoOwner");
            }
            else if (action.equals("remove"))
                owner.coOwners.remove(coOwner);
            else {
                sendHelp(player);
                return;
            }
        else if (type.equals("group"))
            if (action.equals("add")) {
                //Cancel if the Group is already a CoOwner
                if (owner.groups.contains(coOwner)) {
                    player.sendMessage(coOwner+" is already a CoOwner");
                    return;
                }
                
                owner.groups.add(coOwner);
                player.sendMessage(coOwner+" added as a CoOwner");
            }
            else if (action.equals("remove"))
                owner.groups.remove(coOwner);
            else {
                sendHelp(player);
                return;
            }
        else {
            sendHelp(player);
            return;
        }
        
        owner.save();
    }
    
    /**
     * Removes all the Chunks that are owned by the given Player
     * A Chunk is owned buy a Player if the owner field is the Player's name
     * 
     * @param player The given Player
     */
    public static void clear(Player player) {
        clear(player, player.getName());
    }
    
    /**
     * Removes all the Chunks that are owned by the given Player
     * A Chunk is owned buy a Player if the owner field is the Player's name
     * 
     * @param player The name of the Player
     */
    public static void clear(String player) {
        clear(null, player);
    }
    
    /**
     * Removes all the Chunks that are owned by the given Player
     * A Chunk is owned buy a Player if the owner field is the Player's name
     * 
     * @param player The given Player
     */
    private static void clear(Player player, String name) {
        Iterator <OwnedChunk> itr = ChunkOwn.getOwnedChunks().iterator();
        OwnedChunk ownedChunk;
        
        while (itr.hasNext()) {
            ownedChunk = itr.next();
            
            //Sell the Chunk if it is owned by the given Player
            if (ownedChunk.owner.name.equals(name)) {
                if (player == null)
                    Econ.sell(name);
                else
                    Econ.sell(player);

                ChunkOwn.removeOwnedChunk(ownedChunk);
            }
        }
        
        ChunkOwn.saveAll();
    }
    
    /**
     * Displays the ChunkOwn Help Page to the given Player
     *
     * @param Player The Player needing help
     */
    public static void sendHelp(Player player) {
        player.sendMessage("§e     ChunkOwn Help Page:");
        player.sendMessage("§2/"+command+" help addons§b Display the Add-on Help Page");
        player.sendMessage("§2/"+command+" buy§b Purchase the current chunk for "+Econ.format(Econ.getBuyPrice(player.getName())));
        player.sendMessage("§2/"+command+" sell§b Sell the current chunk for "+Econ.format(Econ.getSellPrice(player.getName())));
        player.sendMessage("§2/"+command+" preview§b Preview the current chunk's boundaries");
        player.sendMessage("§2/"+command+" list§b List locations of owned Chunks");
        player.sendMessage("§2/"+command+" info§b List Owner and CoOwners of current Chunk");
        player.sendMessage("§2/"+command+" clear§b Sell all owned Chunks");
        player.sendMessage("§2/"+command+" coowner [Action] [Type] [Name]§b Co-Owner for current Chunk");
        player.sendMessage("§2/"+command+" coowner all [Action] [Type] [Name]§b Co-Owner for all Chunks");
        player.sendMessage("§bAction = 'add' or 'remove'");
        player.sendMessage("§bType = 'player' or 'group'");
        player.sendMessage("§bName = The group name or the Player's name");
    }
    
    /**
     * Displays the Add-on Help Page to the given Player
     *
     * @param Player The Player needing help
     */
    public static void sendAddOnHelp(Player player) {
        player.sendMessage("§e     Add-on Help Page:");
        player.sendMessage("§2Add-ons apply to all Chunks that you own");
        player.sendMessage("§2/"+command+" list addons§b List your current add-ons");
        
        //Display available Add-ons
        if (ChunkOwn.hasPermission(player, AddOn.BLOCKPVP))
            player.sendMessage("§2/"+command+" buy blockpvp§b No damage from Players: "+Econ.format(Econ.getBuyPrice(AddOn.BLOCKPVP)));
        if (ChunkOwn.hasPermission(player, AddOn.BLOCKPVE))
            player.sendMessage("§2/"+command+" buy blockpve§b No damage from Mobs: "+Econ.format(Econ.getBuyPrice(AddOn.BLOCKPVE)));
        if (ChunkOwn.hasPermission(player, AddOn.BLOCKEXPLOSIONS))
            player.sendMessage("§2/"+command+" buy blockexplosions§b No TNT/Creeper griefing: "+Econ.format(Econ.getBuyPrice(AddOn.BLOCKEXPLOSIONS)));
        if (ChunkOwn.hasPermission(player, AddOn.LOCKCHESTS))
            player.sendMessage("§2/"+command+" buy lockchests§b Players can't open Chests/Furnaces/Dispensers: "+Econ.format(Econ.getBuyPrice(AddOn.LOCKCHESTS)));
        if (ChunkOwn.hasPermission(player, AddOn.LOCKDOORS))
            player.sendMessage("§2/"+command+" buy lockdoors§b Players can't open Doors: "+Econ.format(Econ.getBuyPrice(AddOn.LOCKDOORS)));
        if (ChunkOwn.hasPermission(player, AddOn.DISABLEBUTTONS))
            player.sendMessage("§2/"+command+" buy disablebuttons§b Other Players can't use Buttons/Levers/Plates: "+Econ.format(Econ.getBuyPrice(AddOn.DISABLEBUTTONS)));
        if (ChunkOwn.hasPermission(player, AddOn.DISABLEPISTONS))
            player.sendMessage("§2/"+command+" buy disablepistons§b Pistons will no longer function: "+Econ.format(Econ.getBuyPrice(AddOn.DISABLEPISTONS)));
        if (ChunkOwn.hasPermission(player, AddOn.ALARM))
            player.sendMessage("§2/"+command+" buy alarm§b Be alerted when a Player enters your land: "+Econ.format(Econ.getBuyPrice(AddOn.ALARM)));
        if (ChunkOwn.hasPermission(player, AddOn.HEAL))
            player.sendMessage("§2/"+command+" buy heal§b Players gain half a heart every "+ChunkOwnMovementListener.rate+" seconds: "+Econ.format(Econ.getBuyPrice(AddOn.HEAL)));
        if (ChunkOwn.hasPermission(player, AddOn.FEED))
            player.sendMessage("§2/"+command+" buy feed§b Players gain half a food every "+ChunkOwnMovementListener.rate+" seconds: "+Econ.format(Econ.getBuyPrice(AddOn.FEED)));
        if (ChunkOwn.hasPermission(player, AddOn.NOTIFY))
            player.sendMessage("§2/"+command+" buy notify§b Be notified when you enter owned land: "+Econ.format(Econ.getBuyPrice(AddOn.NOTIFY)));
        
        player.sendMessage("§2/"+command+" sell [addon]§b Sell an addon for "+Econ.moneyBack+"% of its buy price");
    }
    
    /**
     * Places Blocks of a predetermined type just above the highest Block at each corner of the given Chunk
     *
     * @param chunk The given Chunk
     */
    public static void markCorners(Chunk chunk) {
        for (int x = 0; x <= 15; x = x + 15)
            for (int z = 0; z <= 15; z = z + 15) {
                int y = chunk.getWorld().getMaxHeight() - 2;
                
                Block block = chunk.getBlock(x, y, z);
                while (y >= 0) {
                    //Do not stack cornerID Blocks
                    if (block.getTypeId() == cornerID)
                        break;
                    
                    switch (block.getType()) {
                        case LEAVES: //Fall through
                        case AIR: y--; break;
                        
                        case SAPLING: //Fall through
                        case LONG_GRASS: //Fall through
                        case DEAD_BUSH: //Fall through
                        case YELLOW_FLOWER: //Fall through
                        case RED_ROSE: //Fall through
                        case BROWN_MUSHROOM: //Fall through
                        case RED_MUSHROOM: //Fall through
                        case SNOW:
                            block.setTypeId(cornerID);
                            y = -1;
                            break;
                        
                        case BED_BLOCK: //Fall through
                        case POWERED_RAIL: //Fall through
                        case DETECTOR_RAIL: //Fall through
                        case RAILS: //Fall through
                        case STONE_PLATE: //Fall through
                        case WOOD_PLATE:
                            block.getRelative(0, 2, 0).setTypeId(cornerID);
                            y = -1;
                            break;
                        
                        default:
                            block.getRelative(0, 1, 0).setTypeId(cornerID);
                            y = -1;
                            break;
                    }
                    
                    block = block.getRelative(0, -1, 0);
                }
                
                scheduleCooldown(block);
            }
    }
    
    /**
     * Schedules the given preview Block to be removed after the cooldown
     * 
     * @param block The given preview Block
     */
    private static void scheduleCooldown(final Block block) {
        previewBlocks.add(block);
        
        ChunkOwn.server.getScheduler().scheduleSyncDelayedTask(ChunkOwn.plugin, new Runnable() {
            @Override
            public void run() {
                removeMarker(block);
            }
        }, cooldown);
    }
    
    /**
     * Removes the given preview Block
     * 
     * @param block The given preview Block
     */
    public static void removeMarker(Block block) {
        if (previewBlocks.contains(block)) {
            block.setTypeId(1);
            previewBlocks.remove(block);
        }
    }
    
    /**
     * Returns true if there are no neighboring Chunks with the given Owner
     * 
     * @param chunk The Chunk that may be a Loner
     * @param player The Player who may own neighboring Chunks
     * @return True if there are no neighboring Chunks with the given Owner
     */
    private static boolean isLoner(Chunk chunk, String player) {
        String world = chunk.getWorld().getName();
        int x = chunk.getX();
        int z = chunk.getZ();
        
        OwnedChunk ownedChunk = ChunkOwn.findOwnedChunk(world, x, z + 1);
        if (ownedChunk != null && ownedChunk.owner.name.equals(player))
            return true;
        
        ownedChunk = ChunkOwn.findOwnedChunk(world, x, z - 1);
        if (ownedChunk != null && ownedChunk.owner.name.equals(player))
            return true;
        
        ownedChunk = ChunkOwn.findOwnedChunk(world, x + 1, z);
        if (ownedChunk != null && ownedChunk.owner.name.equals(player))
            return true;
        
        ownedChunk = ChunkOwn.findOwnedChunk(world, x - 1, z);
        return ownedChunk != null && ownedChunk.owner.name.equals(player);
    }
}