package xyz.fortern.minehunt.mode.bingo

import org.apache.commons.lang3.time.DurationFormatUtils
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Team
import xyz.fortern.minehunt.game.CompletedGameRecord
import xyz.fortern.minehunt.game.GameManager
import xyz.fortern.minehunt.game.GameOutcome
import xyz.fortern.minehunt.game.GamePhase
import xyz.fortern.minehunt.game.Lobby
import xyz.fortern.minehunt.mode.bingo.record.BingoClaimRecord
import xyz.fortern.minehunt.mode.bingo.record.BingoRecord
import xyz.fortern.minehunt.mode.bingo.record.BingoWinningLineRecord
import xyz.fortern.minehunt.mode.bingo.record.PlayerInBingo
import xyz.fortern.minehunt.record.FactionInfo
import xyz.fortern.minehunt.record.FinishType
import xyz.fortern.minehunt.record.GameRecord
import xyz.fortern.minehunt.record.PlayerInGame
import xyz.fortern.minehunt.rule.RuleKey
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import xyz.fortern.minehunt.game.GameMode as RuntimeGameMode
import xyz.fortern.minehunt.record.GameMode as GameModeId

/** 红蓝两队共享 5×5 物品卡的标准连线 Bingo 模式。 */
class BingoGame(
    private val gameManager: GameManager,
    private val plugin: JavaPlugin,
) : RuntimeGameMode {
    private val lobby = Lobby()

    override val id = GameModeId.BINGO
    override val listener = BingoListener()
    override val roles = listOf(ROLE_RED, ROLE_BLUE, ROLE_AUDIENCE)
    override val spectatorRole = ROLE_AUDIENCE
    override val rules = BingoRules()
    override val specialItems = listOf(SPECIAL_ITEM_CARD)

    private val gameRules: BingoRules
        get() = rules

    private val scoreboard = checkNotNull(Bukkit.getScoreboardManager()).mainScoreboard
    private val overworld: World = Bukkit.getWorlds().firstOrNull { it.environment == World.Environment.NORMAL }
        ?: error("Bingo 需要主世界")
    private val worldSeeds: Map<String, Long> = Bukkit.getWorlds().associate { it.name to it.seed }
    private val cardKey = NamespacedKey(plugin, "bingo_card")

    private val redTeam: Team
    private val blueTeam: Team
    private val audienceTeam: Team

    private val redPlayers = LinkedHashSet<UUID>()
    private val bluePlayers = LinkedHashSet<UUID>()
    private val claims = linkedMapOf(
        ROLE_RED to LinkedHashMap<Material, BingoClaimRecord>(),
        ROLE_BLUE to LinkedHashMap<Material, BingoClaimRecord>(),
    )
    private val winningLines = LinkedHashMap<String, List<Int>>()

    private var board: BingoBoard? = null
    private var scanTask: BukkitTask? = null
    private var finishTask: BukkitTask? = null
    private val respawnTasks = HashMap<UUID, BukkitTask>()

    init {
        clearModeObjectives()
        redTeam = registerTeam(TEAM_RED, ChatColor.RED, "[红队]")
        blueTeam = registerTeam(TEAM_BLUE, ChatColor.BLUE, "[蓝队]")
        audienceTeam = registerTeam(TEAM_AUDIENCE, ChatColor.GRAY, "[观众]")
        showRulesScoreboard()

        overworld.worldBorder.size = 32.0
        overworld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        overworld.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
        overworld.setGameRule(GameRule.DO_MOB_SPAWNING, false)
        overworld.setGameRule(GameRule.SPAWN_RADIUS, 0)
        overworld.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
        overworld.difficulty = Difficulty.HARD
    }

    override fun assignRole(player: Player, role: String): Boolean {
        if (gameManager.phase != GamePhase.LOBBY || role !in roles) return false
        lobby.assign(player.uniqueId, player.name, role)
        removeFromScoreboardTeams(player.name)
        when (role) {
            ROLE_RED -> {
                redTeam.addEntry(player.name)
                player.sendMessage("${ChatColor.RED}你已加入红队")
            }

            ROLE_BLUE -> {
                blueTeam.addEntry(player.name)
                player.sendMessage("${ChatColor.BLUE}你已加入蓝队")
            }

            ROLE_AUDIENCE -> {
                audienceTeam.addEntry(player.name)
                player.sendMessage("${ChatColor.GRAY}你已加入观众")
            }
        }
        return true
    }

    override fun onPlayerQuit(player: Player) {
        if (gameManager.phase != GamePhase.COUNTDOWN) return
        val role = lobby.member(player.uniqueId)?.role ?: return
        if (isParticipantRole(role)) gameManager.interruptCountdown()
        lobby.remove(player.uniqueId)
        removeFromScoreboardTeams(player.name)
    }

    override fun validateStart(): String? {
        val onlineRed = lobby.members(ROLE_RED).count { Bukkit.getPlayer(it.uniqueId) != null }
        val onlineBlue = lobby.members(ROLE_BLUE).count { Bukkit.getPlayer(it.uniqueId) != null }
        return when {
            onlineRed == 0 -> "红队需要至少一位在线玩家"
            onlineBlue == 0 -> "蓝队需要至少一位在线玩家"
            else -> null
        }
    }

    override fun participants(): Set<UUID> = lobby.allMembers()
        .filter { isParticipantRole(it.role) && Bukkit.getPlayer(it.uniqueId) != null }
        .mapTo(LinkedHashSet()) { it.uniqueId }

    override fun stopVoters(): Set<UUID> = gameManager.participants

    override fun start() {
        redPlayers.clear()
        bluePlayers.clear()
        claims.values.forEach(MutableMap<Material, BingoClaimRecord>::clear)
        winningLines.clear()

        val configuredSeed = gameRules.getRuleValue(BingoRuleKeys.CARD_SEED)
        val cardSeed = if (configuredSeed == 0L) randomNonZeroSeed() else configuredSeed
        board = BingoBoard.generate(cardSeed)

        val keepInventory = gameRules.getRuleValue(BingoRuleKeys.KEEP_INVENTORY)
        Bukkit.getWorlds().forEach { world ->
            world.setGameRule(GameRule.KEEP_INVENTORY, keepInventory)
            world.setGameRule(GameRule.DO_MOB_SPAWNING, true)
        }
        overworld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true)
        overworld.setGameRule(GameRule.DO_WEATHER_CYCLE, true)
        overworld.setGameRule(GameRule.SPAWN_RADIUS, 10)
        overworld.worldBorder.size = 59_999_968.0

        val spawn = overworld.spawnLocation
        Bukkit.getOnlinePlayers().forEach { player ->
            player.gameMode = GameMode.SPECTATOR
            player.inventory.clear()
            player.health = 20.0
            player.saturation = 20.0f
            player.foodLevel = 20
            player.level = 0
            player.exp = 0.0f
            player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
            player.teleport(spawn)
        }

        snapshotTeam(ROLE_RED, redPlayers, redTeam)
        snapshotTeam(ROLE_BLUE, bluePlayers, blueTeam)
        check(redPlayers.isNotEmpty() && bluePlayers.isNotEmpty()) { "Both Bingo teams must have a player" }

        Bukkit.getOnlinePlayers().forEach(::giveCardIfNeeded)
        showGameScoreboard()
        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendMessage("${ChatColor.GREEN}Bingo 开始！卡片种子：$cardSeed")
        }

        scanTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable(::scanInventories), 1L, 1L)
    }

    override fun rejoin(player: Player) {
        if (gameManager.phase != GamePhase.RUNNING) return
        removeFromScoreboardTeams(player.name)
        when (factionOf(player)) {
            Faction.RED -> {
                redTeam.addEntry(player.name)
                player.gameMode = GameMode.SURVIVAL
                player.sendMessage("${ChatColor.RED}你已返回红队")
            }

            Faction.BLUE -> {
                blueTeam.addEntry(player.name)
                player.gameMode = GameMode.SURVIVAL
                player.sendMessage("${ChatColor.BLUE}你已返回蓝队")
            }

            null -> {
                audienceTeam.addEntry(player.name)
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage("${ChatColor.GRAY}你以观众身份加入")
            }
        }
        giveCardIfNeeded(player)
    }

    override fun finish(outcome: GameOutcome): CompletedGameRecord {
        val activeBoard = checkNotNull(board) { "Bingo board was not generated" }
        val winner = outcome.winnerRole?.let(Faction::fromRole)
        val startedAt = checkNotNull(gameManager.startedAt) { "Game start time is not initialized" }
        val endedAt = gameManager.endedAt ?: Instant.now()

        Bukkit.getOnlinePlayers().forEach { player ->
            player.gameMode = GameMode.SURVIVAL
            player.sendMessage("${ChatColor.GREEN}--------游戏结束--------")
            val winnerMessage = when {
                winner != null -> "获胜者：${winner.displayName}"
                outcome.finishType == FinishType.FINISHED && winningLines.isNotEmpty() -> "双方同时完成连线，比赛平局"
                else -> "比赛已终止，没有获胜者"
            }
            player.sendMessage("${ChatColor.GOLD}$winnerMessage")
        }

        val redRank = rankFor(Faction.RED, winner, outcome.finishType)
        val blueRank = rankFor(Faction.BLUE, winner, outcome.finishType)
        val factionResults = listOf(
            FactionInfo("RED", ChatColor.RED, redRank, redPlayers.toList()),
            FactionInfo("BLUE", ChatColor.BLUE, blueRank, bluePlayers.toList()),
        ).sortedBy { it.rank }

        val modeDetails = BingoRecord(
            activeBoard.seed,
            activeBoard.targets.map { it.key.toString() },
            Faction.entries.flatMap { claims.getValue(it.role).values }.map { it.copy() },
            winningLines.map { (team, slots) -> BingoWinningLineRecord(team, slots.toList()) },
        )
        val gameRecord = GameRecord(
            0,
            UUID.randomUUID(),
            GameModeId.BINGO,
            startedAt,
            endedAt,
            Duration.between(startedAt, endedAt),
            outcome.finishType,
            factionResults,
            overworld.seed,
            worldSeeds,
            modeDetails,
        )

        val playerRecords = (redPlayers + bluePlayers).map { playerId ->
            val faction = checkNotNull(factionOf(Bukkit.getOfflinePlayer(playerId)))
            val completedTargets = claims.getValue(faction.role).values
                .filter { it.player == playerId }
                .map { it.target }
            PlayerInGame(
                playerId,
                0,
                if (faction == Faction.RED) redRank else blueRank,
                PlayerInBingo(faction.role, completedTargets),
            )
        }
        showResultScoreboard(gameRecord, winner)
        refreshOpenCards()
        return CompletedGameRecord(gameRecord, playerRecords)
    }

    override fun giveSpecialItem(player: Player, item: String): Boolean {
        if (item != SPECIAL_ITEM_CARD) return false
        if (board == null) {
            player.sendMessage("${ChatColor.RED}Bingo 卡片尚未生成")
        } else {
            giveCardIfNeeded(player)
        }
        return true
    }

    override fun onRuleChanged(rule: RuleKey<*>) {
        showRulesScoreboard()
    }

    override fun onDatabaseSaved(gameId: Int) {

    }

    override fun cancelTasks() {
        scanTask?.cancel()
        scanTask = null
        finishTask?.cancel()
        finishTask = null
        respawnTasks.values.forEach(BukkitTask::cancel)
        respawnTasks.clear()
    }

    override fun close() {
        cancelTasks()
        clearModeObjectives()
        listOf(redTeam, blueTeam, audienceTeam).forEach { team -> runCatching(team::unregister) }
    }

    private fun scanInventories() {
        if (gameManager.phase != GamePhase.RUNNING || finishTask != null) return
        val activeBoard = board ?: return
        var changed = false

        Faction.entries.forEach { faction ->
            val teamClaims = claims.getValue(faction.role)
            val inventories = playersFor(faction).mapNotNull { playerId ->
                Bukkit.getPlayer(playerId)?.takeIf(Player::isOnline)?.let { player ->
                    player to buildSet<Material> {
                        player.inventory.contents.asSequence()
                            .filterNotNull()
                            .mapTo(this, ItemStack::getType)
                        add(player.itemOnCursor.type)
                    }
                }
            }
            activeBoard.targets.forEach { target ->
                if (target in teamClaims) return@forEach
                val player = inventories.firstOrNull { (_, materials) -> target in materials }?.first
                    ?: return@forEach
                val claim = BingoClaimRecord(
                    faction.role,
                    target.key.toString(),
                    checkNotNull(activeBoard.slotOf(target)),
                    player.uniqueId,
                    elapsedMillis(),
                )
                teamClaims[target] = claim
                announceClaim(faction, player, target)
                changed = true
            }
        }

        if (!changed) return
        showGameScoreboard()
        refreshOpenCards()

        val winners = Faction.entries.mapNotNull { faction ->
            activeBoard.winningLines(claims.getValue(faction.role).keys).firstOrNull()?.let { line ->
                faction to line
            }
        }
        if (winners.isEmpty()) return
        winningLines.clear()
        winners.forEach { (faction, line) -> winningLines[faction.role] = line }
        val winnerRole = winners.singleOrNull()?.first?.role
        finishTask = plugin.server.scheduler.runTask(plugin, Runnable {
            finishTask = null
            gameManager.finish(GameOutcome(winnerRole, FinishType.FINISHED))
        })
    }

    private fun announceClaim(faction: Faction, player: Player, target: Material) {
        val targetName = target.name.lowercase().replace('_', ' ')
        val message = "${faction.chatColor}${faction.displayName}的 ${player.name} 完成了 $targetName"
        Bukkit.getOnlinePlayers().forEach { it.sendMessage(message) }
    }

    private fun snapshotTeam(role: String, destination: MutableSet<UUID>, team: Team) {
        lobby.members(role).forEach { member ->
            val player = Bukkit.getPlayer(member.uniqueId) ?: return@forEach
            destination += player.uniqueId
            team.addEntry(player.name)
            player.gameMode = GameMode.SURVIVAL
        }
    }

    private fun factionOf(player: OfflinePlayer): Faction? = when (gameManager.phase) {
        GamePhase.LOBBY, GamePhase.COUNTDOWN -> Faction.fromRoleOrNull(lobby.member(player.uniqueId)?.role)
        else -> when (player.uniqueId) {
            in redPlayers -> Faction.RED
            in bluePlayers -> Faction.BLUE
            else -> null
        }
    }

    private fun playersFor(faction: Faction): Set<UUID> = when (faction) {
        Faction.RED -> redPlayers
        Faction.BLUE -> bluePlayers
    }

    private fun rankFor(faction: Faction, winner: Faction?, finishType: FinishType): Int = when {
        finishType != FinishType.FINISHED -> 0
        winner == null && winningLines.isNotEmpty() -> 1
        winner == null -> 0
        faction == winner -> 1
        else -> 2
    }

    private fun elapsedMillis(): Long {
        val start = gameManager.startedAt ?: return 0L
        return Duration.between(start, Instant.now()).toMillis().coerceAtLeast(0L)
    }

    private fun registerTeam(name: String, color: ChatColor, prefix: String): Team {
        scoreboard.getTeam(name)?.unregister()
        return scoreboard.registerNewTeam(name).also {
            it.color = color
            it.prefix = prefix
            it.setAllowFriendlyFire(false)
        }
    }

    private fun removeFromScoreboardTeams(playerName: String) {
        redTeam.removeEntry(playerName)
        blueTeam.removeEntry(playerName)
        audienceTeam.removeEntry(playerName)
    }

    private fun showRulesScoreboard() {
        scoreboard.getObjective(OBJECTIVE_RULES)?.unregister()
        val objective = scoreboard.registerNewObjective(
            OBJECTIVE_RULES,
            Criteria.DUMMY,
            "${ChatColor.DARK_AQUA}Bingo 游戏规则",
        )
        val entries = listOf(
            "${ChatColor.GOLD}卡片种子: ${ChatColor.GREEN}${gameRules.getRuleValue(BingoRuleKeys.CARD_SEED)}",
            "${ChatColor.GOLD}死亡保留: ${ChatColor.GREEN}${gameRules.getRuleValue(BingoRuleKeys.KEEP_INVENTORY)}",
            "${ChatColor.GOLD}PVP: ${ChatColor.GREEN}${gameRules.getRuleValue(BingoRuleKeys.PVP)}",
        )
        entries.forEachIndexed { index, entry -> objective.getScore(entry).score = entries.size - index }
        objective.displaySlot = DisplaySlot.SIDEBAR
    }

    private fun showGameScoreboard() {
        scoreboard.getObjective(OBJECTIVE_RULES)?.unregister()
        scoreboard.getObjective(OBJECTIVE_GAME)?.unregister()
        val objective = scoreboard.registerNewObjective(
            OBJECTIVE_GAME,
            Criteria.DUMMY,
            "${ChatColor.DARK_AQUA}Bingo",
        )
        objective.getScore("${ChatColor.RED}红队: ${claims.getValue(ROLE_RED).size}/25").score = 3
        objective.getScore("${ChatColor.BLUE}蓝队: ${claims.getValue(ROLE_BLUE).size}/25").score = 2
        objective.getScore("${ChatColor.GRAY}种子: ${board?.seed ?: 0}").score = 1
        objective.displaySlot = DisplaySlot.SIDEBAR
    }

    private fun showResultScoreboard(gameRecord: GameRecord, winner: Faction?) {
        scoreboard.getObjective(OBJECTIVE_GAME)?.unregister()
        scoreboard.getObjective(OBJECTIVE_RESULT)?.unregister()
        val objective = scoreboard.registerNewObjective(
            OBJECTIVE_RESULT,
            Criteria.DUMMY,
            "${ChatColor.DARK_AQUA}Bingo Game Over",
        )
        val winnerText = when {
            winner != null -> winner.displayName
            gameRecord.finishType == FinishType.FINISHED && winningLines.isNotEmpty() -> "平局"
            else -> "无"
        }
        objective.getScore("${ChatColor.GOLD}胜者: $winnerText").score = 4
        objective.getScore("${ChatColor.RED}红队: ${claims.getValue(ROLE_RED).size}/25").score = 3
        objective.getScore("${ChatColor.BLUE}蓝队: ${claims.getValue(ROLE_BLUE).size}/25").score = 2
        objective.getScore(
            "${ChatColor.GRAY}用时: ${DurationFormatUtils.formatDurationHMS(gameRecord.duration.toMillis())}"
        ).score = 1
        objective.displaySlot = DisplaySlot.SIDEBAR
    }

    private fun clearModeObjectives() {
        listOf(OBJECTIVE_RULES, OBJECTIVE_GAME, OBJECTIVE_RESULT).forEach { name ->
            scoreboard.getObjective(name)?.unregister()
        }
    }

    private fun giveCardIfNeeded(player: Player) {
        if (player.inventory.contents.any(::isBingoCard)) return
        val leftovers = player.inventory.addItem(createCardItem())
        if (leftovers.isNotEmpty()) {
            player.sendMessage("${ChatColor.RED}背包已满，无法放入 Bingo 卡片")
        }
    }

    private fun createCardItem(): ItemStack = ItemStack(Material.KNOWLEDGE_BOOK).apply {
        itemMeta = itemMeta?.apply {
            setDisplayName("${ChatColor.GOLD}Bingo Card")
            lore = listOf("${ChatColor.GRAY}右键查看 5×5 目标卡片")
            persistentDataContainer.set(cardKey, PersistentDataType.BYTE, 1.toByte())
        }
    }

    private fun isBingoCard(item: ItemStack?): Boolean = item?.itemMeta?.persistentDataContainer
        ?.has(cardKey, PersistentDataType.BYTE) == true

    private fun openCard(player: Player) {
        if (board == null) {
            player.sendMessage("${ChatColor.RED}Bingo 卡片尚未生成")
            return
        }
        val holder = BingoCardHolder()
        val inventory = Bukkit.createInventory(holder, CARD_INVENTORY_SIZE, CARD_TITLE)
        holder.bind(inventory)
        renderCard(inventory)
        player.openInventory(inventory)
    }

    private fun renderCard(inventory: Inventory) {
        val activeBoard = board ?: return
        val filler = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { setDisplayName(" ") }
        }
        repeat(inventory.size) { inventory.setItem(it, filler) }

        activeBoard.targets.forEachIndexed { index, target ->
            val redDone = target in claims.getValue(ROLE_RED)
            val blueDone = target in claims.getValue(ROLE_BLUE)
            val winning = winningLines.values.any { index in it }
            val icon = ItemStack(target).apply {
                itemMeta = itemMeta?.apply {
                    lore = buildList {
                        add("${ChatColor.RED}红队: ${if (redDone) "已完成" else "未完成"}")
                        add("${ChatColor.BLUE}蓝队: ${if (blueDone) "已完成" else "未完成"}")
                        if (winning) add("${ChatColor.GOLD}获胜连线")
                    }
                }
            }
            val row = index / BingoBoard.SIZE
            val column = index % BingoBoard.SIZE
            inventory.setItem(row * 9 + column + 2, icon)
        }
    }

    private fun refreshOpenCards() {
        Bukkit.getOnlinePlayers().forEach { player ->
            val top = player.openInventory.topInventory
            if (top.holder is BingoCardHolder) renderCard(top)
        }
    }

    private fun scheduleRespawnState(player: Player) {
        respawnTasks.remove(player.uniqueId)?.cancel()
        respawnTasks[player.uniqueId] = plugin.server.scheduler.runTask(plugin, Runnable {
            respawnTasks.remove(player.uniqueId)
            if (!player.isOnline || gameManager.phase != GamePhase.RUNNING) return@Runnable
            player.gameMode = if (factionOf(player) == null) GameMode.SPECTATOR else GameMode.SURVIVAL
            giveCardIfNeeded(player)
        })
    }

    private fun randomNonZeroSeed(): Long {
        var seed: Long
        do seed = ThreadLocalRandom.current().nextLong() while (seed == 0L)
        return seed
    }

    private fun isParticipantRole(role: String): Boolean = role == ROLE_RED || role == ROLE_BLUE

    private class BingoCardHolder : InventoryHolder {
        private lateinit var backing: Inventory

        fun bind(inventory: Inventory) {
            backing = inventory
        }

        override fun getInventory(): Inventory = backing
    }

    inner class BingoListener : Listener {
        @EventHandler(ignoreCancelled = true)
        fun onCardUse(event: PlayerInteractEvent) {
            if (event.hand != EquipmentSlot.HAND || !isBingoCard(event.item)) return
            if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
            event.isCancelled = true
            openCard(event.player)
        }

        @EventHandler(ignoreCancelled = true)
        fun onCardDrop(event: PlayerDropItemEvent) {
            if (isBingoCard(event.itemDrop.itemStack)) event.isCancelled = true
        }

        @EventHandler
        fun onCardClick(event: InventoryClickEvent) {
            if (event.view.topInventory.holder is BingoCardHolder) event.isCancelled = true
        }

        @EventHandler
        fun onCardDrag(event: InventoryDragEvent) {
            if (event.view.topInventory.holder is BingoCardHolder) event.isCancelled = true
        }

        @EventHandler(ignoreCancelled = true)
        fun onParticipantDamage(event: EntityDamageByEntityEvent) {
            if (gameManager.phase != GamePhase.RUNNING) return
            val victim = event.entity as? Player ?: return
            val attacker = event.damageSource.causingEntity as? Player
                ?: when (val damager = event.damager) {
                    is Player -> damager
                    is Projectile -> damager.shooter as? Player
                    else -> null
                }
                ?: return
            val victimFaction = factionOf(victim) ?: return
            val attackerFaction = factionOf(attacker) ?: return
            if (victimFaction == attackerFaction || !gameRules.getRuleValue(BingoRuleKeys.PVP)) {
                event.isCancelled = true
            }
        }

        @EventHandler
        fun onPlayerDeath(event: PlayerDeathEvent) {
            if (gameManager.phase != GamePhase.RUNNING || factionOf(event.entity) == null) return
            if (gameRules.getRuleValue(BingoRuleKeys.KEEP_INVENTORY)) {
                event.keepInventory = true
                event.drops.clear()
                event.keepLevel = true
                event.droppedExp = 0
            } else {
                event.drops.removeAll(::isBingoCard)
            }
        }

        @EventHandler
        fun onPlayerRespawn(event: PlayerRespawnEvent) {
            if (gameManager.phase == GamePhase.RUNNING) scheduleRespawnState(event.player)
        }
    }

    enum class Faction(
        val role: String,
        val displayName: String,
        val chatColor: ChatColor,
    ) {
        RED(ROLE_RED, "红队", ChatColor.RED),
        BLUE(ROLE_BLUE, "蓝队", ChatColor.BLUE);

        companion object {
            fun fromRole(role: String): Faction = fromRoleOrNull(role)
                ?: error("Unknown Bingo winner role: $role")

            fun fromRoleOrNull(role: String?): Faction? = entries.firstOrNull { it.role == role }
        }
    }

    companion object {
        const val ROLE_RED = "red"
        const val ROLE_BLUE = "blue"
        const val ROLE_AUDIENCE = "audience"
        const val SPECIAL_ITEM_CARD = "card"

        private const val TEAM_RED = "BINGO_RED"
        private const val TEAM_BLUE = "BINGO_BLUE"
        private const val TEAM_AUDIENCE = "BINGO_AUDIENCE"
        private const val OBJECTIVE_RULES = "bingo_rules"
        private const val OBJECTIVE_GAME = "bingo_game"
        private const val OBJECTIVE_RESULT = "bingo_result"
        private const val CARD_TITLE = "Bingo Card"
        private const val CARD_INVENTORY_SIZE = 45
    }
}
