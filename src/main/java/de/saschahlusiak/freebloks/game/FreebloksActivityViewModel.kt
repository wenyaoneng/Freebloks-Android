package de.saschahlusiak.freebloks.game

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import de.saschahlusiak.freebloks.Global
import de.saschahlusiak.freebloks.R
import de.saschahlusiak.freebloks.client.GameClient
import de.saschahlusiak.freebloks.client.GameEventObserver
import de.saschahlusiak.freebloks.lobby.ChatEntry
import de.saschahlusiak.freebloks.lobby.ChatEntry.Companion.serverMessage
import de.saschahlusiak.freebloks.model.GameMode
import de.saschahlusiak.freebloks.network.message.MessageServerStatus

class FreebloksActivityViewModel(app: Application) : AndroidViewModel(app), GameEventObserver {
    private val context = app

    // UI Thread handler
    private val handler = Handler()

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    var vibrateOnMove: Boolean = false
        private set

    var client: GameClient?
        private set

    // todo: is required?
    var lastStatus: MessageServerStatus? = null
        private set

    val game get() = client?.game

    val board get() = client?.game?.board

    val chatHistory = mutableListOf<ChatEntry>()

    // LiveData
    val chatHistoryAsLiveData = MutableLiveData(chatHistory)

    init {
        client = null
        reloadPreferences()
    }

    override fun onCleared() {
        disconnectClient()
    }

    fun reloadPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(getApplication())

        vibrateOnMove = prefs.getBoolean("vibrate", true)
    }

    fun vibrate(milliseconds: Long) {
        if (vibrateOnMove)
            vibrator?.vibrate(milliseconds)
    }

    // TODO: call me
    fun setClient(client: GameClient) {
        this.client = client
        client.addObserver(this)
    }

    fun disconnectClient() {
        client?.disconnect()
        client = null
    }

    //region GameEventObserver callbacks

    override fun onConnected(client: GameClient) {
        lastStatus = null
    }

    override fun serverStatus(status: MessageServerStatus) {
        this.lastStatus = status
    }

    override fun chatReceived(status: MessageServerStatus, client: Int, player: Int, message: String) {
        val name = status.getClientName(context.resources, client)
        val e = ChatEntry.clientMessage(client, player, message, name)

        chatHistory.add(e)
        chatHistoryAsLiveData.postValue(chatHistory)
    }

    override fun playerJoined(player: Int, client: Int, serverStatus: MessageServerStatus) {
        val name = serverStatus.getClientName(context.resources, client)
        // the names of colors
        val colorNames = context.resources.getStringArray(R.array.color_names)
        // the index into colorNames
        val playerColor = Global.getPlayerColor(player, game?.gameMode ?: GameMode.GAMEMODE_4_COLORS_4_PLAYERS)
        // the name of the player's color
        val colorName = colorNames[playerColor]

        val text = context.getString(R.string.player_joined_color, name, colorName)
        val e = serverMessage(player, text, name)

        chatHistory.add(e)
        chatHistoryAsLiveData.postValue(chatHistory)
    }

    override fun playerLeft(player: Int, client: Int, serverStatus: MessageServerStatus) {
        val name = serverStatus.getClientName(context.resources, client)
        // the names of colors
        val colorNames = context.resources.getStringArray(R.array.color_names)
        // the index into colorNames
        val playerColor = Global.getPlayerColor(player, game?.gameMode ?: GameMode.GAMEMODE_4_COLORS_4_PLAYERS)
        // the name of the player's color
        val colorName = colorNames[playerColor]

        val text = context.getString(R.string.player_left_color, name, colorName)
        val e = serverMessage(player, text, name)

        chatHistory.add(e)
        chatHistoryAsLiveData.postValue(chatHistory)
    }

    override fun onDisconnected(client: GameClient, error: Exception?) {
        lastStatus = null
        chatHistory.clear()
    }
    //endregion

}