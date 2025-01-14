package com.mamewo.podplayer0;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.mamewo.podplayer0.Const.*;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import androidx.preference.PreferenceManager;
import com.mamewo.podplayer0.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import com.mamewo.podplayer0.parser.BaseGetPodcastTask;
import com.markupartist.android.widget.PullToRefreshListView;

import com.mamewo.podplayer0.db.PodcastRealm;
import com.mamewo.podplayer0.db.EpisodeRealm;
import com.mamewo.podplayer0.db.SimpleQuery;
import io.realm.RealmResults;
import io.realm.RealmChangeListener;
import io.realm.Realm;
//import io.realm.RealmChangeListener;

import com.bumptech.glide.Glide;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.ActionBar;
import android.widget.ImageButton;

public class PodplayerActivity
    extends BasePodplayerActivity
    implements OnClickListener,
               OnItemClickListener,
               OnItemLongClickListener,
               OnItemSelectedListener,
               PlayerService.PlayerStateListener,
               PullToRefreshListView.OnRefreshListener,
               PullToRefreshListView.OnCancelListener,
               SimpleQuery.DataChangeListener
               //SeekBar.OnSeekBarChangeListener           
{
    private ImageButton playButton_;
    private Spinner selector_;
    private PullToRefreshListView episodeListView_;
    //adapter_: filtered view
    //private SeekBar currentPlayPosition_;
    private EpisodeAdapter adapter_;
    private SimpleQuery currentQuery_;

    //number of items for one screen (small phone)
    static final
    public int EPISODE_BUF_SIZE = 10;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionbar = getSupportActionBar();
        actionbar.setDisplayShowTitleEnabled(false);

		playButton_ = (ImageButton) findViewById(R.id.play_button);
        playButton_.setOnClickListener(this);
        playButton_.setEnabled(false);
        selector_ = (Spinner) findViewById(R.id.podcast_selector);
        selector_.setOnItemSelectedListener(this);
        episodeListView_ = (PullToRefreshListView) findViewById(R.id.list);
        episodeListView_.setOnItemClickListener(this);
        episodeListView_.setOnItemLongClickListener(this);
        episodeListView_.setOnRefreshListener(this);
        episodeListView_.setOnCancelListener(this);
        //initial dummy
        loadRealm();

        adapter_ = new EpisodeAdapter();
        episodeListView_.setAdapter(adapter_);
        //currentPlayPosition_ = (SeekBar) findViewById(R.id.seekbar);
        //currentPlayPosition_.setOnSeekBarChangeListener(this);
    }

    @Override
    public void onStart(){
        super.onStart();
        updateAndLoad();
    }
    
    @Override
    public void onDestroy(){
        Realm realm = Realm.getDefaultInstance();
        realm.close();
        super.onDestroy();
    }
    
    @Override
    public void notifyPodcastListChanged(RealmResults<PodcastRealm> results){
        updateAndLoad();
    }

    public void updateAndLoad(){
        updateSelector();
        boolean doLoad = pref_.getBoolean("load_on_start", getResources().getBoolean(R.bool.default_load_on_start));
        if(doLoad && (null == state_.lastUpdatedDate_ || adapter_.getCount() == 0)){
            episodeListView_.startRefresh();
        }
        adapter_.notifyDataSetChanged();
    }

    @Override
    public void notifyEpisodeListAllChanged(RealmResults<EpisodeRealm> results){
        adapter_.notifyDataSetChanged();
    }

    @Override
    public void notifyEpisodeListGroupChanged(long podcastId, RealmResults<EpisodeRealm> results){
        adapter_.notifyDataSetChanged();
    }

    @Override
    public void notifyQuerySettingChanged(){
        episodeListView_.hideHeader();
        loadRealm();
        adapter_.notifyDataSetChanged();
    }

    @Override
    public void notifyUISettingChanged(){
        adapter_.notifyDataSetChanged();
    }
    
    private void updateUI() {
        adapter_.notifyDataSetChanged();
        updatePlayButton();
    }

    private void loadPodcast() {
        if (isLoading()) {
            Log.i(TAG, "Already loading");
            return;
        }
        Resources res = getResources();
        GetPodcastTask task = new GetPodcastTask();
        startLoading(task);
    }

    public void loadRealm(){
        boolean skipListened = pref_.getBoolean("skip_listened_episode", getResources().getBoolean(R.bool.default_skip_listened_episode));
        int order = Integer.valueOf(pref_.getString("episode_order", "0"));
        currentQuery_ = new SimpleQuery(null, skipListened, order, this);
        for(PodcastRealm podcast: currentQuery_.getPodcastList()){
            currentQuery_.getEpisodeList(podcast.getId());
        }
    }

    private void updatePlayButton(){
        if(null == player_){
            return;
        }
        if(player_.isPlaying()){
            playButton_.setContentDescription(getResources().getString(R.string.action_pause));
            playButton_.setImageResource(R.drawable.ic_pause_white_24dp);
        }
        else {
            playButton_.setContentDescription(getResources().getString(R.string.action_play));
            playButton_.setImageResource(R.drawable.ic_play_arrow_white_24dp);
        }
    }

    @Override
    public void onClick(View v) {
        //add option to load onStart
        if (v == playButton_) {
            if(null == player_){
                return;
            }
            if(player_.isPlaying()) {
                player_.pauseMusic();
            }
            else {
                updatePlaylist(null);
                if(! player_.restartMusic()) {
                    player_.playMusic();
                }
            }
            updatePlayButton();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> list, View view, int pos, long id) {
        if(null == player_){
            return;
        }
        //refresh header is added....
        EpisodeRealm info = (EpisodeRealm)adapter_.getItem(pos-1);
        EpisodeRealm current = player_.getCurrentPodInfo();
        Log.d(TAG, "current: "+current);
        Log.d(TAG, "clicked: "+info);
        if(current != null && current.getId() == info.getId()) {
            Log.d(TAG, "current: title "+current.getTitle());
            Log.d(TAG, "onItemClick: URL: " + current.getURL());
            if(player_.isPlaying()) {
                player_.pauseMusic();
            }
            else {
                if(! player_.restartMusic()){
                    playEpisode(info);
                }
            }
        }
        else {
            playEpisode(info);
        }
    }

    private void playEpisode(EpisodeRealm episode) {
        updatePlaylist(null);
        //TODO: pass episode id
        player_.playById(episode.getId());
    }

    //UI is updated in following callback methods
    @Override
    public void onStartMusic(long episodeId) {
		//setProgressBarIndeterminateVisibility(false);
		//currentPlayPosition_.setMax(player_.getDuration());
		//int pos = player_.getCurrentPositionMsec();
        //currentPlayPosition_.setProgress(pos);
        //timer
        updateUI();
    }

    //xxxx
    @Override
    public void onCompleteMusic(long episodeId){
        Realm realm = Realm.getDefaultInstance();
        RealmResults<EpisodeRealm> result = realm.where(EpisodeRealm.class).equalTo("id", episodeId).findAll();
        if(result.size() == 0){
            Log.d(TAG, "onCompleteMusic: no episode");
            return;
        }
        EpisodeRealm listened = result.get(0);
        //TODO: async write
        realm.beginTransaction();
        listened.setListenedDate(new Date());
        realm.commitTransaction();
    }

    @Override
    public void onStartLoadingMusic(long episodeId) {
        updateUI();
    }

    @Override
    public void onStopMusic(int mode) {
        updateUI();
    }
    // end of callback methods

    public class EpisodeAdapter
        extends BaseAdapter
    {
        public EpisodeAdapter(){
        }
        
        @Override
        public int getCount(){
            //return getCurentEpisodeList().size();
            return getCurrentCount();
        }

        @Override
        public Object getItem(int position){
            //return getCurentEpisodeList().get(position);
            return getCurrentItem(position);
        }
        
        @Override
        public long getItemId(int position){
            return getCurentEpisodeList().get(position).getId();
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            EpisodeHolder holder;

            if (null == convertView) {
                view = View.inflate(PodplayerActivity.this, R.layout.episode_item, null);
                holder = new EpisodeHolder();
                holder.titleView_ = (TextView)view.findViewById(R.id.episode_title);
                holder.timeView_ = (TextView)view.findViewById(R.id.episode_time);
                holder.stateIcon_ = (ImageView)view.findViewById(R.id.play_icon);
                holder.episodeIcon_ = (ImageView)view.findViewById(R.id.episode_icon);
                holder.listenedView_ = (TextView)view.findViewById(R.id.listened_time);
                holder.displayedIconURL_ = null;
                view.setTag(holder);
            }
            else {
                view = convertView;
                holder = (EpisodeHolder)view.getTag();
            }
            EpisodeRealm episode = (EpisodeRealm)getItem(position);
            holder.titleView_.setText(episode.getTitle());
            holder.timeView_.setText(getResources().getString(R.string.published_date)
                                     +" "+episode.getPubdateStr(dateFormat_));
            if(episode.getListened() != null){
                // TODO: format
                holder.listenedView_.setText(getResources().getString(R.string.listened_date)
                                             +" "+dateFormat_.format(episode.getListened()));
                holder.listenedView_.setVisibility(View.VISIBLE);
            }
            else {
                holder.listenedView_.setVisibility(View.GONE);
            }
            if(player_ == null){
                holder.stateIcon_.setVisibility(View.GONE);
            }
            else {
                EpisodeRealm current = player_.getCurrentPodInfo();
                if(current != null && current.getURL().equals(episode.getURL())) {
                //TODO: cache!
                    if(player_.isPlaying()) {
                        holder.stateIcon_.setImageResource(R.drawable.ic_play_arrow_white_24dp);
                        holder.stateIcon_.setContentDescription(getString(R.string.icon_desc_playing));
                    }
                    else {
                        holder.stateIcon_.setImageResource(R.drawable.ic_pause_white_24dp);
                        holder.stateIcon_.setContentDescription(getString(R.string.icon_desc_pausing));
                    }
                    holder.stateIcon_.setVisibility(View.VISIBLE);
                }
                else {
                    holder.stateIcon_.setVisibility(View.GONE);
                }
            }
            // Log.d(TAG, "icon: " + episode.getTitle() + " index: " + episode.getIndex()
            //       + " current: " + currentList_.size()
            //       + " podcast:" + state_.podcastList_.size());
            //String iconURL = state_.podcastList_.get(episode.getIndex()).getIconURL();
            String iconURL = episode.getPodcast().getIconURL();
            if(showPodcastIcon_ && null != iconURL){
                //TODO: check previous icon url
                String displayedIconURL = holder.displayedIconURL_;
                if(View.GONE == holder.episodeIcon_.getVisibility()
                   || null == displayedIconURL
                   || !displayedIconURL.equals(iconURL)){
                    Glide
                        .with(getApplicationContext())
                        .load(iconURL)
                        .into(holder.episodeIcon_);
                    holder.episodeIcon_.setContentDescription(episode.getTitle());
                }
                holder.episodeIcon_.setVisibility(View.VISIBLE);
            }
            else {
                Glide.clear(holder.episodeIcon_);
                holder.episodeIcon_.setContentDescription(getString(R.string.icon_desc_episode_none));
                holder.episodeIcon_.setVisibility(View.GONE);
            }
            holder.displayedIconURL_ = iconURL;
            return view;
        }
    }

    private class GetPodcastTask
        extends BaseGetPodcastTask
    {
        public GetPodcastTask() {
            super(PodplayerActivity.this, client_, EPISODE_BUF_SIZE);
        }

        @Override
        protected void onProgressUpdate(String... values){
            filterSelectedPodcast();
        }

        private void onFinished() {
            state_.lastUpdatedDate_ = new Date();
            episodeListView_.onRefreshComplete(getString(R.string.header_lastupdated) + dateFormat_.format(state_.lastUpdatedDate_));
            episodeListView_.hideHeader();
            updateSelector();
            loadTask_ = null;
            //TODO: Sync playlist
            updatePlaylist(null);
            updateUI();
        }
        
        @Override
        protected void onPostExecute(Void result) {
            onFinished();
        }
        
        @Override
        protected void onCancelled() {
            onFinished();
        }
    }

    @Override
    public void onRefresh() {
        loadPodcast();
    }

    @Override
    public void onCancel() {
        if (null != loadTask_) {
            loadTask_.cancel(true);
            loadTask_ = null;
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> adapter, View view, int pos, long id) {
        EpisodeRealm info = (EpisodeRealm)adapter_.getItem(pos-1);
        Resources res = getResources();
        boolean enableLongClick = pref_.getBoolean("enable_long_click", res.getBoolean(R.bool.default_enable_long_click));
        if ((! enableLongClick) || null == info.getLink()) {
            return false;
        }
        //TODO: skip if url does not refer html?
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(info.getLink()));
        startActivity(i);
        return true;
    }

    @Override
    public void onItemSelected(AdapterView<?> adapter, View view, int pos, long id) {
        filterSelectedPodcast();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapter) {
        filterSelectedPodcast();
    }
    
    private int podcastTitle2Index(String title){
        RealmResults<PodcastRealm> list = currentQuery_.getPodcastList();
        for(int i = 0; i < list.size(); i++) {
            PodcastRealm info = list.get(i);
            if(title.equals(info.getTitle())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        Log.d(TAG, "onServiceConnected");
        player_ = ((PlayerService.LocalBinder)binder).getService();
        player_.setOnStartMusicListener(this);
        playButton_.setEnabled(true);
        syncPreference(pref_, "ALL");
        //TODO: move to base?
        RealmResults<EpisodeRealm> playlist = player_.getCurrentPlaylist();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        player_.clearOnStartMusicListener();
        player_ = null;
    }

    private void updateSelector(){
        List<String> list = new ArrayList<String>();
        list.add(getString(R.string.selector_all));
        for(PodcastRealm info: currentQuery_.getPodcastList()){
            list.add(info.getTitle());
        }
        //stop loading?
        ArrayAdapter<String> adapter =
            new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, list);
		// set appropriate height of dropdown
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        selector_.setAdapter(adapter);
    }

    private void filterSelectedPodcast(){
        episodeListView_.hideHeader();
        adapter_.notifyDataSetChanged();
    }
    
    private String getFilterPodcastTitle(){
        if(selector_.getSelectedItemPosition() == 0){
            return null;
        }
        String title = (String)selector_.getSelectedItem();
        return title;
    }

    public RealmResults<EpisodeRealm> getCurentEpisodeList(){
        int n = selector_.getSelectedItemPosition();
        if(n == 0 || n < 0){
            return currentQuery_.getEpisodeList();
        }
        PodcastRealm info = currentQuery_.getPodcastList().get(n-1);
        return currentQuery_.getEpisodeList(info.getId());
    }

    public int getCurrentCount(){
        int n = selector_.getSelectedItemPosition();
        if(episodeLimit_ < 0){
            if(n == 0 || n < 0){
                return currentQuery_.getEpisodeList().size();
            }
            PodcastRealm info = currentQuery_.getPodcastList().get(n-1);
            return currentQuery_.getEpisodeList(info.getId()).size();
        }
        if(n == 0 || n < 0){
            int size = 0;
            for(PodcastRealm info: currentQuery_.getPodcastList()){
                long id = info.getId();
                size += Math.min(currentQuery_.getEpisodeList(id).size(), episodeLimit_);
            }
            return size;
        }
        PodcastRealm info = currentQuery_.getPodcastList().get(n-1);
        return Math.min(currentQuery_.getEpisodeList(info.getId()).size(), episodeLimit_);
    }

    public EpisodeRealm getCurrentItem(int pos){
        int n = selector_.getSelectedItemPosition();
        if(episodeLimit_ < 0){
            if(n == 0 || n < 0){
                return currentQuery_.getEpisodeList().get(pos);
            }
            PodcastRealm info = currentQuery_.getPodcastList().get(n-1);
            return currentQuery_.getEpisodeList(info.getId()).get(pos);
        }
        if(n == 0 || n < 0){
            int remain = pos;
            for(PodcastRealm info: currentQuery_.getPodcastList()){
                long id = info.getId();
                RealmResults<EpisodeRealm> lst = currentQuery_.getEpisodeList(id);
                int virtsize = Math.min(lst.size(), episodeLimit_);
                if(remain < virtsize){
                    return lst.get(remain);
                }
                remain -= virtsize;
            }
        }
        PodcastRealm info = currentQuery_.getPodcastList().get(n-1);
        return currentQuery_.getEpisodeList(info.getId()).get(pos);
    }
    
    // @Override
    // public void onProgressChanged(SeekBar bar, int progress, boolean fromUser){
    //     if(!fromUser){
    //         return;
    //     }
    //     player_.seekTo(progress);
    // }

    // @Override
    // public void onStartTrackingTouch(SeekBar bar){
    //     //nop
    // }

    // @Override
    // public void onStopTrackingTouch(SeekBar bar){
    //     //nop
    // }

    static
    private class EpisodeHolder {
        TextView titleView_;
        TextView timeView_;
        ImageView stateIcon_;
        ImageView episodeIcon_;
        String displayedIconURL_;
        TextView listenedView_;
    }
}
