# Emergency Alarm Sound Setup

## Instructions for Adding Custom Alarm Sound

To use a custom alarm sound for emergency SOS notifications, follow these steps:

1. **Find or create an alarm sound file**:
   - Format: MP3, WAV, or OGG
   - Duration: 3-10 seconds (recommended)
   - Quality: Clear, attention-grabbing alarm sound

2. **Add the sound file to the raw directory**:
   - Rename your alarm sound file to: `emergency_alarm.mp3`
   - Place it in: `app/src/main/res/raw/emergency_alarm.mp3`

3. **Recommended alarm sound characteristics**:
   - High-pitched, urgent tone
   - Repetitive pattern (beep-beep-beep)
   - Loud enough to wake someone up
   - Distinct from regular notification sounds

4. **Example alarm sounds you can use**:
   - Emergency siren sound
   - High-pitched beeping
   - Ambulance siren
   - Fire alarm sound
   - Police siren

## Current Status
- The app is configured to use `R.raw.emergency_alarm`
- If no custom file is provided, the system will fall back to the default alarm sound
- The alarm sound will play for emergency SOS notifications even when the app is closed

## File Location
```
app/src/main/res/raw/emergency_alarm.mp3
```

## How It Works
1. **With custom alarm file**: Uses your real alarm sound for all emergency notifications
2. **Without custom alarm file**: Falls back to system alarm sound (still works)
3. **Background monitoring**: Custom alarm sound plays even when app is closed
4. **High priority**: Alarm sound overrides silent mode and do not disturb

Once you add the alarm sound file, the emergency SOS notifications will use your custom alarm sound instead of the system default.
