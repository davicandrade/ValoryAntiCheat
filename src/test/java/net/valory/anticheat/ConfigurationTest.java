package net.valory.anticheat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

class ConfigurationTest {
  @Test
  void everyCheckBypassIsExplicitlyFalseEvenForOperators() throws Exception {
    var loader = getClass().getClassLoader();
    try (var plugin = loader.getResourceAsStream("plugin.yml");
        var checks = loader.getResourceAsStream("checks.yml")) {
      var description = new PluginDescriptionFile(plugin);
      var config =
          YamlConfiguration.loadConfiguration(
              new InputStreamReader(checks, StandardCharsets.UTF_8));
      for (String key : config.getConfigurationSection("checks").getKeys(false)) {
        String permission =
            "valoryanticheat.bypass." + key.split("_")[0].toLowerCase(java.util.Locale.ROOT);
        var value =
            description.getPermissions().stream()
                .filter(p -> p.getName().equals(permission))
                .findFirst();
        assertTrue(value.isPresent(), permission);
        assertEquals(PermissionDefault.FALSE, value.orElseThrow().getDefault(), permission);
      }
      assertFalse(
          description.getPermissions().stream()
              .filter(p -> p.getName().equals("valoryanticheat.admin"))
              .findFirst()
              .orElseThrow()
              .getChildren()
              .containsKey("valoryanticheat.bypass"));
    }
  }
}
