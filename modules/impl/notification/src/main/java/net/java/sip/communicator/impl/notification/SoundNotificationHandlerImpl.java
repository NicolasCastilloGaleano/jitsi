private void play(SoundNotificationAction action, NotificationData data, SCAudioClipDevice device) {
    AudioNotifierService audioNotifService = NotificationActivator.getAudioNotifier();
    
    if (audioNotifService == null || StringUtils.isBlank(action.getDescriptor())) return;
    
    // this is hack, seen on some os (particularly seen on macosx with external devices).
    // when playing notification in the call, can break the call and
    // no further communicating can be done after the notification.
    // So we skip playing notification if we have a call running
    ConfigurationService cfg = NotificationActivator.getConfigurationService();
    if (cfg != null && cfg.getBoolean(PROP_DISABLE_NOTIFICATION_DURING_CALL, false) 
            && SCAudioClipDevice.PLAYBACK.equals(device)) {
        UIService uiService = NotificationActivator.getUIService();
        if (!uiService.getInProgressCalls().isEmpty()) return;
    }

    SCAudioClip audio = createAudioClipForDevice(device, audioNotifService, action);
    if (audio == null) return;

    PlaybackExecution execution = new PlaybackExecution(audio, data);
    execution.execute(action.getLoopInterval());
}

private SCAudioClip createAudioClipForDevice(SCAudioClipDevice device, AudioNotifierService audioNotifService, SoundNotificationAction action) {
    if (device == SCAudioClipDevice.PC_SPEAKER) {
        return OSUtils.IS_ANDROID ? null : new PCSpeakerClip();
    }
    if (device == SCAudioClipDevice.NOTIFICATION || device == SCAudioClipDevice.PLAYBACK) {
        return audioNotifService.createAudio(action.getDescriptor(), SCAudioClipDevice.PLAYBACK.equals(device));
    }
    return null;
}

private class PlaybackExecution {
    private final SCAudioClip audio;
    private final NotificationData data;
    
    PlaybackExecution(SCAudioClip audio, NotificationData data) {
        this.audio = audio;
        this.data = data;
    }
    
    void execute(int loopInterval) {
        synchronized(playedClips) {
            playedClips.put(audio, data);
        }
        
        boolean played = false;
        try {
            @SuppressWarnings("unchecked")
            Callable<Boolean> loopCondition = (Callable<Boolean>) data.getExtra(
                NotificationData.SOUND_NOTIFICATION_HANDLER_LOOP_CONDITION_EXTRA);
            audio.play(loopInterval, loopCondition);
            played = true;
        }
        finally {
            synchronized(playedClips) {
                if (!played) playedClips.remove(audio);
            }
        }
    }
}