/*
 * PlayCrates
 *
 * Copyright (c) 2022 M Botsko (viveleroi)
 *                    Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package network.darkhelmet.playcrates.commands;

import com.google.inject.Inject;

import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.BaseCommand;
import dev.triumphteam.cmd.core.annotation.Command;
import dev.triumphteam.cmd.core.annotation.Join;
import dev.triumphteam.cmd.core.annotation.SubCommand;
import dev.triumphteam.cmd.core.annotation.Suggestion;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import network.darkhelmet.playcrates.services.configuration.ConfigurationService;
import network.darkhelmet.playcrates.services.crates.Crate;
import network.darkhelmet.playcrates.services.crates.CrateService;
import network.darkhelmet.playcrates.services.gui.GuiService;
import network.darkhelmet.playcrates.services.messages.MessageService;
import network.darkhelmet.playcrates.services.translation.TranslationKey;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Command(value = "playcrates", alias = {"pc", "crates"})
public class CrateCommand extends BaseCommand {
    /**
     * The message service.
     */
    private final ConfigurationService configurationService;

    /**
     * The crate service.
     */
    private final CrateService crateService;

    /**
     * The GUI service.
     */
    private final GuiService guiService;

    /**
     * The message service.
     */
    private final MessageService messageService;

    /**
     * Cache a list of materials we consider transparent.
     */
    private final Set<Material> transparent = new HashSet<>();

    /**
     * Construct the crate command.
     *
     * @param configurationService The configuration service
     * @param crateService The crate service
     * @param guiService The GUI service
     * @param messageService The message service
     */
    @Inject
    public CrateCommand(
            ConfigurationService configurationService,
            CrateService crateService,
            GuiService guiService,
            MessageService messageService) {
        this.configurationService = configurationService;
        this.crateService = crateService;
        this.guiService = guiService;
        this.messageService = messageService;

        transparent.add(Material.AIR);
        transparent.add(Material.LAVA);
        transparent.add(Material.SNOW);
        transparent.add(Material.WATER);
    }

    /**
     * Lookup a crate using either an identifier (via command) or a block (player look).
     *
     * @param player The player
     * @param crateId The identifier
     * @return The crate, if any
     */
    private Optional<Crate> crateFromIdOrTarget(Player player, String crateId) {
        Crate crate = null;

        if (crateId != null) {
            Optional<Crate> crateOptional = crateService.crate(crateId);
            if (crateOptional.isPresent()) {
                crate = crateOptional.get();
            }
        } else {
            Block block = player.getTargetBlock(transparent, 5);
            Optional<Crate> crateOptional = crateService.crate(block.getLocation());
            if (crateOptional.isPresent()) {
                crate = crateOptional.get();
            }
        }

        return Optional.ofNullable(crate);
    }

    /**
     * Run the create command.
     *
     * @param player The player
     * @param crateId The crate identifier
     * @param title The crate title
     */
    @SubCommand("addcrate")
    @Permission("playcrates.admin")
    public void onCreate(final Player player, String crateId, @Join(" ") String title) {
        if (crateService.crate(crateId).isPresent()) {
            messageService.error(player, new TranslationKey("error-crate-exists"));
            return;
        }

        crateService.createCrate(crateId, title);
        configurationService.saveAll();

        messageService.crateCreated(player);
    }

    /**
     * Run the addreward command.
     *
     * @param player The player
     * @param crateId The crate identifier
     */
    @SubCommand("addreward")
    @Permission("playcrates.admin")
    public void onAddReward(final Player player,
            @dev.triumphteam.cmd.core.annotation.Optional @Suggestion("crates") final String crateId) {
        Optional<Crate> crateOptional = crateFromIdOrTarget(player, crateId);
        if (crateOptional.isEmpty()) {
            messageService.error(player, new TranslationKey("error-invalid-crate"));
            return;
        }

        ItemStack itemStack = player.getInventory().getItemInMainHand();
        crateOptional.get().addReward(itemStack);
        configurationService.saveAll();

        messageService.rewardAdded(player);
    }

    /**
     * Run the addloc command.
     *
     * @param player The player
     * @param crateId The crate identifier
     */
    @SubCommand("addloc")
    @Permission("playcrates.admin")
    public void onAddLocation(final Player player, final String crateId) {
        Block block = player.getTargetBlock(transparent, 5);
        if (block.getType().equals(Material.AIR)) {
            messageService.error(player, new TranslationKey("error-invalid-crate-block"));
            return;
        }

        Optional<Crate> crateOptional = crateService.crate(crateId);
        if (crateOptional.isEmpty()) {
            messageService.error(player, new TranslationKey("error-invalid-crate-id"));
            return;
        }

        crateOptional.get().addLocation(block.getLocation());
        configurationService.saveAll();

        messageService.locationAdded(player);
    }

    /**
     * Run the givekey command.
     *
     * @param player The player
     * @param crateId The crate identifier
     */
    @SubCommand("givekey")
    @Permission("playcrates.admin")
    public void onGiveKey(final Player player,
          @dev.triumphteam.cmd.core.annotation.Optional @Suggestion("crates") final String crateId) {
        Optional<Crate> crateOptional = crateFromIdOrTarget(player, crateId);
        if (crateOptional.isEmpty()) {
            messageService.error(player, new TranslationKey("error-invalid-crate"));
            return;
        }

        Crate crate = crateOptional.get();
        ItemStack crateKey = crate.config().key().toItemStack();

        player.getInventory().addItem(crateKey);

        messageService.crateKeyGivenSelf(player);
    }

    /**
     * Run the open command.
     *
     * @param player The player
     * @param crateId The crate identifier
     */
    @SubCommand("open")
    @Permission("playcrates.open")
    public void onOpen(final Player player,
           @dev.triumphteam.cmd.core.annotation.Optional @Suggestion("crates") final String crateId) {
        Optional<Crate> crateOptional = crateFromIdOrTarget(player, crateId);
        if (crateOptional.isEmpty()) {
            messageService.error(player, new TranslationKey("error-invalid-crate"));
            return;
        }

        Crate crate = crateOptional.get();

        ItemStack itemStack = player.getInventory().getItemInMainHand();
        if (!crate.keyMatches(itemStack)) {
            messageService.error(player, new TranslationKey("error-invalid-crate-key"));
            return;
        }

        crate.open(player);

        player.sendMessage("Opening crate!");
    }

    /**
     * Run the preview command.
     *
     * @param player The player
     * @param crateId The crate identifier
     */
    @SubCommand("preview")
    @Permission("playcrates.preview")
    public void onPreview(final Player player,
          @dev.triumphteam.cmd.core.annotation.Optional @Suggestion("crates") final String crateId) {
        Optional<Crate> crateOptional = crateFromIdOrTarget(player, crateId);
        if (crateOptional.isEmpty()) {
            messageService.error(player, new TranslationKey("error-invalid-crate"));
            return;
        }

        guiService.open(crateOptional.get(), player);
    }

    /**
     * Run the setkey command.
     *
     * @param player The player
     * @param crateId The crate identifier
     */
    @SubCommand("setkey")
    @Permission("playcrates.preview")
    public void onSetKey(final Player player,
             @dev.triumphteam.cmd.core.annotation.Optional @Suggestion("crates") final String crateId) {
        Optional<Crate> crateOptional = crateFromIdOrTarget(player, crateId);
        if (crateOptional.isEmpty()) {
            messageService.error(player, new TranslationKey("error-invalid-crate"));
            return;
        }

        ItemStack itemStack = player.getInventory().getItemInMainHand();
        crateOptional.get().createKey(itemStack);
        configurationService.saveAll();

        messageService.crateKeyCreated(player);
    }
}