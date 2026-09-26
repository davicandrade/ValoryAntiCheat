# ValoryAntiCheat

Anti-cheat orientado a pacotes para Paper 1.21.8, construído com violações mensuráveis, revisão da staff e evidências antes de qualquer punição.

## Cobertura de detecções

- Movimento: velocidade, voo, air-jump, step, phase, timer, movimento inválido, no-fall e spider.
- Combate: alcance, hitbox, autoclicker, multi-aura e análise de padrão de mira.
- Blocos/ações: place e break impossíveis, scaffold e fast-place.
- Protocolo: validação de pacotes inválidos e malformados.

O VAC processa pacotes por filas limitadas, coleta o estado Bukkit na thread do servidor, aplica isenções para mecânicas legítimas, agrega risco e armazena evidências em SQLite.

## Dependências

- **Obrigatórias:** Paper 1.21.8 e PacketEvents.
- **Opcionais:** ValoryPunish, TAB, LuckPerms, ViaVersion/ViaBackwards, Floodgate e Geyser.

A integração com o ValoryPunish é opcional e carregada por reflexão; o VAC inicia normalmente sem ele.

## Comandos

`/vac alerts`, `/vac profile`, `/vac violations`, `/vac logs`, `/vac debug`, `/vac status`, `/vac checks`, `/vac performance`, `/vac reload`.

## Compilação

Requer JDK 21 e Maven 3.9 ou superior.

```bash
mvn clean package
```

O artefato de lançamento é `target/ValoryAntiCheat-1.0.0.jar`.

## Implantação segura

As punições automáticas ficam desativadas por padrão. Execute o plugin em modo de observação, valide alertas e evidências com tráfego real e calibre `checks.yml` para as mecânicas do servidor antes de habilitar setbacks ou punições automáticas configuradas em `punishments.yml`.

## Verificação

A suíte Maven executa 47 testes unitários e de simulação para checks, geometria, predição de movimento, risco, configuração, ciclo de vida e persistência de evidências SQLite.
