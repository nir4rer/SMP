/*
 * SicMu Player - Lightweight music player for Android
 * Copyright (C) 2015  Mathieu Souchaud
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package souch.smp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

public class Settings extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        Preference.OnPreferenceClickListener
{
    private MusicService musicSrv;
    private Intent playIntent;
    private boolean serviceBound = false;

    // todo: improve preference default value

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("Settings", "onCreate");
        super.onCreate(savedInstanceState);
        // fixme: everything should be put in onResume?
        addPreferencesFromResource(R.xml.preferences);
        playIntent = new Intent(this, MusicService.class);
        bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);

        SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();

        String thresholdKeys = PrefKeys.SHAKE_THRESHOLD.name();
        EditTextPreference prefShakeThreshold = (EditTextPreference) findPreference(thresholdKeys);
        CheckBoxPreference prefEnableShake = (CheckBoxPreference) findPreference(PrefKeys.ENABLE_SHAKE.name());
        if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
            prefShakeThreshold.setSummary(sharedPreferences.getString(thresholdKeys, getString(R.string.settings_default_shake_threshold)));
            prefEnableShake.setChecked(getShakePref(getPreferenceScreen().getSharedPreferences()));
        }
        else {
            prefShakeThreshold.setEnabled(false);
            prefEnableShake.setEnabled(false);
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.settings_no_accelerometer),
                    Toast.LENGTH_LONG).show();
        }

        String textSizeKey;
        EditTextPreference prefTextSize;
        textSizeKey = PrefKeys.TEXT_SIZE_GROUP.name();
        prefTextSize = (EditTextPreference) findPreference(textSizeKey);
        prefTextSize.setSummary(sharedPreferences.getString(textSizeKey,
                getString(R.string.settings_text_size_group_default)));
        textSizeKey = PrefKeys.TEXT_SIZE_SONG.name();
        prefTextSize = (EditTextPreference) findPreference(textSizeKey);
        prefTextSize.setSummary(sharedPreferences.getString(textSizeKey,
                getString(R.string.settings_text_size_song_default)));

        Preference rescan = findPreference(getResources().getString(R.string.settings_rescan_key));
        rescan.setOnPreferenceClickListener(this);

        Preference donate = findPreference("donate");
        donate.setOnPreferenceClickListener(this);

        String rootFolderKey = PrefKeys.ROOT_FOLDER.name();
        EditTextPreference prefRootFolder = (EditTextPreference) findPreference(rootFolderKey);
        prefRootFolder.setSummary(sharedPreferences.getString(rootFolderKey, getDefaultMusicDir()));
        if(!sharedPreferences.contains(rootFolderKey))
            prefRootFolder.setText(getDefaultMusicDir());

        setFoldSummary(sharedPreferences);

        this.onContentChanged();
    }

    private ServiceConnection musicConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d("Settings", "onServiceConnected");
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicSrv = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("Settings", "onServiceDisconnected");
            serviceBound = false;
        }
    };

    @Override
    protected void onDestroy() {
        unbindService(musicConnection);
        serviceBound = false;
        musicSrv = null;
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(!serviceBound)
            return;
        Log.d("MusicService", "onSharedPreferenceChanged: " + key);

        if(key.equals(PrefKeys.DEFAULT_FOLD.name())) {
            setFoldSummary(sharedPreferences);
        }
        else if(key.equals(PrefKeys.TEXT_SIZE_GROUP.name())) {
            String strTextSize = sharedPreferences.getString(key,
                    getString(R.string.settings_text_size_group_default));
            findPreference(key).setSummary(strTextSize);
            RowGroup.textSize = Integer.valueOf(strTextSize);
            // todo a bit dirty, we actually just need to call Adapter.notifyDataSetChanged
            musicSrv.setChanged();
        }
        else if(key.equals(PrefKeys.TEXT_SIZE_SONG.name())) {
            String strTextSize = sharedPreferences.getString(key,
                    getString(R.string.settings_text_size_song_default));
            findPreference(key).setSummary(strTextSize);
            RowSong.textSize = Integer.valueOf(strTextSize);
            musicSrv.setChanged();
        }
        else if(key.equals(PrefKeys.ENABLE_SHAKE.name())) {
            musicSrv.setEnableShake(getShakePref(sharedPreferences));
        }
        else if(key.equals(PrefKeys.SHAKE_THRESHOLD.name())) {
            String strThreshold = sharedPreferences.getString(key,
                    getString(R.string.settings_default_shake_threshold));
            float threshold = Float.valueOf(strThreshold) / 10.0f;
            musicSrv.setShakeThreshold(threshold);
            Log.d("MusicService", "Set shake threshold to: " + threshold);

            findPreference(key).setSummary(strThreshold);
            this.onContentChanged();
        }
        else if(key.equals(PrefKeys.ROOT_FOLDER.name())) {
            String rootFolder = sharedPreferences.getString(key, getDefaultMusicDir());
            findPreference(key).setSummary(rootFolder);
            if(!(new File(rootFolder)).exists())
                Toast.makeText(getApplicationContext(),
                        "! The path '" + rootFolder + "' does not exists on the phone !",
                        Toast.LENGTH_LONG).show();
            boolean reinited = musicSrv.getRows().setRootFolder(rootFolder);
            if(reinited)
                musicSrv.setChanged();
        }
    }

    static public String getDefaultMusicDir() {
        String musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getPath();
        if (!musicDir.endsWith(File.separator))
            musicDir += File.separator;
        return musicDir;
    }

    static public boolean getShakePref(SharedPreferences sharedPreferences) {
        return sharedPreferences.getBoolean(PrefKeys.ENABLE_SHAKE.name(), false);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if(preference.getKey().equals(getResources().getString(R.string.settings_rescan_key))) {
            rescan();
        } else if (preference.getKey().equals("donate")) {
            showDonateWebsite();
        }
        return false;
    }

    static public int getFoldPref(SharedPreferences sharedPreferences) {
        return Integer.valueOf(sharedPreferences.getString(PrefKeys.DEFAULT_FOLD.name(), "0"));
    }

    private void setFoldSummary(SharedPreferences sharedPreferences) {
        ListPreference prefFold = (ListPreference) findPreference(PrefKeys.DEFAULT_FOLD.name());
        int idx = getFoldPref(sharedPreferences);
        prefFold.setSummary((getResources().getStringArray(R.array.settings_fold_entries))[idx]);
    }

    private void showDonateWebsite() {
        Intent webIntent = new Intent(Intent.ACTION_VIEW);
        webIntent.setData(Uri.parse(getString(R.string.settings_donate_www)));
        this.startActivity(webIntent);
    }

    public void rescan() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Toast.makeText(getApplicationContext(),
                "Rescan broadcast disabled from Android KitKat.", Toast.LENGTH_LONG).show();
            return;
        }

        // Broadcast the Media Scanner Intent to trigger it
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri
                .parse("file://" + Environment.getExternalStorageDirectory())));
        Toast toast = Toast.makeText(getApplicationContext(),
                "Media Scanner Triggered...", Toast.LENGTH_SHORT);
        toast.show();

        // todo: should be improved with this?
        // http://stackoverflow.com/questions/13270789/how-to-run-media-scanner-in-android
        // careful from hitkat 4.4+ :
        // http://stackoverflow.com/questions/24072489/java-lang-securityexception-permission-denial-not-allowed-to-send-broadcast-an
    }
}
