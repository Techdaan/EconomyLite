/*
 * This file is part of EconomyLite, licensed under the MIT License (MIT). See the LICENSE file at the root of this project for more information.
 */
package io.github.flibio.economylite.commands;

import io.github.flibio.economylite.CauseFactory;
import io.github.flibio.economylite.EconomyLite;
import io.github.flibio.economylite.api.PlayerEconService;
import io.github.flibio.economylite.api.VirtualEconService;
import io.github.flibio.economylite.impl.PlayerDataService;
import io.github.flibio.economylite.impl.PlayerServiceCommon;
import io.github.flibio.utils.commands.AsyncCommand;
import io.github.flibio.utils.commands.BaseCommandExecutor;
import io.github.flibio.utils.commands.Command;
import io.github.flibio.utils.message.MessageStorage;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.command.spec.CommandSpec.Builder;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.text.Text;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@AsyncCommand
@Command(aliases = {"migrate"}, permission = "economylite.admin.migrate")
public class MigrateCommand extends BaseCommandExecutor<CommandSource> {

    private MessageStorage messageStorage = EconomyLite.getMessageStorage();
    private PlayerEconService playerService = EconomyLite.getPlayerService();
    private VirtualEconService virtualService = EconomyLite.getVirtualService();
    private Logger logger = EconomyLite.getInstance().getLogger();

    @Override
    public Builder getCommandSpecBuilder() {
        return CommandSpec.builder()
                .executor(this)
                .arguments(GenericArguments.string(Text.of("mode")), GenericArguments.optional(GenericArguments.bool(Text.of("confirm"))));
    }

    @Override
    public void run(CommandSource src, CommandContext args) {
        if (args.getOne("mode").isPresent()) {
            // Check if migration is confirmed
            if (args.getOne("confirm").isPresent() && args.<Boolean>getOne("confirm").get()) {
                // Attempt to migrate
                String mode = args.<String>getOne("mode").get();
                if (mode.equalsIgnoreCase("totaleconomy")) {
                    try {
                        // Migrate from TotalEconomy
                        String configDir = EconomyLite.getInstance().getMainDir();
                        if (!configDir.substring(configDir.length() - 1).equalsIgnoreCase("/")) {
                            configDir += "/";
                        }
                        // Check if the file exists
                        File file = new File(configDir + "totaleconomy/accounts.conf");
                        if (!file.exists()) {
                            logger.error("Could not find TotalEconomy file!");
                            throw new FileNotFoundException();
                        }
                        // Attempt to load the file
                        ConfigurationLoader<?> manager = HoconConfigurationLoader.builder().setFile(file).build();
                        ConfigurationNode root = manager.load();
                        root.getChildrenMap().keySet().forEach(raw -> {
                            if (raw instanceof String) {
                                String uuid = (String) raw;
                                root.getNode(uuid).getChildrenMap().keySet().forEach(n -> {
                                    if (n.toString().contains("balance")) {
                                        ConfigurationNode sub = root.getNode(uuid).getNode(n.toString());
                                        if (!sub.isVirtual()) {
                                            playerService.setBalance(UUID.fromString(uuid), BigDecimal.valueOf(sub.getDouble()),
                                                    EconomyLite.getEconomyService().getDefaultCurrency(), CauseFactory.create("Migration"));
                                            logger.debug(uuid.toString() + ":migrate: " + sub.getDouble());
                                        }
                                    }
                                });
                            }
                        });
                        src.sendMessage(messageStorage.getMessage("command.migrate.completed"));
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                        src.sendMessage(messageStorage.getMessage("command.migrate.fail"));
                    }
                } else if (mode.equalsIgnoreCase("tomysql")) {
                    try {
                        // Migrate from H2 to MySQL
                        if (EconomyLite.getPlayerService() instanceof PlayerDataService) {
                            // MySQL is not setup
                            src.sendMessage(messageStorage.getMessage("command.migrate.fail"));
                            return;
                        }
                        // Get current MySQL service
                        PlayerEconService s = EconomyLite.getPlayerService();
                        if (!(s instanceof PlayerServiceCommon)) {
                            src.sendMessage(messageStorage.getMessage("command.migrate.fail"));
                            return;
                        }
                        PlayerServiceCommon sqlService = (PlayerServiceCommon) s;
                        // Load the data service
                        PlayerDataService dataService = new PlayerDataService();
                        // Get Currency Data
                        Map<String, Currency> cIds = new HashMap<>();
                        EconomyLite.getCurrencyService().getCurrencies().forEach(c -> {
                            cIds.put(c.getId(), c);
                        });
                        // Insert new data
                        dataService.getAccountsMigration().forEach(r -> {
                            String[] d = r.split("%-%");
                            if (cIds.get(d[2]) != null) {
                                sqlService.setRawData(d[0], d[1], cIds.get(d[2]));
                            }
                        });
                        src.sendMessage(messageStorage.getMessage("command.migrate.completed"));
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                        src.sendMessage(messageStorage.getMessage("command.migrate.fail"));
                    }
                } else {
                    // Invalid mode
                    src.sendMessage(messageStorage.getMessage("command.migrate.nomode"));
                }
            } else {
                src.sendMessage(messageStorage.getMessage("command.migrate.confirm"));
            }
        } else {
            src.sendMessage(messageStorage.getMessage("command.migrate.nomode"));
        }
    }
}
