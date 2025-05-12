package com.eternalcode.combat.border;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;

public interface BorderService {

    void updateBorder(Player player, Location to);

    void clearBorder(Player player);

    Set<BorderPoint> getActiveBorder(Player player);

}
