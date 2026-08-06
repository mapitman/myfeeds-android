package com.bugzapperlabs.myfeeds.playback

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.bugzapperlabs.myfeeds.R
import com.google.common.collect.ImmutableList

/**
 * Adds skip-forward/skip-backward/cycle-speed buttons to the media notification (issue #293), in
 * place of Media3's default seek-to-next/seek-to-previous actions, which the notification would
 * otherwise show given [MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS] -- those only
 * operate on the player's own timeline, but Next Up is managed externally with only ever one
 * [androidx.media3.common.MediaItem] loaded at a time (issue #179), so they'd never actually do
 * anything. [getMediaButtons] is the documented extension point for customizing which buttons
 * a [DefaultMediaNotificationProvider] shows.
 */
@UnstableApi
class MyFeedsMediaNotificationProvider(private val context: Context) : DefaultMediaNotificationProvider(context) {

    @OptIn(markerClass = [UnstableApi::class])
    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        showPauseButton: Boolean,
    ): ImmutableList<CommandButton> {
        val compactViewExtras = { index: Int ->
            Bundle().apply { putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, index) }
        }
        val currentSpeed = session.player.playbackParameters.speed
        val skipBackward = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_15)
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SKIP_BACKWARD, Bundle.EMPTY))
            .setDisplayName(context.getString(R.string.cd_rewind))
            .setExtras(compactViewExtras(0))
            .build()
        val playPause = if (showPauseButton) {
            CommandButton.Builder(CommandButton.ICON_PAUSE)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setDisplayName(context.getString(androidx.media3.session.R.string.media3_controls_pause_description))
                .setExtras(compactViewExtras(1))
                .build()
        } else {
            CommandButton.Builder(CommandButton.ICON_PLAY)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setDisplayName(context.getString(androidx.media3.session.R.string.media3_controls_play_description))
                .setExtras(compactViewExtras(1))
                .build()
        }
        val skipForward = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SKIP_FORWARD, Bundle.EMPTY))
            .setDisplayName(context.getString(R.string.cd_forward))
            .setExtras(compactViewExtras(2))
            .build()
        val speedCycle = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setIconResId(R.drawable.ic_notification_speed)
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_CYCLE_SPEED, Bundle.EMPTY))
            .setDisplayName(context.getString(R.string.notification_cd_speed, formatSpeedForNotification(currentSpeed)))
            .build()
        return ImmutableList.of(skipBackward, playPause, skipForward, speedCycle)
    }
}

/**
 * Builds the same skip-backward/skip-forward/cycle-speed [CommandButton]s as
 * [MyFeedsMediaNotificationProvider.getMediaButtons] (minus notification-only compact-view
 * placement extras, which don't mean anything here), for [PlaybackService] to push through
 * [MediaSession.setMediaButtonPreferences] -- issue #293: without that, Media3's legacy platform
 * bridge never learns about these custom actions, since it builds the legacy
 * `PlaybackStateCompat`'s custom actions from the session's media button preferences/custom
 * layout, not from whatever the notification provider itself renders. That legacy
 * `PlaybackStateCompat` is what drives the system's own "now playing" media card/lock-screen
 * controls, which otherwise falls back to a bare, generic previous/next chevron layout.
 */
@OptIn(markerClass = [UnstableApi::class])
internal fun buildSkipAndSpeedMediaButtonPreferences(context: Context, currentSpeed: Float): List<CommandButton> {
    val skipBackward = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_15)
        .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SKIP_BACKWARD, Bundle.EMPTY))
        .setDisplayName(context.getString(R.string.cd_rewind))
        .build()
    val skipForward = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
        .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SKIP_FORWARD, Bundle.EMPTY))
        .setDisplayName(context.getString(R.string.cd_forward))
        .build()
    val speedCycle = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
        .setIconResId(R.drawable.ic_notification_speed)
        .setSessionCommand(SessionCommand(CUSTOM_COMMAND_CYCLE_SPEED, Bundle.EMPTY))
        .setDisplayName(context.getString(R.string.notification_cd_speed, formatSpeedForNotification(currentSpeed)))
        .build()
    return listOf(skipBackward, skipForward, speedCycle)
}

internal fun formatSpeedForNotification(speed: Float): String =
    "${"%.2f".format(speed).trimEnd('0').trimEnd('.')}x"
