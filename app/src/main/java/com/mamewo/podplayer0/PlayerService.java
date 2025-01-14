package com.mamewo.podplayer0;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.mamewo.podplayer0.db.PodcastRealm;
import com.mamewo.podplayer0.db.EpisodeRealm;
import com.mamewo.podplayer0.db.SimpleQuery;
import com.mamewo.podplayer0.parser.Util;
import io.realm.RealmResults;

import static com.mamewo.podplayer0.Const.*;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.preference.PreferenceManager;
import com.mamewo.podplayer0.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

public class PlayerService
	extends Service
	implements MediaPlayer.OnCompletionListener,
	MediaPlayer.OnErrorListener,
    MediaPlayer.OnPreparedListener,
	AudioManager.OnAudioFocusChangeListener
{
	final static
	public String PACKAGE_NAME = PlayerService.class.getPackage().getName();
	final static
	public String START_MUSIC_ACTION = PACKAGE_NAME + ".START_MUSIC_ACTION";
	final static
	public String STOP_MUSIC_ACTION = PACKAGE_NAME + ".STOP_MUSIC_ACTION";
	final static
	public String PREVIOUS_MUSIC_ACTION = PACKAGE_NAME + ".PREVIOUS_MUSIC_ACTION";
	final static
	public String NEXT_MUSIC_ACTION = PACKAGE_NAME + ".NEXT_MUSIC_ACTION";
	final static
	public String JACK_UNPLUGGED_ACTION = PACKAGE_NAME + ".JUCK_UNPLUGGED_ACTION";
	final static
	public String MEDIA_BUTTON_ACTION = PACKAGE_NAME + ".MEDIA_BUTTON_ACTION";
	final static
	public int STOP = 1;
	final static
	public int PAUSE = 2;
	final static
	private int PREV_INTERVAL_MILLIS = 3000;
	final static
	private Class<MainActivity> USER_CLASS = MainActivity.class;
	final static
	private int NOTIFY_PLAYING_ID = 1;
	private final IBinder binder_ = new LocalBinder();
	private RealmResults<PodcastRealm> podcastList_;
	private RealmResults<EpisodeRealm> currentPlaylist_;
	private int playCursor_;
	private MediaPlayer player_;
	private PlayerStateListener listener_;
	private Receiver receiver_;
	private boolean isPreparing_;
	private boolean stopOnPrepared_;
	private boolean isPausing_;
	private ComponentName mediaButtonReceiver_;
	private long previousPrevKeyTime_;
	private EpisodeRealm currentPlaying_;
	private	SharedPreferences pref_;

	final static
	private Class<?> userClass_ = MainActivity.class;

	//error code
	//error code from base/include/media/stagefright/MediaErrors.h
	final static
	private int MEDIA_ERROR_BASE = -1000;
	final static
	private int ERROR_ALREADY_CONNECTED = MEDIA_ERROR_BASE;
	final static
	private int ERROR_NOT_CONNECTED = MEDIA_ERROR_BASE - 1;
	final static
	private int ERROR_UNKNOWN_HOST = MEDIA_ERROR_BASE - 2;
	final static
	private int ERROR_CANNOT_CONNECT = MEDIA_ERROR_BASE - 3;
	final static
	private int ERROR_IO = MEDIA_ERROR_BASE - 4;
	final static
	private int ERROR_CONNECTION_LOST = MEDIA_ERROR_BASE - 5;
	final static
	private int ERROR_MALFORMED = MEDIA_ERROR_BASE - 7;
	final static
	private int ERROR_OUT_OF_RANGE = MEDIA_ERROR_BASE - 8;
	final static
	private int ERROR_BUFFER_TOO_SMALL = MEDIA_ERROR_BASE - 9;
	final static
	private int ERROR_UNSUPPORTED = MEDIA_ERROR_BASE - 10;
	final static
	private int ERROR_END_OF_STREAM = MEDIA_ERROR_BASE - 11;
	// Not technically an error.
	final static
	private int INFO_FORMAT_CHANGED = MEDIA_ERROR_BASE - 12;
	final static
	private int INFO_DISCONTINUITY = MEDIA_ERROR_BASE - 13;

	//TODO: check
	// static
	// public boolean isNetworkConnected(Context context) {
	// 	ConnectivityManager connMgr =
	// 			(ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
	// 	NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
	// 	return (networkInfo != null && networkInfo.isConnected());
	// }

    //TODO: pass query
	public void setPlaylistQuery(String title, boolean skipListened, int order) {
		//currentPlaylist_ = playlist;
        //add test
        SimpleQuery query = new SimpleQuery(title, skipListened, order);
        podcastList_ = query.getPodcastList();
        currentPlaylist_ = query.getEpisodeList();
	}

	//previous tune when previous is clicked twice quickly
	public void playPrevious(){
		long currentTime = System.currentTimeMillis();
		Log.d(TAG, "Interval: " + (currentTime - previousPrevKeyTime_));
		if ((currentTime - previousPrevKeyTime_) <= PREV_INTERVAL_MILLIS) {
			if(0 == playCursor_){
				playCursor_ = currentPlaylist_.size() - 1;
			}
			else {
				playCursor_--;
			}
			playNth(playCursor_);
		}
		else {
			//rewind
			playMusic();
		}
		previousPrevKeyTime_ = currentTime;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		//null intent is passed when service is restarted after killed on Android 4.0
		if (null == intent) {
			Log.d(TAG, "onStartCommand: intent is null");
			return START_STICKY;
		}
		String action = intent.getAction();
		Log.d(TAG, "onStartCommand: " + action);
		if (null == action) {
			return START_STICKY;
		}
		if (START_MUSIC_ACTION.equals(action)) {
			restartMusic();
		}
		else if (STOP_MUSIC_ACTION.equals(action)) {
			//stopMusic();
			pauseMusic();
		}
		else if(NEXT_MUSIC_ACTION.equals(action)){
			//TODO: move cursor to next if not playing
			if (player_.isPlaying()) {
				playNext();
			}
		}
		else if(PREVIOUS_MUSIC_ACTION.equals(action)){
			//TODO: move cursor to previous if not playing
			if (player_.isPlaying()) {
				playPrevious();
			}
		}
		else if (JACK_UNPLUGGED_ACTION.equals(action)) {
			Resources res = getResources();
			boolean pause = pref_.getBoolean("pause_on_unplugged", res.getBoolean(R.bool.default_pause_on_unplugged));
			if (pause && null != player_ && player_.isPlaying()) {
				pauseMusic();
			}
		}
		else if (MEDIA_BUTTON_ACTION.equals(action)) {
			KeyEvent event = intent.getParcelableExtra("event");
			Log.d(TAG, "SERVICE: Received media button: " + event.getKeyCode());
			if (event.getAction() != KeyEvent.ACTION_UP) {
				return START_STICKY;
			}
			switch(event.getKeyCode()) {
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
				if (player_.isPlaying()){
					pauseMusic();
				}
				else {
					playMusic();
				}
				break;
			case KeyEvent.KEYCODE_MEDIA_NEXT:
				if (player_.isPlaying()) {
					playNext();
				}
				break;
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
				if (player_.isPlaying()) {
					playPrevious();
				}
				break;
			default:
				break;
			}
		}
		return START_STICKY;
	}

	public boolean isPlaying() {
        if(null == player_){
            return false;
        }
		return (! stopOnPrepared_) && (isPreparing_ || player_.isPlaying());
	}

	public RealmResults<EpisodeRealm> getCurrentPlaylist() {
		return currentPlaylist_;
	}

	/**
	 * get current playing or pausing music
	 * @return current music info
	 */
	public EpisodeRealm getCurrentPodInfo(){
		if (null != currentPlaylist_) {
			return currentPlaying_;
		}
		return null;
	}

	public boolean playNext() {
		Log.d(TAG, "playNext");
		if(currentPlaylist_ == null || currentPlaylist_.size() == 0) {
			return false;
		}
		if (player_.isPlaying()) {
			player_.pause();
		}
		playCursor_ = (playCursor_ + 1) % currentPlaylist_.size();
		return playMusic();
	}

	/**
	 * plays music of given index
	 * @param pos index on currentPlayList_
	 * @return true if succeed
	 */
	public boolean playNth(int pos) {
		if(currentPlaylist_ == null || currentPlaylist_.size() == 0){
			Log.d(TAG, "playNth: currentPlaylist_: " + currentPlaylist_);
			return false;
		}
		if(isPreparing_){
			Log.d(TAG, "playNth: preparing");
			return false;
		}
		isPausing_ = false;
		playCursor_ = pos % currentPlaylist_.size();
		return playMusic();
	}

	public boolean playById(long id) {
        int pos = -1;
        for(int i = 0; i < currentPlaylist_.size(); i++){
            if(id == currentPlaylist_.get(i).getId()){
                pos = i;
                break;
            }
        }
        if(pos < 0){
            Log.d(TAG, "playById: not found: "+id);
        }
		return playNth(pos);
    }


	/**
	 * start music from paused position
	 * @return true if succeed
	 */
	public boolean restartMusic() {
		Log.d(TAG, "restartMusic: " + isPausing_);
		if(! isPausing_) {
			return false;
		}
		if(isPreparing_) {
			stopOnPrepared_ = false;
			//TODO: is this correct? false?
			return true;
		}
		AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		int result = manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
		if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED){
			return false;
		}
		player_.start();
		EpisodeRealm info = currentPlaylist_.get(playCursor_);
		if(null != listener_){
			listener_.onStartMusic(info.getId());
		}
		showNotification(info.getTitle());
		return true;
	}

	/**
	 * play current music from beginning
	 * @return true if succeed
	 */
	public boolean playMusic() {
		if (isPreparing_) {
			Log.d(TAG, "playMusic: preparing");
			stopOnPrepared_ = false;
			return false;
		}
		if (null == currentPlaylist_ || currentPlaylist_.isEmpty()) {
			Log.i(TAG, "playMusic: playlist is null");
			return false;
		}
		currentPlaying_ = currentPlaylist_.get(playCursor_);
		Log.d(TAG, "playMusic: " + playCursor_ + ": " + currentPlaying_.getURL());
		try {
            player_.reset();
            String username = currentPlaying_.getPodcast().getUsername();
            String password = currentPlaying_.getPodcast().getPassword();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                Uri uri = Uri.parse(currentPlaying_.getURL());
                Map<String, String> header = null;
                if(null != username && null != password){
                    String authHeader = Util.makeHTTPAuthorizationHeader(username, password);
                    header = new HashMap<String,String>();
                    header.put("Authorization", authHeader);
                }
                //player_.setDataSource(this, uri, header);
                //TODO: support header
                String url = currentPlaying_.getURL();
                player_.setDataSource(url);
            }
            else {
                String url = currentPlaying_.getURL();
                //TODO: try add auth info to url
                // Uri uri;
                // if(null != username && null != password){
                //     uri = Uri.parse(currentPlaying_.getURLWithAuthInfo());
                //     Log.d(TAG, "setDatasource: input: "+uri.toString()+" "+uri.getUserInfo());
                // }
                // else {
                //     uri = Uri.parse(url);
                // }
                // player_.setDataSource(this, uri);
                player_.setDataSource(url);
            }
			player_.prepareAsync();
			isPreparing_ = true;
			isPausing_ = false;
		}
		catch (IOException e) {
            Log.d(TAG, "playMusic IOException", e);
			return false;
		}
		if(null != listener_){
			listener_.onStartLoadingMusic(currentPlaying_.getId());
		}
		return true;
	}

	public void stopMusic() {
		if (isPreparing_) {
			stopOnPrepared_ = true;
		}
		else if(player_.isPlaying()){
			AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			manager.abandonAudioFocus(this);
			player_.stop();
		}
		isPausing_ = false;
		stopForeground(false);
		if(null != listener_){
			Log.d(TAG, "call onStopMusic");
			listener_.onStopMusic(STOP);
		}
	}

	//TODO: correct paused state
	public void pauseMusic() {
        if(!player_.isPlaying()){
            return;
        }
		Log.d(TAG, "pauseMusic: " + player_.isPlaying());
		if (isPreparing_) {
			stopOnPrepared_ = true;
		}
		else if(player_.isPlaying()){
			AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			manager.abandonAudioFocus(this);
			player_.pause();
		}
		isPausing_ = true;
		stopForeground(false);
        //TODO: update notificatoion (button)
        showNotification(currentPlaying_.getTitle());
		if(null != listener_){
			listener_.onStopMusic(PAUSE);
		}
	}

	public class LocalBinder
		extends Binder
	{
		public PlayerService getService() {
			return PlayerService.this;
		}
	}

	@Override
	public void onCreate(){
		super.onCreate();
		Log.d(TAG, "PlayerService.onCreate is called");
		currentPlaylist_ = null;
		currentPlaying_ = null;
		listener_ = null;
		pref_ =	PreferenceManager.getDefaultSharedPreferences(this);
		player_ = new MediaPlayer();
		player_.setOnCompletionListener(this);
		player_.setOnErrorListener(this);
		player_.setOnPreparedListener(this);
		receiver_ = new Receiver();
		registerReceiver(receiver_, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
		registerReceiver(receiver_, new IntentFilter(STOP_MUSIC_ACTION));
		isPreparing_ = false;
		stopOnPrepared_ = false;
		isPausing_ = false;
		playCursor_ = 0;
		previousPrevKeyTime_ = 0;
		AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		mediaButtonReceiver_ = new ComponentName(getPackageName(), Receiver.class.getName());
		manager.registerMediaButtonEventReceiver(mediaButtonReceiver_);
	}

	@Override
	public void onDestroy() {
		AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		manager.unregisterMediaButtonEventReceiver(mediaButtonReceiver_);
		unregisterReceiver(receiver_);
		stopForeground(true);
		player_.setOnCompletionListener(null);
		player_.setOnErrorListener(null);
		player_.setOnPreparedListener(null);
		player_ = null;
		listener_ = null;
		currentPlaylist_ = null;
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder_;
	}

	//TODO: show podcast title / episode title
	//TODO: support old android < O
	private void showNotification(String episodeTitle) {
		// String channelId = "";
		// if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		// 	channelId = createNotificationChannel("com.mamewo.podplayer0", "PlayerService");
		// }
		String NOTIFICATION_CHANNEL_ID = "com.mamewo.podplayer0";
		String channelName = "PodPlayer";
		NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
															  channelName,
															  NotificationManager.IMPORTANCE_DEFAULT);
		NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		manager.createNotificationChannel(channel);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle(episodeTitle)
            .setAutoCancel(false)
            .setOngoing(true);

        Intent resultIntent = new Intent(this, userClass_);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(userClass_);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent);
        Notification note = builder.build();
        startForeground(NOTIFY_PLAYING_ID, note);
		
		// RemoteViews rvs = new RemoteViews(getClass().getPackage().getName(), R.layout.notification);
		// rvs.setTextViewText(R.id.notification_title, episodeTitle);

		// Intent pauseIntent = new Intent(this, getClass());
		// if(isPlaying()){
		// 	pauseIntent.setAction(STOP_MUSIC_ACTION);
		// 	rvs.setImageViewResource(R.id.notification_pause, android.R.drawable.ic_media_pause);
		// 	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
		// 		rvs.setContentDescription(R.id.notification_pause, getString(R.string.notification_icon_desc_pause));
		// 	}
		// }
		// else {
		// 	pauseIntent.setAction(START_MUSIC_ACTION);
		// 	rvs.setImageViewResource(R.id.notification_pause, android.R.drawable.ic_media_play);
		// 	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
		// 		rvs.setContentDescription(R.id.notification_pause, getString(R.string.notification_icon_desc_play));
		// 	}
		// }
		// PendingIntent pausePendingIntent = PendingIntent.getService(this, 0, pauseIntent, 0);
		// rvs.setOnClickPendingIntent(R.id.notification_pause, pausePendingIntent);

		// Intent prevIntent = new Intent(this, getClass());
		// prevIntent.setAction(PREVIOUS_MUSIC_ACTION);
		// PendingIntent previousPendingIntent = PendingIntent.getService(this, 0, prevIntent, 0);
		// rvs.setOnClickPendingIntent(R.id.notification_previous, previousPendingIntent);

		// Intent nextIntent = new Intent(this, getClass());
		// nextIntent.setAction(NEXT_MUSIC_ACTION);
		// PendingIntent nextPendingIntent = PendingIntent.getService(this, 0, nextIntent, 0);
		// rvs.setOnClickPendingIntent(R.id.notification_next, nextPendingIntent);

		// //for backward compatibility
		// NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
		// 	.setSmallIcon(R.drawable.ic_status)
		// 	.setContentTitle(getString(R.string.app_name))
		// 	.setContentText(episodeTitle)
        //     .setTicker(episodeTitle)
		// 	.setAutoCancel(false)
		// 	.setOngoing(true)
		// 	.setContent(rvs);
		// //.setForegroundService(true)
        // //.setCategory(Notification.CATEGORY_TRANSPORT)
		// Intent resultIntent = new Intent(this, USER_CLASS);
		// TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		// stackBuilder.addParentStack(USER_CLASS);
		// stackBuilder.addNextIntent(resultIntent);
		// PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
		// builder.setContentIntent(resultPendingIntent);
		// Notification note = builder.build();
		// startForeground(NOTIFY_PLAYING_ID, note);
	}

	@Override
	public void onPrepared(MediaPlayer player) {
		Log.d(TAG, "onPrepared");
		isPreparing_ = false;
		if(stopOnPrepared_) {
			Log.d(TAG, "onPrepared aborted");
			stopOnPrepared_ = false;
			return;
		}
		AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		int result = manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
		if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED){
			return;
		}
		player_.start();
        showNotification(currentPlaying_.getTitle());
		if(null != listener_){
			listener_.onStartMusic(currentPlaying_.getId());
		}
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
        listener_.onCompleteMusic(currentPlaying_.getId());
		playNext();
	}

	private String ErrorCode2String(int err) {
		String result;
		switch(err){
		//TODO: localize?
		case ERROR_ALREADY_CONNECTED:
			result = "Already Connected";
			break;
		case ERROR_NOT_CONNECTED:
			result = "Not Connected";
			break;
		case ERROR_UNKNOWN_HOST:
			result = "Unknown Host";
			break;
		case ERROR_CANNOT_CONNECT:
			result = "Cannot Connect";
			break;
		case ERROR_IO:
			result = "I/O Error";
			break;
		case ERROR_CONNECTION_LOST:
			result = "Connection Lost";
			break;
		case ERROR_MALFORMED:
			result = "Malformed Media";
			break;
		case ERROR_OUT_OF_RANGE:
			result = "Out of Range";
			break;
		case ERROR_BUFFER_TOO_SMALL:
			result = "Buffer too Small";
			break;
		case ERROR_UNSUPPORTED:
			result = "Unsupported Media";
			break;
		case ERROR_END_OF_STREAM:
			result = "End of Stream";
			break;
		case INFO_FORMAT_CHANGED:
			result = "Format Changed";
			break;
		case INFO_DISCONTINUITY:
			result = "Info Discontinuity";
			break;
		default:
			result = "Unknown error: " + err;
			break;
		}
		return result;
	}

	// This method is not called when DRM error occurs
	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		String code = ErrorCode2String(extra);
		EpisodeRealm info = currentPlaying_;
		Log.i(TAG, "onError: what: " + what + " error code: " + code + " url: " + info.getURL());
		//TODO: show error message to GUI
		isPreparing_ = false;
		//TODO: localize
		showMessage("Network error: " + code);
		//TODO: if next item is different, continue playing
		boolean stopOnError = pref_.getBoolean("stop_playing_on_error", false);
		if(stopOnError){
			stopMusic();
		}
		else {
			//TODO: if next item is different
			if(currentPlaylist_.size() > 1){
				playNext();
			}
		}
		return true;
	}

	@Override
	public void onAudioFocusChange(int focusChange){
		AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

		switch(focusChange){
		case AudioManager.AUDIOFOCUS_GAIN:
			Log.d(TAG, "AUDIOFOCUS_GAIN");
			//TODO: test stop -> gain / playing -> gain
			manager.registerMediaButtonEventReceiver(mediaButtonReceiver_);
			restartMusic();
			break;
		case AudioManager.AUDIOFOCUS_LOSS:
			Log.d(TAG, "AUDIOFOCUS_LOSS");
			pauseMusic();
			manager.unregisterMediaButtonEventReceiver(mediaButtonReceiver_);
			break;
		default:
			break;
		}
	}

	//use intent instead?
	public void setOnStartMusicListener(PlayerStateListener listener) {
		listener_ = listener;
	}

//	public int getDuration(){
//		return player_.getDuration();
//	}
//
//	public int getCurrentPositionMsec(){
//		return player_.getCurrentPosition();
//	}
//
//	public void seekTo(int msec){
//		player_.seekTo(msec);
//	}

	public void clearOnStartMusicListener() {
		listener_ = null;
	}

	public interface PlayerStateListener {
		void onStartLoadingMusic(long episodeId);
        void onCompleteMusic(long episodeId);
		void onStartMusic(long episodeId);
		void onStopMusic(int mode);
	}

	final static
	public class Receiver
		extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Log.d(TAG, "onReceive is called: " + action);
			if (null == action) {
				return;
			}
			if(Intent.ACTION_HEADSET_PLUG.equals(action)) {
				if(intent.getIntExtra("state", 1) == 0) {
					//unplugged
					Intent i = new Intent(context, PlayerService.class);
					i.setAction(JACK_UNPLUGGED_ACTION);
					context.startService(i);
				}
			}
			else if(Intent.ACTION_MEDIA_BUTTON.equals(action)) {
				Log.d(TAG, "media button");
				Intent i = new Intent(context, PlayerService.class);
				i.setAction(MEDIA_BUTTON_ACTION);
				i.putExtra("event", (Serializable)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT));
				context.startService(i);
			}
		}
	}

	public void showMessage(String message) {
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}
}
