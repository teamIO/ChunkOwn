package com.codisimus.plugins.chunkown;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.painting.PaintingBreakByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

/**
 * Listens for griefing events
 * 
 * @author Codisimus
 */
public class ChunkOwnListener implements Listener {
    /* Building/Griefing Events */
    
    /**
     * Blocks can only be placed within an OwnedChunk by the Owner, a Co-Owner, or an Admin
     * 
     * @param event The BlockPlaceEvent that occurred
     */
    @EventHandler (priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!ChunkOwn.canBuild(event.getPlayer(), event.getBlock()))
            event.setCancelled(true);
    }
    
    /**
     * Blocks within an OwnedChunk can only be broken by the Owner, a Co-Owner, or an Admin
     * 
     * @param event The BlockBreakEvent that occurred
     */
    @EventHandler (priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!ChunkOwn.canBuild(event.getPlayer(), event.getBlock()))
            event.setCancelled(true);
    }

    /**
     * Signs within an OwnedChunk can only be changed by the Owner, a Co-Owner, or an Admin
     * 
     * @param event The SignChangeEvent that occurred
     */
    @EventHandler (priority = EventPriority.HIGHEST)
    public void onSignChange(SignChangeEvent event) {
        if (!ChunkOwn.canBuild(event.getPlayer(), event.getBlock()))
            event.setCancelled(true);
    }

    /**
     * Blocks within an OwnedChunk can only be ignited by the Owner, a Co-Owner, or an Admin
     * 
     * @param event The BlockIgniteEvent that occurred
     */
    @EventHandler (priority = EventPriority.HIGHEST)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!ChunkOwn.canBuild(event.getPlayer(), event.getBlock()))
            event.setCancelled(true);
    }

    /**
     * Eggs within an OwnedChunk can only be hatched by the Owner, a Co-Owner, or an Admin
     * 
     * @param event The BlockIgniteEvent that occurred
     */
    @EventHandler (priority = EventPriority.HIGHEST)
    public void onEggThrow(PlayerEggThrowEvent event) {
        Player player = event.getPlayer();
        if (!ChunkOwn.canBuild(player, player.getTargetBlock(null, 10)))
            event.setHatching(false);
    }
    
    /**
     * Buckets can only be emptied within an OwnedChunk by the Owner, a Co-Owner, or an Admin
     * 
     * @param event The PlayerBucketEmptyEvent that occurred
     */
    @EventHandler (priority = EventPriority.HIGHEST)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!ChunkOwn.canBuild(event.getPlayer(), event.getBlockClicked().getRelative(event.getBlockFace())))
            event.setCancelled(true);
    }

    /**
     * Buckets can only be filled within an OwnedChunk by the Owner, a Co-Owner, or an Admin
     * 
     * @param event The PlayerBucketFillEvent that occurred
     */
    @EventHandler (priority = EventPriority.HIGHEST)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        if (!ChunkOwn.canBuild(event.getPlayer(), event.getBlockClicked()))
            event.setCancelled(true);
    }
    
    /**
     * Paintings can only be broken within an OwnedChunk by the Owner, a Co-Owner, or an Admin
     * 
     * @param event The PaintingBreakByEntityEvent that occurred
     */
    @EventHandler (priority = EventPriority.HIGHEST)
    public void onPaintingBreak(PaintingBreakByEntityEvent event) {
        Player player = null;
        Entity entity = event.getRemover();
        if (entity instanceof Player)
            player = (Player)entity;
        
        if (!ChunkOwn.canBuild(player, event.getPainting().getLocation().getBlock()))
            event.setCancelled(true);
    }
    
    /**
     * Vehicles within an OwnedChunk can only be damaged by the Owner, a Co-Owner, or an Admin
     * 
     * @param event The VehicleDamageEvent that occurred
     */
    @EventHandler (priority = EventPriority.HIGHEST)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player player = null;
        Entity entity = event.getAttacker();
        if (entity instanceof Player)
            player = (Player)entity;
        
        if (!ChunkOwn.canBuild(player, event.getVehicle().getLocation().getBlock()))
            event.setCancelled(true);
    }

    /**
     * Vehicles within an OwnedChunk can only be destroyed by the Owner, a Co-Owner, or an Admin
     * 
     * @param event The VehicleDestroyEvent that occurred
     */
    @EventHandler (priority = EventPriority.HIGHEST)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Player player = null;
        Entity entity = event.getAttacker();
        if (entity instanceof Player)
            player = (Player)entity;
        
        if (!ChunkOwn.canBuild(player, event.getVehicle().getLocation().getBlock()))
            event.setCancelled(true);
    }
    
    
    /* Monitor Events */
    
    /**
     * Removes a preview Block if it is damaged
     * 
     * @param event The BlockDamageEvent that occurred
     */
    @EventHandler (priority = EventPriority.MONITOR)
    public void onBlockDamage(BlockDamageEvent event) {
        ChunkOwnCommand.removeMarker(event.getBlock());
    }
    
    /**
     * Updates the last time that Players that own Chunks were seen
     * 
     * @param event The PlayerJoinEvent that occurred
     */
    @EventHandler (priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        logAsSeen(event.getPlayer().getName());
    }
    
    /**
     * Updates the last time that Players that own Chunks were seen
     * 
     * @param event The PlayerQuitEvent that occurred
     */
    @EventHandler (priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        logAsSeen(player.getName());
        ChunkOwnMovementListener.playerLeftChunk(player);
    }
    
    /**
     * Updates the last time that Players that own Chunks were seen
     * 
     * @param player The Name of the Player who was seen
     */
    private void logAsSeen(String player) {
        ChunkOwner owner = ChunkOwn.findOwner(player);
        if (owner == null)
            return;
            
        ChunkOwn.lastDaySeen.setProperty(player, String.valueOf(ChunkOwn.getDayAD()));
        ChunkOwn.saveLastSeen();
    }
}