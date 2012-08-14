package com.mamewo.podplayer0;

/**
 * @author Takashi Masuyama <mamewotoko@gmail.com>
 * http://www002.upp.so-net.ne.jp/mamewo/
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ImageView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.mamewo.podplayer0.PlayerService.PodInfo;

public class PodplayerExpActivity
	extends BasePodplayerActivity
	implements OnClickListener,
	ServiceConnection,
	OnItemLongClickListener,
	PlayerService.PlayerStateListener,
	OnSharedPreferenceChangeListener,
	OnChildClickListener
{
	private ToggleButton playButton_;
	private ImageView reloadButton_;
	private Button expandButton_;
	private Button collapseButton_;
	private ExpandableListView expandableList_;
	private SimpleExpandableListAdapter expandableAdapter_;
	private int allIndex2viewIndex_[];

	private List<Map<String,String>> groupData_;
	private List<List<Map<String, Object>>> childData_;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState, this);
		setContentView(R.layout.expandable_main);
		reloadButton_ = (ImageView) findViewById(R.id.reload_button);
		reloadButton_.setOnClickListener(this);
		playButton_ = (ToggleButton) findViewById(R.id.play_button);
		playButton_.setOnClickListener(this);
		playButton_.setEnabled(false);
		expandableList_ =
				(ExpandableListView) findViewById(R.id.exp_list);
		expandableList_.setOnItemLongClickListener(this);
		expandButton_ = (Button) findViewById(R.id.expand_button);
		expandButton_.setOnClickListener(this);
		collapseButton_ = (Button) findViewById(R.id.collapse_button);
		collapseButton_.setOnClickListener(this);
		groupData_ = new ArrayList<Map<String, String>>();
		childData_ = new ArrayList<List<Map<String, Object>>>();
		allIndex2viewIndex_ = new int[allTitles_.length];
	}

	//TODO: fetch current playing episode to update currentPodInfo
	@Override
	public void onResume(){
		super.onResume();
		//stop loading?
		for(int i = 0; i < allIndex2viewIndex_.length; i++) {
			allIndex2viewIndex_[i] = -1;
		}
		int j = 0;
		groupData_.clear();
		childData_.clear();
		for (int i = 0; i < state_.podcastURLList_.size(); i++) {
			String podcastURL = state_.podcastURLList_.get(i).toString();
			for ( ; j < allURLs_.length; j++) {
				if(podcastURL.equals(allURLs_[j])) {
					Map<String, String> groupItem = new HashMap<String, String>();
					allIndex2viewIndex_[j] = i;
					groupItem.put("TITLE", allTitles_[j++]);
					groupData_.add(groupItem);
					childData_.add(new ArrayList<Map<String, Object>>());
					break;
				}
			}
		}
		expandableAdapter_ = new ExpAdapter(
				this,
				groupData_,
				android.R.layout.simple_expandable_list_item_1,
				new String[] {"TITLE"},
				new int[] { android.R.id.text1 },
				childData_,
				R.layout.episode_item,
				new String[] {"TITLE"},
				new int[] { R.id.episode_title });
		expandableList_.setAdapter(expandableAdapter_);
		SharedPreferences pref =
				PreferenceManager.getDefaultSharedPreferences(this);
		boolean expandInDefault = pref.getBoolean("expand_in_default", true);
		//TODO: only when start is called?
		if (expandInDefault) { 
			expandOrCollapseAll(true);
		}
		expandableList_.setOnChildClickListener(this);
		boolean doLoad = pref.getBoolean("load_on_start", true);
		updateUI();
		if(doLoad){
			loadPodcast();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putSerializable("state", state_);
	}

	private void updateUI() {
		if(null == player_) {
			return;
		}
		expandableAdapter_.notifyDataSetChanged();
		playButton_.setChecked(player_.isPlaying());
	}

	//must be called from UI thread
	private void loadPodcast(){
		if (isLoading()) {
			Log.d(TAG, "Already loading");
			return;
		}
		reloadButton_.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
		for (int i = 0; i < childData_.size(); i++) {
			childData_.get(i).clear();
		}
		updateUI();
		SharedPreferences pref=
				PreferenceManager.getDefaultSharedPreferences(this);
		boolean showPodcastIcon = pref.getBoolean("show_episode_icon", true);
		int timeout = Integer.valueOf(pref.getString("read_timeout", "30"));
		loadTask_ = new GetPodcastTask(showPodcastIcon, timeout);
		loadTask_.execute(state_.podcastURLList_.toArray(DUMMY_URL_LIST));
	}

	@Override
	public void onClick(View v) {
		//add option to load onStart
		if (v == playButton_) {
			if(player_.isPlaying()) {
				player_.pauseMusic();
			}
			else {
				updatePlaylist();
				if(! player_.restartMusic()) {
					player_.playMusic();
				}
			}
			playButton_.setChecked(player_.isPlaying());
		}
		else if (v == reloadButton_) {
			if (isLoading()) {
				loadTask_.cancel(true);
			}
			else {
				loadPodcast();
			}
		}
		else if (v == expandButton_) {
			for (int i = 0; i < groupData_.size(); i++) {
				expandOrCollapseAll(true);
			}
		}
		else if (v == collapseButton_) {
			for (int i = 0; i < groupData_.size(); i++) {
				expandOrCollapseAll(false);
			}
		}
	}

	private void expandOrCollapseAll(boolean expand) {
		for (int i = 0; i < groupData_.size(); i++) {
			if (expand) {
				expandableList_.expandGroup(i);
			}
			else {
				expandableList_.collapseGroup(i);
			}
		}
	}
	
	@Override
	public void onServiceConnected(ComponentName name, IBinder binder) {
		player_ = ((PlayerService.LocalBinder)binder).getService();
		player_.setOnStartMusicListener(this);
		playButton_.setEnabled(true);
		updateUI();
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		player_.clearOnStartMusicListener();
		player_ = null;
	}

	@Override
	public boolean onChildClick (ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
		//refresh header is added....
		@SuppressWarnings("unchecked")
		HashMap<String,Object> map =
			(HashMap<String, Object>) expandableAdapter_.getChild(groupPosition, childPosition);
		PodInfo info = (PodInfo)map.get("DATA");
		PodInfo current = player_.getCurrentPodInfo();
		if(current != null && current.url_.equals(info.url_)) {
			if(player_.isPlaying()) {
				player_.pauseMusic();
			}
			else {
				if(! player_.restartMusic()){
					playByInfo(info);
				}
			}
		}
		else {
			updatePlaylist();
			playByInfo(info);
		}
		return true;
	}

	private void playByInfo(PodInfo info) {
		//umm...
		int playPos = -1;
		for(playPos = 0; playPos < state_.loadedEpisode_.size(); playPos++) {
			if(state_.loadedEpisode_.get(playPos) == info) {
				break;
			}
		}
		if (playPos < 0){
			Log.i(TAG, "playByInfo: info is not found: " + info.url_);
			return;
		}
		player_.playNth(playPos);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.mainmenu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		boolean handled = false;
		switch(item.getItemId()) {
		case R.id.exit_menu:
			finishServiceOnExit_ = true;
			finish();
			handled = true;
			break;
		case R.id.pref_menu:
			startActivity(new Intent(this, PodplayerPreference.class));
			handled = true;
			break;
		default:
			break;
		}
		return handled;
	}
	
	public class ExpAdapter
		extends SimpleExpandableListAdapter {

		public ExpAdapter(Context context,
				List<? extends Map<String, ?>> groupData,
				int groupLayout,
				String[] groupFrom, int[] groupTo,
				List<? extends List<? extends Map<String, ?>>> childData,
				int childLayout, String[] childFrom,
				int[] childTo) {
			super(context, groupData, groupLayout, groupFrom,
					groupTo, childData, childLayout, childFrom, childTo);
		}

		//TODO: optimize
		@Override
		public View getChildView (int groupPosition, int childPosition, boolean isLastChild,
								View convertView, ViewGroup parent)
		{
			int childNum = getChildrenCount(groupPosition);
			if(childPosition > childNum) {
				return null;
			}
			View view;
			if (null == convertView) {
				view = View.inflate(PodplayerExpActivity.this, R.layout.episode_item, null);
			}
			else {
				view = convertView;
			}
			@SuppressWarnings("unchecked")
			HashMap<String, Object> map = (HashMap<String, Object>)getChild(groupPosition, childPosition);
			PodInfo info = (PodInfo)map.get("DATA");
			TextView titleView = (TextView)view.findViewById(R.id.episode_title);
			TextView timeView = (TextView)view.findViewById(R.id.episode_time);
			titleView.setText(info.title_);
			timeView.setText(info.pubdate_);
			ImageView stateIcon = (ImageView)view.findViewById(R.id.play_icon);
			ImageView episodeIcon = (ImageView)view.findViewById(R.id.episode_icon);
			PodInfo current = player_.getCurrentPodInfo();
			if(current != null && current.url_.equals(info.url_)) {
				//cache!
				if(player_.isPlaying()) {
					stateIcon.setImageResource(android.R.drawable.ic_media_play);
				}
				else {
					stateIcon.setImageResource(android.R.drawable.ic_media_pause);
				}
				stateIcon.setVisibility(View.VISIBLE);
			}
			else {
				stateIcon.setVisibility(View.GONE);
			}
			if(showPodcastIcon_ && null != iconData_[info.index_]){
				episodeIcon.setImageDrawable(iconData_[info.index_]);
				episodeIcon.setVisibility(View.VISIBLE);
			}
			else {
				episodeIcon.setVisibility(View.GONE);
			}
			return view;
		}
	}

	//UI is updated in following callback methods
	@Override
	public void onStartMusic(PodInfo info) {
		updateUI();
	}

	@Override
	public void onStartLoadingMusic(PodInfo info) {
		updateUI();
	}

	@Override
	public void onStopMusic(int mode) {
		Log.d(TAG, "onStopMusic");
		updateUI();
	}
	// end of callback methods

	private class GetPodcastTask
		extends BaseGetPodcastTask
	{
		public GetPodcastTask(boolean showPodcastIcon, int timeout) {
			super(PodplayerExpActivity.this, allURLs_, state_.iconURLs_, iconData_, showPodcastIcon, timeout);
		}

		@Override
		protected void onProgressUpdate(PodInfo... values){
			for (int i = 0; i < values.length; i++) {
				PodInfo info = values[i];
				//TODO: remove?
				state_.loadedEpisode_.add(info);
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("TITLE", info.title_);
				map.put("DATA", info);
				childData_.get(allIndex2viewIndex_[info.index_]).add(map);
			}
			updateUI();
		}

		@Override
		protected void onPostExecute(Void result) {
			loadTask_ = null;
			//TODO: Sync playlist
			updatePlaylist();
			reloadButton_.setImageResource(android.R.drawable.ic_popup_sync);
		}
		
		@Override
		protected void onCancelled() {
			//TODO: show toast?
			loadTask_ = null;
			reloadButton_.setImageResource(android.R.drawable.ic_popup_sync);
		}
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> adapter, View view, int pos, long id) {
		SharedPreferences pref=
				PreferenceManager.getDefaultSharedPreferences(this);
		boolean enableLongClick = pref.getBoolean("enable_long_click", false);
		if (! enableLongClick) {
			return false;
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> map =
				(Map<String, Object>)adapter.getItemAtPosition(pos);
		PodInfo info = (PodInfo)map.get("DATA");
		if (null == info) {
			//parent is long clicked
			return false;
		}
		if ((! enableLongClick) || null == info.link_) {
			return false;
		}
		//TODO: add preference to enable this 
		Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		if (vibrator != null) {
			vibrator.vibrate(100);
		}
		Intent i =
				new Intent(Intent.ACTION_VIEW, Uri.parse(info.link_));
		startActivity(new Intent(i));
		return true;
	}
}