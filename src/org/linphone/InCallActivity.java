package org.linphone;
/*
InCallActivity.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphonePlayer;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.ui.AvatarWithShadow;
import org.linphone.ui.Numpad;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Sylvain Berfini
 */
public class InCallActivity extends FragmentActivity implements OnClickListener {
	public boolean animating_show_controls=false;

	public final static int NEVER = -1;
	public final static int NOW = 0;
	public final static int SECONDS_BEFORE_HIDING_CONTROLS = 3000;
	public final static int SECONDS_BEFORE_DENYING_CALL_UPDATE = 30000;

	private static InCallActivity instance;

	ArrayList<String> linphone_core_stats_list;

	private Handler mControlsHandler = new Handler();
	private Runnable mControls;
	private ImageView switchCamera;

	private boolean isCameraMutedPref;

	private TextView pause, hangUp, dialer, video, micro, speaker, options, addCall, transfer, conference;
	private TextView audioRoute, routeSpeaker, routeReceiver, routeBluetooth;
	private LinearLayout routeLayout;
	private ProgressBar videoProgress;
	private StatusFragment status;
	private AudioCallFragment audioCallFragment;
	private VideoCallFragment videoCallFragment;
	private boolean isCameraMutedOnStart=false, isCameraMuted=false, isMicMuted = false, isTransferAllowed, isAnimationDisabled,
			isRTTLocallyEnabled = false, isRTTEnabled=true;
	private static boolean isSpeakerMuted;
	public ViewGroup mControlsLayout;
	private Numpad numpad;
	private int cameraNumber;
	private Animation slideOutLeftToRight, slideInRightToLeft, slideInBottomToTop, slideInTopToBottom, slideOutBottomToTop, slideOutTopToBottom;
	private CountDownTimer timer;
	private boolean isVideoCallPaused = false;
	AcceptCallUpdateDialogFragment callUpdateDialog;

	private TableLayout callsList;
	private LayoutInflater inflater;
	private ViewGroup container;
	private boolean isConferenceRunning = false;
	private boolean showCallListInVideo = false;
	private LinphoneCoreListenerBase mListener;
	private Timer outgoingRingCountTimer = null;

	public Contact contact;

	// RTT views
	private int TEXT_MODE;
	private int NO_TEXT=-1;
	private int RTT=0;
	private int SIP_SIMPLE=1;
	private TextWatcher rttTextWatcher;
	private ScrollView rtt_scrollview;
	private View rttContainerView;
	private View rttHolder;

	String contactName = "";

	int OUTGOING=0;
	int INCOMING=1;

	private boolean isRTTMaximized = false;
	private boolean isIncommingBubbleCreated = false;
	public int rttIncomingBubbleCount=0;
	private int rttOutgoingBubbleCount=0;
	public boolean incoming_chat_initiated=false;
	private SharedPreferences prefs;
	private EditText previousoutgoingEditText;
	private EditText outgoingEditText;
	private TextView incomingTextView;
	View mFragmentHolder;
	View mViewsHolder;
	View linphone_core_stats_holder;
	TableLayout linphone_core_stats_table;
	RelativeLayout mainLayout;
	final float mute_db = -1000.0f;

	private HeadPhoneJackIntentReceiver myReceiver;

	public static InCallActivity instance() {
		return instance;
	}

	public static boolean isInstanciated() {
		return instance != null;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d("onCreate()");

		instance = this;
		//DialerFragment.instance().mOrientationHelper.disable();
		LinphoneActivity.instance().mOrientationHelper.enable();
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		mainLayout = new RelativeLayout(this);
		mainLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		LayoutInflater inflator = LayoutInflater.from(this);
		mViewsHolder =  inflator.inflate(R.layout.incall, null);



		mFragmentHolder = inflator.inflate(R.layout.incall_fragment_holder, null);
		rttHolder =  inflator.inflate(R.layout.rtt_holder, null);
		View statusBar = inflator.inflate(R.layout.status_holder, null);
		RelativeLayout.LayoutParams paramss = new RelativeLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT);

		linphone_core_stats_holder =  inflator.inflate(R.layout.linphone_core_stats, null);
		linphone_core_stats_table = (TableLayout)linphone_core_stats_holder.findViewById(R.id.linphone_core_stats);
		show_extra_linphone_core_stats();

		//paramss.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		mainLayout.addView(mFragmentHolder,paramss);
		mainLayout.addView(mViewsHolder);
		mainLayout.addView(rttHolder, paramss);
		mainLayout.addView(statusBar);
		mainLayout.addView(linphone_core_stats_holder, paramss);
		setContentView(mainLayout);

		myReceiver = new HeadPhoneJackIntentReceiver();

		isTransferAllowed = getApplicationContext().getResources().getBoolean(R.bool.allow_transfers);
		showCallListInVideo = getApplicationContext().getResources().getBoolean(R.bool.show_current_calls_above_video);
		LinphoneManager.getLc().enableSpeaker(true);


		//if (params.realTimeTextEnabled()) { // Does not work, always false
		isRTTLocallyEnabled=LinphoneManager.getInstance().getRttPreference();

		isAnimationDisabled = getApplicationContext().getResources().getBoolean(R.bool.disable_animations) || !LinphonePreferences.instance().areAnimationsEnabled();
		cameraNumber = AndroidCameraConfiguration.retrieveCameras().length;


		getTextMode();


		isCameraMutedPref=prefs.getBoolean(getString(R.string.pref_av_camera_mute_key), false);
		isCameraMutedOnStart=isCameraMutedPref;

		boolean isMicMutedPref = prefs.getBoolean(getString(R.string.pref_av_mute_mic_key), false);
		LinphoneManager.getLc().muteMic(isMicMutedPref);

		boolean isSpeakerMutedPref = prefs.getBoolean(getString(R.string.pref_av_speaker_mute_key), false);
		if (isSpeakerMutedPref) {
			LinphoneManager.getLc().setPlaybackGain(mute_db);
		} else {
			LinphoneManager.getLc().setPlaybackGain(0);
		}

		status.callStats.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v) {
				Log.d("stats clicked");
				linphone_core_stats_holder.setVisibility(View.VISIBLE);
			}
		});
		linphone_core_stats_table.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v) {
				Log.d("stats clicked");
				linphone_core_stats_holder.setVisibility(View.GONE);
			}
		});


		mListener = new LinphoneCoreListenerBase(){
			@Override
			public void isComposingReceived(LinphoneCore lc, LinphoneChatRoom cr) {
				super.isComposingReceived(lc, cr);
				Log.d("RTT incall", "isComposingReceived cr=" + cr.toString());
				Log.d("RTT incall","isRTTMaximaized"+isRTTMaximized);
				Log.d("RTT", "incoming_chat_initiated" + incoming_chat_initiated);

				try {
					if (!cr.isRemoteComposing()) {
						Log.d("RTT incall: remote is not composing, getChar() returns: " + cr.getChar());
						return;
					}
				}catch(Throwable e){

				}



			}

			@Override
			public void infoReceived(LinphoneCore lc, LinphoneCall call,
									 LinphoneInfoMessage info) {
				Log.d("info received"+info.getHeader("action"));
				if(info.getHeader("action").equals("camera_mute_off")){
					VideoCallFragment.cameraCover.setImageResource(R.drawable.camera_mute);
					VideoCallFragment.cameraCover.setVisibility(View.VISIBLE);

				}else if(info.getHeader("action").equals("isCameraMuted") || info.getHeader("action").equals("camera_mute_on")){
					VideoCallFragment.cameraCover.setVisibility(View.GONE);

				}


			}


			@Override
			public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr, LinphoneChatMessage message) {
				super.messageReceived(lc, cr, message);
				Log.d("RTT", "messageReceived cr=" + message.toString());

			}

			@Override
			public void callState(LinphoneCore lc, final LinphoneCall call, LinphoneCall.State state, String message) {
				Log.d("callState change");
				try {
					LinphoneActivity.instance().display_all_core_values(lc, state.toString());
				}catch(Throwable e){
					e.printStackTrace();
				}
				if (lc.getCallsNb() == 0) {
					finish();
					return;
				}
				if(state==State.IncomingReceived||state == state.OutgoingInit) {
					LinphoneManager.getInstance().initSDP(isVideoEnabled(call));
				}
				if (state == State.IncomingReceived) {
					startIncomingCallActivity();
					return;
				}


				if (state == State.Paused || state == State.PausedByRemote ||  state == State.Pausing) {
					video.setEnabled(false);
					if(!isVideoEnabled(call)){
						showAudioView();
					}
				}

				if (state == State.Resuming) {
					if(LinphonePreferences.instance().isVideoEnabled()){
						status.refreshStatusItems(call, isVideoEnabled(call));
						if(isVideoEnabled(call)){
							showVideoView();
						}
					}
				}

				if (state == State.StreamsRunning) {
					if(isRTTLocallyEnabled) {
						isRTTEnabled = call.getRemoteParams().realTimeTextEnabled();
					}
					else{
						isRTTEnabled = false;
					}

					switchVideo(isVideoEnabled(call));
					//Check media in progress
					if(LinphonePreferences.instance().isVideoEnabled() && !call.mediaInProgress()){
						video.setEnabled(true);
					}
					isMicMuted = lc.isMicMuted();
					isSpeakerMuted = lc.getPlaybackGain()==mute_db;

					enableAndRefreshInCallActions();

					if (status != null) {
						videoProgress.setVisibility(View.GONE);
						status.refreshStatusItems(call, isVideoEnabled(call));
					}
				}

				refreshInCallActions();

				refreshCallList(getResources());

				if (state == State.CallUpdatedByRemote) {
					// If the correspondent proposes video while audio call
					boolean videoEnabled = LinphonePreferences.instance().isVideoEnabled();
					if (!videoEnabled) {
						acceptCallUpdate(false);
						return;
					}

					boolean remoteVideo = call.getRemoteParams().getVideoEnabled();
					boolean localVideo = call.getCurrentParamsCopy().getVideoEnabled();
					boolean autoAcceptCameraPolicy = LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests();
					if (remoteVideo && !localVideo && !autoAcceptCameraPolicy && !LinphoneManager.getLc().isInConference()) {
						showAcceptCallUpdateDialog();

						timer = new CountDownTimer(SECONDS_BEFORE_DENYING_CALL_UPDATE, 1000) {
							public void onTick(long millisUntilFinished) { }
							public void onFinish() {
								if (callUpdateDialog != null)
									callUpdateDialog.dismiss();
								acceptCallUpdate(false);
							}
						}.start();
					}
//        			else if (remoteVideo && !LinphoneManager.getLc().isInConference() && autoAcceptCameraPolicy) {
//        				mHandler.post(new Runnable() {
//        					@Override
//        					public void run() {
//        						acceptCallUpdate(true);
//        					}
//        				});
//        			}
				}

				transfer.setEnabled(LinphoneManager.getLc().getCurrentCall() != null);
			}

			@Override
			public void callEncryptionChanged(LinphoneCore lc, final LinphoneCall call, boolean encrypted, String authenticationToken) {
				if (status != null) {
					status.refreshStatusItems(call, call.getCurrentParamsCopy().getVideoEnabled());
				}
			}
		};

		if (findViewById(R.id.fragmentContainer) != null) {
			initUI();

			if (LinphoneManager.getLc().getCallsNb() > 0) {
				LinphoneCall call = LinphoneManager.getLc().getCalls()[0];

				if (LinphoneUtils.isCallEstablished(call)) {
					enableAndRefreshInCallActions();
				}
			}

			if (savedInstanceState != null) {
				Log.d("getting savedInstanceState");
				// Fragment already created, no need to create it again (else it will generate a memory leak with duplicated fragments)
				isRTTMaximized = savedInstanceState.getBoolean("isRTTMaximized");
				isMicMuted = savedInstanceState.getBoolean("Mic");
				isSpeakerMuted = savedInstanceState.getBoolean("Speaker");
				isVideoCallPaused = savedInstanceState.getBoolean("VideoCallPaused");
				refreshInCallActions();


				return;
			}

			Fragment callFragment;
			if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
				callFragment = new VideoCallFragment();
				videoCallFragment = (VideoCallFragment) callFragment;

				if (cameraNumber > 1) {
					switchCamera.setVisibility(View.VISIBLE);
				}
			} else {
				callFragment = new AudioCallFragment();
				audioCallFragment = (AudioCallFragment) callFragment;
				switchCamera.setVisibility(View.INVISIBLE);
			}

			if(BluetoothManager.getInstance().isBluetoothHeadsetAvailable()){
				BluetoothManager.getInstance().routeAudioToBluetooth();
			}

			callFragment.setArguments(getIntent().getExtras());
			getSupportFragmentManager().beginTransaction().add(R.id.fragmentContainer, callFragment).commitAllowingStateLoss();


			LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
			if(call != null) {
				LinphoneCallParams params = call.getCurrentParamsCopy();
				initRTT();

				if (isRTTMaximized) {
					showRTTinterface();
				}
			}

		}


	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Log.d("onConfigChanged");

		boolean contralersVisible = mControlsLayout.getVisibility() == View.VISIBLE;
		mainLayout.removeView(mViewsHolder);


		mViewsHolder = (ViewGroup) getLayoutInflater().inflate(R.layout.incall, null);

		mainLayout.addView(mViewsHolder, 1);
		initUI();
		if(!contralersVisible)
			mControlsLayout.setVisibility(View.GONE);

		/*if(isRTTEnabled){
			initRTT();
		}*/
		/*if(isRTTMaximized){
			showRTTinterface();
		}
		if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
			displayVideoCallControlsIfHidden();
		}
*/
		if (LinphoneManager.getLc().getCallsNb() > 0) {
			LinphoneCall call = LinphoneManager.getLc().getCalls()[0];

			if (LinphoneUtils.isCallEstablished(call)) {
				enableAndRefreshInCallActions();
			}
		}
		//refreshCallList(getResources());
		//handleViewIntent();

	}

	public void getTextMode(){
		prefs = PreferenceManager.getDefaultSharedPreferences(LinphoneActivity.instance());
		Log.d("Text Send Mode" + prefs.getString(getString(R.string.pref_text_settings_send_mode_key), "RTT"));
		String text_mode=prefs.getString(getString(R.string.pref_text_settings_send_mode_key), "RTT");
		if(text_mode.equals("SIP_SIMPLE")) {
			TEXT_MODE=SIP_SIMPLE;
		}else if (text_mode.equals("RTT")) {
			TEXT_MODE = RTT;

		}
		Log.d("TEXT_MODE ", TEXT_MODE);
	}

	public void hold_cursor_at_end_of_edit_text(final EditText et) {
		et.setCursorVisible(false);
		et.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				et.setSelection(et.getText().length());
			}
		});
	}


	public void show_extra_linphone_core_stats(){
		//Add all linphone core stats.
		linphone_core_stats_list=LinphoneActivity.instance().display_all_core_values(LinphoneManager.getLc(), "In Call Stats Populated");
		for(int i=0; i<linphone_core_stats_list.size(); i++){

			TableRow tr=new TableRow(LinphoneActivity.instance());
			tr.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.FILL_PARENT, TableRow.LayoutParams.WRAP_CONTENT));

			TextView label=new TextView(LinphoneActivity.instance());
			label.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
			String label_string=linphone_core_stats_list.get(i).split(",")[0];
			label.setText(label_string);
			label.setTextColor(Color.WHITE);
			label.setTextSize(12);
			tr.addView(label);

			TextView content=new TextView(LinphoneActivity.instance());
			content.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
			content.setText(linphone_core_stats_list.get(i).subSequence(label_string.length()+1, linphone_core_stats_list.get(i).length()));
			content.setTextColor(Color.WHITE);
			content.setTextSize(12);
			tr.addView(content);

			linphone_core_stats_table.addView(tr);

		}
		//callStats.setLayoutParams(new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 5000));
		//callStats.invalidate();
		//((ScrollView)callStats.getParent()).invalidate();
	}



	/** Initializes the views and other components needed for RTT in a call */
	private void initRTT(){
		rttContainerView = findViewById(R.id.rtt_container);

		rtt_scrollview = (ScrollView)findViewById(R.id.rtt_scrollview);
		rtt_scrollview.getChildAt(0).setOnClickListener(this);

		rttTextWatcher = new TextWatcher() {
			boolean enter_pressed;
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				enter_pressed=false;



				if (s.length()>0 && s.subSequence(s.length()-1, s.length()).toString().equalsIgnoreCase("\n")) {
					enter_pressed=true;
				}

				char enter_button=(char) 10;
				char back_space_button=(char) 8;

				if(TEXT_MODE==RTT){
					if(enter_pressed){
						previousoutgoingEditText=outgoingEditText;
						sendRttCharacter(enter_button);
						create_new_outgoing_bubble(outgoingEditText, /*true*/ true);
					}else if(count > before){

						CharSequence last_letter_of_sequence = s.subSequence(start + before, start + count);
						Log.d("last_letter_of_sequence="+last_letter_of_sequence);

						int numeric_value=Character.getNumericValue(last_letter_of_sequence.charAt(0));
						Log.d("numeric value="+numeric_value);

						sendRttCharacterSequence(last_letter_of_sequence);
					}else if(count < before){
						sendRttCharacter(back_space_button); // backspace);
					}
				}else if(TEXT_MODE==SIP_SIMPLE){
					previousoutgoingEditText=outgoingEditText;
					if(enter_pressed) {
						//send preceding new line character to force other end to drop a line.
						if(rttOutgoingBubbleCount>1){
							sendRttCharacterSequence("\n"+String.valueOf(s.subSequence(0,s.length()-1)));
						}else{
							sendRttCharacterSequence(String.valueOf(s.subSequence(0,s.length()-1)));
						}
						create_new_outgoing_bubble(outgoingEditText, true);
					}
				}

			}

			@Override
			public void afterTextChanged(Editable s) {
				//REMOVE EXTRA LINE FROM ENTER PRESS
				if(enter_pressed) {
					previousoutgoingEditText.removeTextChangedListener(rttTextWatcher);
					previousoutgoingEditText.setText(previousoutgoingEditText.getText().toString().subSequence(0,previousoutgoingEditText.getText().toString().length()-1));
				}
			}
		};

		outgoingEditText = (EditText) findViewById(R.id.et_outgoing_bubble);
		outgoingEditText.addTextChangedListener(rttTextWatcher);
		standardize_bubble_view(outgoingEditText);
		hold_cursor_at_end_of_edit_text(outgoingEditText);
		outgoingEditText.setMovementMethod(null);


		try {
			populate_messages();
		}catch(Throwable e){
			//No messages to populate
			e.printStackTrace();
		}

	}

	public int to_dp(int dp){
		final float scale = getApplicationContext().getResources().getDisplayMetrics().density;
		int pixels = (int) (dp * scale + 0.5f);
		return pixels;
	}

	public void disable_bubble_editing(EditText et){
		et.setKeyListener(null);
	}
	public void standardize_bubble_view(TextView tv){
		tv.setSingleLine(false);
		//tv.setPadding(to_dp(10), to_dp(5), to_dp(10), to_dp(20));
		tv.setTextAppearance(this, R.style.RttTextStyle);
		//Log.d("RTT textsize by default="+tv.getTextSize());
		//Default TextSize is 32dp
		tv.setTextSize(16);
		tv.getBackground().setAlpha(180);
	}
	public TextView create_new_outgoing_bubble(EditText old_bubble, boolean is_current_editable_bubble){
		/*if(old_bubble!=null){
			disable_bubble_editing(old_bubble);
		}*/
		LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(to_dp(300), LinearLayout.LayoutParams.WRAP_CONTENT);
		lp.setMargins(to_dp(10), 0, 0, 0);

		TextView et=new TextView(this);
		et.setLayoutParams(lp);
		et.setBackgroundResource(R.drawable.chat_bubble_outgoing);
		et.setTag(true);
		et.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				outgoingEditText.requestFocus();
				InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);

			}
		});
		standardize_bubble_view(et);

		//if(TEXT_MODE==RTT) {
//		if(is_current_editable_bubble) {
//			et.addTextChangedListener(rttTextWatcher);
//		}
		//}

//		et.setOnKeyListener(new View.OnKeyListener() { //FIXME: not triggered for software keyboards
//			@Override
//			public boolean onKey(View v, int keyCode, KeyEvent event) {
//				if (event.getAction() == KeyEvent.ACTION_DOWN) {
//					if (keyCode == KeyEvent.KEYCODE_ENTER) {
//						Log.d("ENTER BUTTON PRESSED");
//						if(TEXT_MODE==RTT){
//							sendRttCharacter((char) 10);
//							create_new_outgoing_bubble((EditText) v);
//						}else if(TEXT_MODE==SIP_SIMPLE){
//							String current_message=((EditText) v).getText().toString();
//							sendRttCharacterSequence(current_message+(char) 10);
//							create_new_outgoing_bubble((EditText) v);
//						}
//
//
//					}
//				}
//				return false;
//			}
//		});
		//hold_cursor_at_end_of_edit_text(et);
		//outgoingEditText=et;
		if(((LinearLayout) rttContainerView).getChildCount()==0 || !isIncommingBubbleCreated || !is_current_editable_bubble)
			((LinearLayout) rttContainerView).addView(et);
		else
			((LinearLayout) rttContainerView).addView(et,((LinearLayout) rttContainerView).getChildCount()-1 );

//		et.requestFocus();
//		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
//		imm.showSoftInput(et, InputMethodManager.SHOW_FORCED);
//
		rtt_scrollview.post(new Runnable() {
			@Override
			public void run() {
				rtt_scrollview.fullScroll(ScrollView.FOCUS_DOWN);
			}
		});
		rttOutgoingBubbleCount++;
		et.setText(outgoingEditText.getText().toString().replace("\n", ""));
		outgoingEditText.setText("");
		rtt_scrollview.post(new Runnable() {
			@Override
			public void run() {
				rtt_scrollview.fullScroll(ScrollView.FOCUS_DOWN);
			}
		});
		return et;
	}
	public void updateIncomingTextView(final long character) {
		runOnUiThread(new Runnable(){
			public void run() {
				if(rttHolder.getVisibility()!=View.VISIBLE){
					showRTTinterface();
				}
				if(mControlsLayout.getVisibility()!= View.GONE)
					mControlsLayout.setVisibility(View.GONE);

				if(!incoming_chat_initiated){
					incomingTextView=create_new_incoming_bubble();
					incoming_chat_initiated=true;
				}

				if (incomingTextView == null) return;

				if(!incomingTextView.isShown()){
					incomingTextView=create_new_incoming_bubble();
				}

				String currentText = incomingTextView.getText().toString();
				if (character == 8) {// backspace
					incomingTextView.setText(currentText.substring(0, currentText.length() - 1));
				} else if (character == (long)0x2028) {
					Log.d("RTT: received Line Separator");
					create_new_incoming_bubble();
				} else if (character == 10) {
					Log.d("RTT: received newline");
					incomingTextView.append(System.getProperty("line.separator"));
					create_new_incoming_bubble();
				} else { // regular character
					if(rttIncomingBubbleCount==0){
						Log.d("There was no incoming bubble to send text to, so now we must make one.");
						incomingTextView=create_new_incoming_bubble();
					}
					incomingTextView.setText(currentText + (char)character);
				}
				rtt_scrollview.post(new Runnable() {
					@Override
					public void run() {
						rtt_scrollview.fullScroll(ScrollView.FOCUS_DOWN);
					}
				});

			}
		});


		//int scroll_amount = (incomingTextView.getLineCount() * incomingTextView.getLineHeight()) - (incomingTextView.getBottom() - incomingTextView.getTop());
		//incomingTextView.scrollTo(0, (int) (scroll_amount + incomingTextView.getLineHeight() * 0.5));
	}
	public TextView create_new_incoming_bubble(){
		isIncommingBubbleCreated = true;
		LinearLayout.LayoutParams lp1=new LinearLayout.LayoutParams(to_dp(300), LinearLayout.LayoutParams.WRAP_CONTENT);
		lp1.setMargins(0, 0, to_dp(10), 0);
		lp1.gravity = Gravity.RIGHT;
		TextView tv=new TextView(this);
		tv.setLayoutParams(lp1);
		tv.setBackgroundResource(R.drawable.chat_bubble_incoming);
		tv.setTag(false);

		standardize_bubble_view(tv);

		tv.setTextColor(Color.parseColor("#000000"));
		tv.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				outgoingEditText.requestFocus();
				InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);

			}
		});
		incomingTextView=tv;
		((LinearLayout)rttContainerView).addView(tv);

		rtt_scrollview.post(new Runnable() {
			@Override
			public void run() {
				rtt_scrollview.fullScroll(ScrollView.FOCUS_DOWN);
			}
		});
		rttIncomingBubbleCount++;
		return tv;
	}
	private void showRTTinterface() {
		//getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
		/*runOnUiThread(new Runnable() {
			public void run() {
				isRTTMaximized = true;
				rttHolder.setVisibility(View.VISIBLE);
			}
		});*/

		isRTTMaximized = true;
		rttHolder.setVisibility(View.VISIBLE);
	}
	@Override
	public void onBackPressed() {
		super.onBackPressed();
		mControlsLayout.setVisibility(View.VISIBLE);
	}

	/** Called when backspace is pressed in an RTT conversation.
	 * Sends a backspace character and updates the outgoing text
	 * views if necessary.
	 * @return true if the key event should sbe consumed (ie. there should
	 * be no further processing of this backspace event)
	 */
//	private boolean backspacePressed() {
//		if (rttOutputEditTexts.getText().length() == 0) {
//			rttOutputEditTexts.removeTextChangedListener(rttTextWatcher);
//
//			// If there's no text in the input EditText, check if
//			// there's any old sent text that can be brought down.
//			// Lines are delimited by \n in this simple text UI.
//
//			String outtext = rttOutputEditTexts.getText().toString();
//			int newline = outtext.lastIndexOf("\n");
//
//			if (newline >= 0) {
//				rttOutputEditTexts.setText(outtext.substring(0, newline));
//				rttOutputEditTexts.append(outtext.substring(newline+1));
//			} else {
//				rttOutputField.setText("");
//				rttOutputEditTexts.append(outtext);
//			}
//			rttOutputEditTexts.addTextChangedListener(rttTextWatcher);
//			return true;
//		} else {
//
//			sendRttCharacter((char) 8);
//
//			if (hasHardwareKeyboard()) {
//				rttOutputEditTexts.removeTextChangedListener(rttTextWatcher);
//
//				// Quick and dirty hack to keep the cursor at the end of the line.
//				// EditText.append() inserts the text and places the cursor last.
//				CharSequence cs = rttOutputEditTexts.getText();
//				rttOutputEditTexts.setText("");
//				rttOutputEditTexts.append(cs.subSequence(0, cs.length() - 1));
//
//				rttOutputField.addTextChangedListener(rttTextWatcher);
//			}
//
//			return true;
//		}
//	}

	/** Somewhat reliable method of detecting the presence of a hardware
	 * keyboard. Not fully tested, needs to work for both Bluetooth and USB.
	 * @return true if a hardware keyboard is present
	 */
	private boolean hasHardwareKeyboard() {
		Resources res = getApplicationContext().getResources();
		return res.getConfiguration().keyboard == Configuration.KEYBOARD_QWERTY;
	}

	/** Called when the user has pressed enter in an RTT conversation. This
	 * method inserts line breaks in the text views and sends the appropriate
	 * newline character.
	 */
//	private void enterPressed() {
//		rttOutputEditTexts.removeTextChangedListener(rttTextWatcher);
//		//rttOutgoingTextView.setText(rttOutgoingTextView.getText() + "\n" + rttOutputEditTexts.getText());
//		rttOutgoingTextView.append("\n");
//		rttOutgoingTextView.append(rttOutputEditTexts.getText());
//
//		int scroll_amount = (rttOutgoingTextView.getLineCount() * rttOutgoingTextView.getLineHeight()) - (rttOutgoingTextView.getBottom() - rttOutgoingTextView.getTop());
//		rttOutgoingTextView.scrollTo(0, (int) (scroll_amount + rttOutgoingTextView.getLineHeight() * 0.5));
//
//		rttOutputEditTexts.setText("");
//		sendRttCharacter((char) 10);
//
//		//rttOutputEditTexts.addTextChangedListener(rttTextWatcher);
//	}

	/** Send a single character in RTT */
	private void sendRttCharacter(char character) {
		sendRttCharacterSequence(String.valueOf(character));
	}

	/** Send a sequence of characters in RTT */
	private void sendRttCharacterSequence(CharSequence cs) {

		if (cs.length() > 0) {
			Log.d("RTT","LinphoneManager.getInstance().sendRealtimeText(cs);"+cs);
			LinphoneManager.getInstance().sendRealtimeText(cs);
		}
	}

	private boolean isVideoEnabled(LinphoneCall call) {
		if(call != null){
			return call.getCurrentParamsCopy().getVideoEnabled();
		}
		return false;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putBoolean("isRTTMaximized", isRTTMaximized);
		outState.putBoolean("Mic", LinphoneManager.getLc().isMicMuted());
		outState.putBoolean("Speaker", LinphoneManager.getLc().getPlaybackGain() == mute_db);
		outState.putBoolean("VideoCallPaused", isVideoCallPaused);

		super.onSaveInstanceState(outState);
	}

	public void save_messages(){

		LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		if(call == null) {
			delete_messages();
			return;
		}
		Log.d("saving RTT view");
		//Store RTT or SIP SIMPLE text log
		int number_of_messages=((LinearLayout) rttContainerView).getChildCount();
		LinphoneActivity.instance().message_directions=new int[number_of_messages + 1];
		LinphoneActivity.instance().message_texts=new String[number_of_messages + 1];
		for(int j=0; j<number_of_messages; j++){
			View view=((LinearLayout) rttContainerView).getChildAt(j);
			if(((Boolean)view.getTag())){
				LinphoneActivity.instance().message_directions[j]=OUTGOING;
			}else{
				LinphoneActivity.instance().message_directions[j]=INCOMING;
			}
			LinphoneActivity.instance().message_texts[j]=((TextView)view).getText().toString();
		}
		LinphoneActivity.instance().message_texts[number_of_messages]=(outgoingEditText).getText().toString();
		LinphoneActivity.message_call_Id = call.getCallLog().getCallId();
	}

	public void populate_messages(){
		LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		if(call == null || !call.getCallLog().getCallId().equals( LinphoneActivity.message_call_Id) ){
			return;
		}
		int[] direction=LinphoneActivity.instance().message_directions;
		String[] messages=LinphoneActivity.instance().message_texts;
		Log.d("openning saved RTT view");
		for(int i=0; i<messages.length-1; i++){

			if(direction[i]==OUTGOING){
				Log.d("OUTGOING: "+messages[i]);
				create_new_outgoing_bubble(null, false).setText(messages[i]);
			}else{
				Log.d("INCOMING: "+messages[i]);
				create_new_incoming_bubble();
				incomingTextView.setText(messages[i]);
			}

		}
		outgoingEditText.setText(messages[messages.length - 1]);

	}

	public void delete_messages(){
		LinphoneActivity.instance().message_directions=null;
		LinphoneActivity.instance().message_texts=null;
	}

	private boolean isTablet() {
		return getResources().getBoolean(R.bool.isTablet);
	}

	private void initUI() {
		inflater = LayoutInflater.from(this);
		container = (ViewGroup) findViewById(R.id.topLayout);
		callsList = (TableLayout) findViewById(R.id.calls);
		if (!showCallListInVideo) {
			callsList.setVisibility(View.GONE);

		}

		video = (TextView) findViewById(R.id.video);

		video.setOnClickListener(this);
		video.setEnabled(false);


		micro = (TextView) findViewById(R.id.micro);
		micro.setOnClickListener(this);
//		micro.setEnabled(false);
		speaker = (TextView) findViewById(R.id.speaker);
		speaker.setOnClickListener(this);
		toggleSpeaker(isSpeakerMuted);

		addCall = (TextView) findViewById(R.id.addCall);
		addCall.setOnClickListener(this);
		addCall.setEnabled(false);
		transfer = (TextView) findViewById(R.id.transfer);
		transfer.setOnClickListener(this);
		transfer.setEnabled(false);
		options = (TextView) findViewById(R.id.options);
		options.setOnClickListener(this);
		options.setEnabled(false);
		pause = (TextView) findViewById(R.id.toggleChat);
		pause.setOnClickListener(this);
		pause.setEnabled(false);
		hangUp = (TextView) findViewById(R.id.hangUp);
		hangUp.setOnClickListener(this);
		conference = (TextView) findViewById(R.id.conference);
		conference.setOnClickListener(this);
		dialer = (TextView) findViewById(R.id.dialer);
		dialer.setOnClickListener(this);
		dialer.setEnabled(false);
		numpad = (Numpad) findViewById(R.id.numpad);
		numpad.setHapticEnabled(true);
		numpad.setDTMFSoundEnabled(false);
		videoProgress =  (ProgressBar) findViewById(R.id.videoInProgress);
		videoProgress.setVisibility(View.GONE);



		try {
			routeLayout = (LinearLayout) findViewById(R.id.routesLayout);
			audioRoute = (TextView) findViewById(R.id.audioRoute);
			audioRoute.setOnClickListener(this);
			routeSpeaker = (TextView) findViewById(R.id.routeSpeaker);
			routeSpeaker.setOnClickListener(this);
			routeReceiver = (TextView) findViewById(R.id.routeReceiver);
			routeReceiver.setOnClickListener(this);
			routeBluetooth = (TextView) findViewById(R.id.routeBluetooth);
			routeBluetooth.setOnClickListener(this);
		} catch (NullPointerException npe) {
			Log.e("Bluetooth: Audio routes menu disabled on tablets for now (1)");
		}



		switchCamera = (ImageView) findViewById(R.id.switchCamera);
		switchCamera.setOnClickListener(this);

		mControlsLayout = (ViewGroup) findViewById(R.id.menu);

		if (!isTransferAllowed) {
			addCall.setBackgroundResource(R.drawable.options_add_call);
		}
		if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
			if(!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
				BluetoothManager.getInstance().initBluetooth();
			} else {
				isSpeakerMuted = true;
			}
		}

		if (!isAnimationDisabled) {
			slideInRightToLeft = AnimationUtils.loadAnimation(this, R.anim.slide_in_right_to_left);
			slideOutLeftToRight = AnimationUtils.loadAnimation(this, R.anim.slide_out_left_to_right);
			slideInBottomToTop = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom_to_top);
			slideInTopToBottom = AnimationUtils.loadAnimation(this, R.anim.slide_in_top_to_bottom);
			slideOutBottomToTop = AnimationUtils.loadAnimation(this, R.anim.slide_out_bottom_to_top);
			slideOutTopToBottom = AnimationUtils.loadAnimation(this, R.anim.slide_out_top_to_bottom);
		}

		if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
			try {
				if (routeLayout != null)
					routeLayout.setVisibility(View.VISIBLE);
				audioRoute.setVisibility(View.VISIBLE);
				speaker.setVisibility(View.GONE);
			} catch (NullPointerException npe) { Log.e("Bluetooth: Audio routes menu disabled on tablets for now (2)"); }
		} else {
			try {
				if (routeLayout != null)
					routeLayout.setVisibility(View.GONE);
				audioRoute.setVisibility(View.GONE);
				speaker.setVisibility(View.VISIBLE);
			} catch (NullPointerException npe) { Log.e("Bluetooth: Audio routes menu disabled on tablets for now (3)"); }
		}


		LinphoneManager.getInstance().changeStatusToOnThePhone();
		if(isCameraMutedOnStart) {
			Log.d("isCameraMutedOnStart3");
			toggleCamera_mute();
			video.setBackgroundResource(R.drawable.video_off);
			isCameraMutedOnStart=false;
		}
	}



	private void refreshInCallActions() {
		if (!LinphonePreferences.instance().isVideoEnabled()) {
			video.setEnabled(false);
		} else {
			if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())&&!isCameraMuted) {
				video.setBackgroundResource(R.drawable.video_on);
			} else {
				video.setBackgroundResource(R.drawable.video_off);
			}
		}

		try {
			if (!isSpeakerMuted) {
				speaker.setBackgroundResource(R.drawable.speaker_on);
				routeSpeaker.setBackgroundResource(R.drawable.route_speaker_on);
				routeReceiver.setBackgroundResource(R.drawable.route_receiver_off);
				routeBluetooth.setBackgroundResource(R.drawable.route_bluetooth_off);
			} else {
				speaker.setBackgroundResource(R.drawable.speaker_off);
				routeSpeaker.setBackgroundResource(R.drawable.route_speaker_off);
				if (BluetoothManager.getInstance().isUsingBluetoothAudioRoute()) {
					routeReceiver.setBackgroundResource(R.drawable.route_receiver_off);
					routeBluetooth.setBackgroundResource(R.drawable.route_bluetooth_on);
				} else {
					routeReceiver.setBackgroundResource(R.drawable.route_receiver_on);
					routeBluetooth.setBackgroundResource(R.drawable.route_bluetooth_off);
				}
			}
		} catch (NullPointerException npe) {
			Log.e("Bluetooth: Audio routes menu disabled on tablets for now (4)");
		}

		if (isMicMuted) {
			micro.setBackgroundResource(R.drawable.micro_off);
		} else {
			micro.setBackgroundResource(R.drawable.micro_on);
		}

		if (LinphoneManager.getLc().getCallsNb() > 1) {
			conference.setVisibility(View.VISIBLE);
			pause.setVisibility(View.GONE);
		} else {
			conference.setVisibility(View.GONE);
			pause.setVisibility(View.VISIBLE);

			List<LinphoneCall> pausedCalls = LinphoneUtils.getCallsInState(LinphoneManager.getLc(), Arrays.asList(State.Paused));
			if (pausedCalls.size() == 1) {
				pause.setBackgroundResource(R.drawable.pause_on);
			} else {
				pause.setBackgroundResource(R.drawable.pause_off);
			}
		}

	}

	private void enableAndRefreshInCallActions() {
		addCall.setEnabled(LinphoneManager.getLc().getCallsNb() < LinphoneManager.getLc().getMaxCalls());
		transfer.setEnabled(getResources().getBoolean(R.bool.allow_transfers));
		options.setEnabled(!getResources().getBoolean(R.bool.disable_options_in_call) && (addCall.isEnabled() || transfer.isEnabled()));

		if(LinphoneManager.getLc().getCurrentCall() != null && LinphonePreferences.instance().isVideoEnabled() && !LinphoneManager.getLc().getCurrentCall().mediaInProgress()) {
			video.setEnabled(true);
		}
		micro.setEnabled(true);
		speaker.setEnabled(true);

		transfer.setEnabled(true);
		pause.setEnabled(true);
		dialer.setEnabled(true);
		conference.setEnabled(true);

		refreshInCallActions();
	}

	public void updateStatusFragment(StatusFragment statusFragment) {
		status = statusFragment;
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		if(isRTTMaximized){
			hideRTTinterface();
			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
			isRTTMaximized = false;
			mControlsLayout.setVisibility(View.VISIBLE);
		}


		if (id == R.id.video) {
			toggleCamera_mute();
		}
		else if (id == R.id.micro) {
			toggleMicro();
		}
		else if (id == R.id.speaker) {
			toggleSpeaker(!isSpeakerMuted);

		}
		else if (id == R.id.addCall) {
			goBackToDialer();
		}
		else if (id == R.id.toggleChat) {
			if(isRTTEnabled) {
				toggle_chat();
			}
			else{
				Toast.makeText(InCallActivity.this, "RTT has been disabled for this call", Toast.LENGTH_SHORT).show();
			}
		}

		else if (id == R.id.hangUp) {
			hangUp();
		}
		else if (id == R.id.dialer) {
			hideOrDisplayNumpad();
		}
		else if (id == R.id.conference) {
			enterConference();
		}
		else if (id == R.id.switchCamera) {
			if (videoCallFragment != null) {
				videoCallFragment.switchCamera();
			}
		}
		else if (id == R.id.transfer) {
			goBackToDialerAndDisplayTransferButton();
		}
		else if (id == R.id.options) {
			hideOrDisplayCallOptions();
		}
		else if (id == R.id.audioRoute) {
			hideOrDisplayAudioRoutes();
		}
		else if (id == R.id.routeBluetooth) {
			if (BluetoothManager.getInstance().routeAudioToBluetooth()) {
				isSpeakerMuted = true;
				routeBluetooth.setBackgroundResource(R.drawable.route_bluetooth_on);
				routeReceiver.setBackgroundResource(R.drawable.route_receiver_off);
				routeSpeaker.setBackgroundResource(R.drawable.route_speaker_off);
			}
			hideOrDisplayAudioRoutes();
		}
		else if (id == R.id.routeReceiver) {
			LinphoneManager.getInstance().routeAudioToReceiver();
			isSpeakerMuted = true;
			routeBluetooth.setBackgroundResource(R.drawable.route_bluetooth_off);
			routeReceiver.setBackgroundResource(R.drawable.route_receiver_on);
			routeSpeaker.setBackgroundResource(R.drawable.route_speaker_off);
			hideOrDisplayAudioRoutes();
		}
		else if (id == R.id.routeSpeaker) {
			LinphoneManager.getInstance().routeAudioToSpeaker();
			routeBluetooth.setBackgroundResource(R.drawable.route_bluetooth_off);
			routeReceiver.setBackgroundResource(R.drawable.route_receiver_off);
			routeSpeaker.setBackgroundResource(R.drawable.route_speaker_on);
			hideOrDisplayAudioRoutes();
		}

		else if (id == R.id.callStatus) {
			LinphoneCall call = (LinphoneCall) v.getTag();
			pauseOrResumeCall(call);
		}
		else if (id == R.id.conferenceStatus) {
			pauseOrResumeConference();
		}
	}

	public void toggle_chat() {
		Log.d("RTT", "toggleChat clicked");
		Log.d("RTT", "isRTTMaximaized" + isRTTMaximized);
		mControlsLayout.setVisibility(View.GONE);

		if(isRTTMaximized){
			hideRTTinterface();
		} else{
			Log.d("rttOutgoingBubbleCount"+rttOutgoingBubbleCount);
//			if(rttOutgoingBubbleCount==0){
//				create_new_outgoing_bubble(null, true);
//			}
			showRTTinterface();
			outgoingEditText.requestFocus();
			((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
					.showSoftInput(outgoingEditText, InputMethodManager.SHOW_FORCED);
		}

	}
	public void hideRTTinterface(){
		if(rttHolder!=null) {
			rttHolder.setVisibility(View.GONE);
			isRTTMaximized=false;
			mControlsLayout.setVisibility(View.VISIBLE);
		}
	}

	public void toggleCamera_mute(){
		if (isCameraMuted==true) {
			video.setBackgroundResource(R.drawable.video_on);

			LinphoneInfoMessage message = LinphoneManager.getLc().createInfoMessage();
			message.addHeader("action", "isCameraMuted");
			final LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
			call.sendInfoMessage(message);

			//This line remains for other platforms. To force the video to unfreeze.
			LinphoneManager.getLc().setPreviewWindow(VideoCallFragment.mCaptureView);
			isCameraMuted=false;
		} else if (isCameraMuted==false){
			video.setBackgroundResource(R.drawable.video_off);

			LinphoneInfoMessage message = LinphoneManager.getLc().createInfoMessage();
			message.addHeader("action", "camera_mute_off");
			final LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
			call.sendInfoMessage(message);

			//This line remains for other platforms. To force the video to freeze.
			LinphoneManager.getLc().setPreviewWindow(null);
			isCameraMuted=true;

		}
	}

	public void displayCustomToast(final String message, final int duration) {
		LayoutInflater inflater = getLayoutInflater();
		View layout = inflater.inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toastRoot));

		TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
		toastText.setText(message);

		final Toast toast = new Toast(getApplicationContext());
		toast.setGravity(Gravity.CENTER, 0, 0);
		toast.setDuration(duration);
		toast.setView(layout);
		toast.show();
	}

	private void switchVideo(final boolean displayVideo) {
		final LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		if (call == null) {
			return;
		}

		//Check if the call is not terminated
		if(call.getState() == State.CallEnd || call.getState() == State.CallReleased) return;

		if (!displayVideo) {
			showAudioView();
		} else {
			if (!call.getRemoteParams().isLowBandwidthEnabled()) {
				LinphoneManager.getInstance().addVideo();
				if (videoCallFragment == null || !videoCallFragment.isVisible())
					showVideoView();
			} else {
				displayCustomToast(getString(R.string.error_low_bandwidth), Toast.LENGTH_LONG);
			}
		}
	}

	private void showAudioView() {
		video.setBackgroundResource(R.drawable.video_on);
		LinphoneManager.startProximitySensorForActivity(InCallActivity.this);
		replaceFragmentVideoByAudio();
		setCallControlsVisibleAndRemoveCallbacks();
	}

	private void showVideoView() {



			if (!BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
				Log.w("Bluetooth not available, using speaker");
				LinphoneManager.getInstance().routeAudioToSpeaker();
				speaker.setBackgroundResource(R.drawable.speaker_on);
			}
			video.setBackgroundResource(R.drawable.video_off);
			video.setEnabled(true);
			videoProgress.setVisibility(View.INVISIBLE);

			LinphoneManager.stopProximitySensorForActivity(InCallActivity.this);
			replaceFragmentAudioByVideo();
			displayVideoCallControlsIfHidden(SECONDS_BEFORE_HIDING_CONTROLS);

	}

	private void replaceFragmentVideoByAudio() {
		audioCallFragment = new AudioCallFragment();

		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.replace(R.id.fragmentContainer, audioCallFragment);
		try {
			transaction.commitAllowingStateLoss();
		} catch (Exception e) {
		}
	}

	private void replaceFragmentAudioByVideo() {
//		Hiding controls to let displayVideoCallControlsIfHidden add them plus the callback
		mControlsLayout.setVisibility(View.GONE);
		switchCamera.setVisibility(View.INVISIBLE);

		videoCallFragment = new VideoCallFragment();

		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.replace(R.id.fragmentContainer, videoCallFragment);
		try {
			transaction.commitAllowingStateLoss();
		} catch (Exception e) {
		}
	}

	private void toggleMicro() {
		LinphoneCore lc = LinphoneManager.getLc();
		isMicMuted = !isMicMuted;
		lc.muteMic(isMicMuted);
		if (isMicMuted) {
			micro.setBackgroundResource(R.drawable.micro_off);
		} else {
			micro.setBackgroundResource(R.drawable.micro_on);
		}
	}

	private void toggleSpeaker(boolean isMuted) {
		final float mute_db = -1000.0f;
		isSpeakerMuted = isMuted;
		if (isSpeakerMuted) {
			LinphoneManager.getLc().setPlaybackGain(mute_db);
			speaker.setBackgroundResource(R.drawable.speaker_off);
		} else {
			LinphoneManager.getLc().setPlaybackGain(0);
			speaker.setBackgroundResource(R.drawable.speaker_on);
		}
	}

	private void pauseOrResumeCall() {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null && lc.getCallsNb() >= 1) {
			LinphoneCall call = lc.getCalls()[0];
			pauseOrResumeCall(call);
		}
	}

	public void pauseOrResumeCall(LinphoneCall call) {
		LinphoneCore lc = LinphoneManager.getLc();
		if (call != null && LinphoneUtils.isCallRunning(call)) {
			if (call.isInConference()) {
				lc.removeFromConference(call);
				if (lc.getConferenceSize() <= 1) {
					lc.leaveConference();
				}
			} else {
				lc.pauseCall(call);
				if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
					isVideoCallPaused = true;
					showAudioView();
				}
				pause.setBackgroundResource(R.drawable.pause_on);
			}
		} else if (call != null) {
			if (call.getState() == State.Paused) {
				lc.resumeCall(call);
				if (isVideoCallPaused) {
					isVideoCallPaused = false;
					showVideoView();
				}
				pause.setBackgroundResource(R.drawable.pause_off);
			}
		}
	}

	private void hangUp() {
		LinphoneCore lc = LinphoneManager.getLc();
		LinphoneCall currentCall = lc.getCurrentCall();

		if (currentCall != null) {
			lc.terminateCall(currentCall);
		} else if (lc.isInConference()) {
			lc.terminateConference();
		} else {
			lc.terminateAllCalls();
		}
		delete_messages();

	}

	private void enterConference() {
		LinphoneManager.getLc().addAllToConference();
	}

	public void pauseOrResumeConference() {
		LinphoneCore lc = LinphoneManager.getLc();
		if (lc.isInConference()) {
			lc.leaveConference();
		} else {
			lc.enterConference();
		}
	}

	public void displayVideoCallControlsIfHidden(int delay_until_hide) {
		if (mControlsLayout != null) {
			if (mControlsLayout.getVisibility() != View.VISIBLE) {
				if (isAnimationDisabled) {
					mControlsLayout.setVisibility(View.VISIBLE);
					callsList.setVisibility(showCallListInVideo ? View.VISIBLE : View.GONE);
					if (cameraNumber > 1) {
						switchCamera.setVisibility(View.VISIBLE);
					}
				} else {
					Animation animation = slideInBottomToTop;
					animation.setAnimationListener(new AnimationListener() {
						@Override
						public void onAnimationStart(Animation animation) {
							animating_show_controls=true;
							mControlsLayout.setVisibility(View.VISIBLE);
							callsList.setVisibility(showCallListInVideo ? View.VISIBLE : View.GONE);
							if (cameraNumber > 1) {
								switchCamera.setVisibility(View.VISIBLE);
							}
						}

						@Override
						public void onAnimationRepeat(Animation animation) {
						}

						@Override
						public void onAnimationEnd(Animation animation) {
							animation.setAnimationListener(null);
							animating_show_controls=false;

						}
					});
					mControlsLayout.startAnimation(animation);
					if (cameraNumber > 1) {
						switchCamera.startAnimation(slideInTopToBottom);
					}
				}
			}
			hide_controls(delay_until_hide);
		}
	}

	public void hide_controls(int delay_until_hide) {
		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;

		if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall()) && mControlsHandler != null) {
			if(delay_until_hide!=NEVER) {
				mControlsHandler.postDelayed(mControls = new Runnable() {
					public void run() {
						hideNumpad();

						if (isAnimationDisabled) {
							transfer.setVisibility(View.INVISIBLE);
							addCall.setVisibility(View.INVISIBLE);
							mControlsLayout.setVisibility(View.GONE);
							callsList.setVisibility(View.GONE);
							switchCamera.setVisibility(View.INVISIBLE);
							numpad.setVisibility(View.GONE);
							options.setBackgroundResource(R.drawable.options);
						} else {
							Animation animation = slideOutTopToBottom;
							animation.setAnimationListener(new AnimationListener() {
								@Override
								public void onAnimationStart(Animation animation) {
									video.setEnabled(false); // HACK: Used to avoid controls from being hided if video is switched while controls are hiding
								}

								@Override
								public void onAnimationRepeat(Animation animation) {
								}

								@Override
								public void onAnimationEnd(Animation animation) {
									video.setEnabled(true); // HACK: Used to avoid controls from being hided if video is switched while controls are hiding
									transfer.setVisibility(View.INVISIBLE);
									addCall.setVisibility(View.INVISIBLE);
									mControlsLayout.setVisibility(View.GONE);
									callsList.setVisibility(View.GONE);
									switchCamera.setVisibility(View.INVISIBLE);
									numpad.setVisibility(View.GONE);
									options.setBackgroundResource(R.drawable.options);

									animation.setAnimationListener(null);
								}
							});
							mControlsLayout.startAnimation(animation);
							if (cameraNumber > 1) {
								switchCamera.startAnimation(slideOutBottomToTop);
							}
						}
					}
				}, delay_until_hide);
			}
		}
	}

	public void setCallControlsVisibleAndRemoveCallbacks() {
		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;

		mControlsLayout.setVisibility(View.VISIBLE);
		callsList.setVisibility(View.VISIBLE);
		switchCamera.setVisibility(View.INVISIBLE);
	}

	private void hideNumpad() {
		if (numpad == null || numpad.getVisibility() != View.VISIBLE) {
			return;
		}

		dialer.setBackgroundResource(R.drawable.dialer_alt);
		if (isAnimationDisabled) {
			numpad.setVisibility(View.GONE);
		} else {
			Animation animation = slideOutTopToBottom;
			animation.setAnimationListener(new AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {

				}

				@Override
				public void onAnimationRepeat(Animation animation) {

				}

				@Override
				public void onAnimationEnd(Animation animation) {
					numpad.setVisibility(View.GONE);
					animation.setAnimationListener(null);
				}
			});
			numpad.startAnimation(animation);
		}
	}

	private void hideOrDisplayNumpad() {
		if (numpad == null) {
			return;
		}

		if (numpad.getVisibility() == View.VISIBLE) {
			hideNumpad();
		} else {
			dialer.setBackgroundResource(R.drawable.dialer_alt_back);
			if (isAnimationDisabled) {
				numpad.setVisibility(View.VISIBLE);
			} else {
				Animation animation = slideInBottomToTop;
				animation.setAnimationListener(new AnimationListener() {
					@Override
					public void onAnimationStart(Animation animation) {

					}

					@Override
					public void onAnimationRepeat(Animation animation) {

					}

					@Override
					public void onAnimationEnd(Animation animation) {
						numpad.setVisibility(View.VISIBLE);
						animation.setAnimationListener(null);
					}
				});
				numpad.startAnimation(animation);
			}
		}
	}

	private void hideAnimatedPortraitCallOptions() {
		Animation animation = slideOutLeftToRight;
		animation.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
			}

			@Override
			public void onAnimationRepeat(Animation animation) {
			}

			@Override
			public void onAnimationEnd(Animation animation) {
				if (isTransferAllowed) {
					transfer.setVisibility(View.INVISIBLE);
				}
				addCall.setVisibility(View.INVISIBLE);
				animation.setAnimationListener(null);
			}
		});
		if (isTransferAllowed) {
			transfer.startAnimation(animation);
		}
		addCall.startAnimation(animation);
	}

	private void hideAnimatedLandscapeCallOptions() {
		Animation animation = slideOutTopToBottom;
		if (isTransferAllowed) {
			animation.setAnimationListener(new AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
				}

				@Override
				public void onAnimationRepeat(Animation animation) {
				}

				@Override
				public void onAnimationEnd(Animation animation) {
					transfer.setAnimation(null);
					transfer.setVisibility(View.INVISIBLE);
					animation = AnimationUtils.loadAnimation(InCallActivity.this, R.anim.slide_out_top_to_bottom); // Reload animation to prevent transfer button to blink
					animation.setAnimationListener(new AnimationListener() {
						@Override
						public void onAnimationStart(Animation animation) {
						}

						@Override
						public void onAnimationRepeat(Animation animation) {
						}

						@Override
						public void onAnimationEnd(Animation animation) {
							addCall.setVisibility(View.INVISIBLE);
						}
					});
					addCall.startAnimation(animation);
				}
			});
			transfer.startAnimation(animation);
		} else {
			animation.setAnimationListener(new AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
				}

				@Override
				public void onAnimationRepeat(Animation animation) {
				}

				@Override
				public void onAnimationEnd(Animation animation) {
					addCall.setVisibility(View.INVISIBLE);
				}
			});
			addCall.startAnimation(animation);
		}
	}

	private void showAnimatedPortraitCallOptions() {
		Animation animation = slideInRightToLeft;
		animation.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
			}

			@Override
			public void onAnimationRepeat(Animation animation) {
			}

			@Override
			public void onAnimationEnd(Animation animation) {
				options.setBackgroundResource(R.drawable.options_alt);
				if (isTransferAllowed) {
					transfer.setVisibility(View.VISIBLE);
				}
				addCall.setVisibility(View.VISIBLE);
				animation.setAnimationListener(null);
			}
		});
		if (isTransferAllowed) {
			transfer.startAnimation(animation);
		}
		addCall.startAnimation(animation);
	}

	private void showAnimatedLandscapeCallOptions() {
		Animation animation = slideInBottomToTop;
		animation.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
			}

			@Override
			public void onAnimationRepeat(Animation animation) {
			}

			@Override
			public void onAnimationEnd(Animation animation) {
				addCall.setAnimation(null);
				options.setBackgroundResource(R.drawable.options_alt);
				addCall.setVisibility(View.VISIBLE);
				if (isTransferAllowed) {
					animation.setAnimationListener(new AnimationListener() {
						@Override
						public void onAnimationStart(Animation animation) {
						}

						@Override
						public void onAnimationRepeat(Animation animation) {
						}

						@Override
						public void onAnimationEnd(Animation animation) {
							transfer.setVisibility(View.VISIBLE);
						}
					});
					transfer.startAnimation(animation);
				}
			}
		});
		addCall.startAnimation(animation);
	}

	private void hideOrDisplayAudioRoutes()
	{
		if (routeSpeaker.getVisibility() == View.VISIBLE) {
			routeSpeaker.setVisibility(View.GONE);
			routeBluetooth.setVisibility(View.GONE);
			routeReceiver.setVisibility(View.GONE);
			audioRoute.setSelected(false);
		} else {
			routeSpeaker.setVisibility(View.VISIBLE);
			routeBluetooth.setVisibility(View.VISIBLE);
			routeReceiver.setVisibility(View.VISIBLE);
			audioRoute.setSelected(true);
		}
	}

	private void hideOrDisplayCallOptions() {
		boolean isOrientationLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

		if (addCall.getVisibility() == View.VISIBLE) {
			options.setBackgroundResource(R.drawable.options);
			if (isAnimationDisabled) {
				if (isTransferAllowed) {
					transfer.setVisibility(View.INVISIBLE);
				}
				addCall.setVisibility(View.INVISIBLE);
			} else {
				if (isOrientationLandscape) {
					hideAnimatedLandscapeCallOptions();
				} else {
					hideAnimatedPortraitCallOptions();
				}
			}
			options.setSelected(false);
		} else {
			if (isAnimationDisabled) {
				if (isTransferAllowed) {
					transfer.setVisibility(View.VISIBLE);
				}
				addCall.setVisibility(View.VISIBLE);
				options.setBackgroundResource(R.drawable.options_alt);
			} else {
				if (isOrientationLandscape) {
					showAnimatedLandscapeCallOptions();
				} else {
					showAnimatedPortraitCallOptions();
				}
			}
			options.setSelected(true);
			transfer.setEnabled(LinphoneManager.getLc().getCurrentCall() != null);
		}
	}

	public void goBackToDialer() {
		Intent intent = new Intent();
		intent.putExtra("Transfer", false);
		setResult(Activity.RESULT_FIRST_USER, intent);
		finish();
	}

	private void goBackToDialerAndDisplayTransferButton() {
		Intent intent = new Intent();
		intent.putExtra("Transfer", true);
		setResult(Activity.RESULT_FIRST_USER, intent);
		finish();
	}

	public void acceptCallUpdate(boolean accept) {
		if (timer != null) {
			timer.cancel();
		}

		LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		if (call == null) {
			return;
		}

		LinphoneCallParams params = call.getCurrentParamsCopy();
		if (accept) {
			params.setVideoEnabled(true);
			LinphoneManager.getLc().enableVideo(true, true);
		}

		try {
			LinphoneManager.getLc().acceptCallUpdate(call, params);
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
	}

	public void startIncomingCallActivity() {
		startActivity(new Intent(this, IncomingCallActivity.class));
	}



	private void showAcceptCallUpdateDialog() {
		FragmentManager fm = getSupportFragmentManager();
		callUpdateDialog = new AcceptCallUpdateDialogFragment();
		callUpdateDialog.show(fm, "Accept Call Update Dialog");
	}

	@Override
	protected void onResume() {
//		try {
//			populate_messages();
//		}catch(Throwable e){
//			//No messages to populate
//			e.printStackTrace();
//		}
		Log.d("onResume()");
		instance = this;

		if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {

			displayVideoCallControlsIfHidden(SECONDS_BEFORE_HIDING_CONTROLS);
		} else {
			LinphoneManager.startProximitySensorForActivity(this);
			setCallControlsVisibleAndRemoveCallbacks();
		}

		super.onResume();

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}

		refreshCallList(getResources());

		handleViewIntent();

		toggleSpeaker(isSpeakerMuted);

		IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
		registerReceiver(myReceiver, filter);
	}

	private void handleViewIntent() {
		Intent intent = getIntent();
		if(intent != null && intent.getAction() == "android.intent.action.VIEW") {
			LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
			if(call != null && isVideoEnabled(call)) {
				LinphonePlayer player = call.getPlayer();
				String path = intent.getData().getPath();
				Log.i("Openning " + path);
				int openRes = player.open(path, new LinphonePlayer.Listener() {

					@Override
					public void endOfFile(LinphonePlayer player) {
						player.close();
					}
				});
				if(openRes == -1) {
					String message = "Could not open " + path;
					Log.e(message);
					Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
					return;
				}
				Log.i("Start playing");
				if(player.start() == -1) {
					player.close();
					String message = "Could not start playing " + path;
					Log.e(message);
					Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
				}
			}
		}
	}

	@Override
	protected void onPause() {
		Log.d("onPause()");
		unregisterReceiver(myReceiver);
		save_messages();

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}

		super.onPause();

		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;


		if (!isVideoEnabled(LinphoneManager.getLc().getCurrentCall())) {
			LinphoneManager.stopProximitySensorForActivity(this);
		}
	}

	@Override
	protected void onDestroy() {
		Log.d("onDestroy()");
		LinphoneManager.getInstance().changeStatusToOnline();

		if (mControlsHandler != null && mControls != null) {
			mControlsHandler.removeCallbacks(mControls);
		}
		mControls = null;
		mControlsHandler = null;

		unbindDrawables(findViewById(R.id.topLayout));
		instance = null;
		super.onDestroy();
		System.gc();
	}

	private void unbindDrawables(View view) {
		if (view.getBackground() != null) {
			view.getBackground().setCallback(null);
		}
		if (view instanceof ImageView) {
			view.setOnClickListener(null);
		}
		if (view instanceof ViewGroup && !(view instanceof AdapterView)) {
			for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
				unbindDrawables(((ViewGroup) view).getChildAt(i));
			}
			((ViewGroup) view).removeAllViews();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (LinphoneUtils.onKeyVolumeAdjust(keyCode)) return true;
		if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) return true;
		return super.onKeyDown(keyCode, event);
	}

	public void bindAudioFragment(AudioCallFragment fragment) {
		audioCallFragment = fragment;
	}

	public void bindVideoFragment(VideoCallFragment fragment) {
		videoCallFragment = fragment;
	}

	private void displayConferenceHeader() {
		LinearLayout conferenceHeader = (LinearLayout) inflater.inflate(R.layout.conference_header, container, false);

		ImageView conferenceState = (ImageView) conferenceHeader.findViewById(R.id.conferenceStatus);
		conferenceState.setOnClickListener(this);
		if (LinphoneManager.getLc().isInConference()) {
			conferenceState.setImageResource(R.drawable.play);
		} else {
			conferenceState.setImageResource(R.drawable.pause);
		}

		callsList.addView(conferenceHeader);
	}

	private void displayCall(Resources resources, LinphoneCall call, int index) {
		String sipUri = call.getRemoteAddress().asStringUriOnly();
		LinphoneAddress lAddress;
		try {
			lAddress = LinphoneCoreFactory.instance().createLinphoneAddress(sipUri);
		} catch (LinphoneCoreException e) {
			Log.e("Incall activity cannot parse remote address",e);
			lAddress= LinphoneCoreFactory.instance().createLinphoneAddress("uknown","unknown","unkonown");
		}

		boolean hide_additional_info = showCallListInVideo && isVideoEnabled(LinphoneManager.getLc().getCurrentCall());
		// Control Row and Image Row
		LinearLayout callView = (LinearLayout) inflater.inflate(R.layout.active_call_control_row, container, false);
		LinearLayout imageView = (LinearLayout) inflater.inflate(R.layout.active_call_image_row, container, false);
		//callView.setId(index+1);

		setContactName(imageView, lAddress, sipUri, resources);
		displayCallStatusIconAndReturnCallPaused(callView, imageView, call);
		if(!hide_additional_info)
			setRowBackground(callView, index);
		registerCallDurationTimer(callView, call);
		callsList.addView(callView);

		if(!hide_additional_info) {
			contact = ContactsManager.getInstance().findContactWithAddress(imageView.getContext().getContentResolver(), lAddress);
			if (contact != null) {
				displayOrHideContactPicture(imageView, contact.getPhotoUri(), contact.getThumbnailUri(), false);
			} else {
				displayOrHideContactPicture(imageView, null, null, false);
			}
			callsList.addView(imageView);

			callView.setTag(imageView);
			callView.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					if (v.getTag() != null) {
						View imageView = (View) v.getTag();
						if (imageView.getVisibility() == View.VISIBLE)
							imageView.setVisibility(View.GONE);
						else
							imageView.setVisibility(View.VISIBLE);
						callsList.invalidate();
					}
				}
			});
		}
	}

	private void setContactName(LinearLayout callView, LinphoneAddress lAddress, String sipUri, Resources resources) {
		TextView contact = (TextView) callView.findViewById(R.id.contactNameOrNumber);
		//TextView partnerName = (TextView) findViewById(R.id.partner_name);
		//TextView userName = (TextView) findViewById(R.id.user_name);
		LinphonePreferences mPrefs = LinphonePreferences.instance();
		String username = mPrefs.getAccountUsername(mPrefs.getDefaultAccountIndex());
		//userName.setText(username);

		Contact lContact  = ContactsManager.getInstance().findContactWithAddress(callView.getContext().getContentResolver(), lAddress);
		if (lContact == null) {
			if (resources.getBoolean(R.bool.only_display_username_if_unknown) && LinphoneUtils.isSipAddress(sipUri)) {
				contact.setText(lAddress.getUserName());
				contactName = lAddress.getUserName();
				android.util.Log.e("Info", "contactName = " + contactName);
				//partnerName.setText(contactName);
			} else {
				contact.setText(sipUri);
				contactName = sipUri;
				android.util.Log.e("Info", "contactName = " + contactName);
				// partnerName.setText(contactName);
			}
		} else {
			contact.setText(lContact.getName());
			contactName = lContact.getName();
			android.util.Log.e("Info", "contactName = " + contactName);
			//partnerName.setText(contactName);
		}
	}

	private void startOutgoingRingCount(LinearLayout callView) {
		outgoingRingCountTimer = new Timer();
		float outGoingRingDuration = LinphonePreferences.instance().getConfig().getFloat("vtcsecure", "outgoing_ring_duration", 2.0f);
		final TextView outgoingRingCountTextView = (TextView) callView.findViewById(R.id.outboundRingCount);
		outgoingRingCountTextView.setVisibility(View.VISIBLE);
		outgoingRingCountTimer.schedule(new TimerTask() {
			int ringCount = 0;
			@Override
			public void run() {
				InCallActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						ringCount++;
						outgoingRingCountTextView.setText(ringCount+"");
					}
				});
			}
		}, 0, (long)(outGoingRingDuration*1000));
	}

	private void stopOutgoingRingCount() {
		if (outgoingRingCountTimer != null) {
			outgoingRingCountTimer.cancel();
			outgoingRingCountTimer = null;
			findViewById(R.id.outboundRingCount).setVisibility(View.GONE);
		}
	}

	private boolean displayCallStatusIconAndReturnCallPaused(LinearLayout callView, LinearLayout  imageView,  LinphoneCall call) {
		boolean isCallPaused, isInConference;
		ImageView callState = (ImageView) callView.findViewById(R.id.callStatus);
		callState.setTag(call);
		callState.setOnClickListener(this);

		if (call.getState() == State.Paused || call.getState() == State.PausedByRemote || call.getState() == State.Pausing) {
			callState.setImageResource(R.drawable.pause);
			isCallPaused = true;
			isInConference = false;
			stopOutgoingRingCount();
		} else if (call.getState() == State.OutgoingInit || call.getState() == State.OutgoingProgress || call.getState() == State.OutgoingRinging) {
			callState.setImageResource(R.drawable.call_state_ringing_default);
			isCallPaused = false;
			isInConference = false;
			if (call.getState() == State.OutgoingRinging) startOutgoingRingCount(imageView);
			else stopOutgoingRingCount();
		} else {
			stopOutgoingRingCount();
			if (isConferenceRunning && call.isInConference()) {
				callState.setImageResource(R.drawable.remove);
				isInConference = true;
			} else {
				callState.setImageResource(R.drawable.play);
				isInConference = false;
			}
			isCallPaused = false;
		}

		return isCallPaused || isInConference;
	}

	private void displayOrHideContactPicture(LinearLayout callView, Uri pictureUri, Uri thumbnailUri, boolean hide) {
		String rawContactId = null;
		try{
			rawContactId = ContactsManager.getInstance().findRawContactID(LinphoneActivity.instance().getContentResolver(), String.valueOf(contact.getID()));
		}catch(Throwable e){

			e.printStackTrace();
		}

		AvatarWithShadow contactPicture = (AvatarWithShadow) callView.findViewById(R.id.contactPicture);
		if (pictureUri != null) {
			LinphoneUtils.setImagePictureFromUri(callView.getContext(), contactPicture.getView(), Uri.parse(pictureUri.toString()), thumbnailUri, R.drawable.unknown_small);
		}else if(rawContactId!=null&&ContactsManager.picture_exists_in_storage_for_contact(rawContactId)){
			contactPicture.getView().setImageBitmap(ContactsManager.get_bitmap_by_contact_resource_id(rawContactId));
		}

		callView.setVisibility(hide ? View.GONE : View.VISIBLE);
	}

	private void setRowBackground(LinearLayout callView, int index) {
		int backgroundResource;
		if (index == 0) {
//			backgroundResource = active ? R.drawable.cell_call_first_highlight : R.drawable.cell_call_first;
			backgroundResource = R.drawable.cell_call_first;
		} else {
//			backgroundResource = active ? R.drawable.cell_call_highlight : R.drawable.cell_call;
			backgroundResource = R.drawable.cell_call;
		}
		callView.setBackgroundResource(backgroundResource);
	}

	private void registerCallDurationTimer(View v, LinphoneCall call) {
		int callDuration = call.getDuration();
		if (callDuration == 0 && call.getState() != State.StreamsRunning) {
			return;
		}

		Chronometer timer = (Chronometer) v.findViewById(R.id.callTimer);
		if (timer == null) {
			throw new IllegalArgumentException("no callee_duration view found");
		}

		timer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
		timer.start();
	}

	public void refreshCallList(Resources resources) {
		if (callsList == null) {
			return;
		}

		callsList.removeAllViews();
		int index = 0;

		if (LinphoneManager.getLc().getCallsNb() == 0) {
			goBackToDialer();
			return;
		}

		isConferenceRunning = LinphoneManager.getLc().getConferenceSize() > 1;
		if (isConferenceRunning) {
			displayConferenceHeader();
			index++;
		}
		for (LinphoneCall call : LinphoneManager.getLc().getCalls()) {
			displayCall(resources, call, index);
			index++;
		}

		if(LinphoneManager.getLc().getCurrentCall() == null){
			showAudioView();
			video.setEnabled(false);
		}

		callsList.invalidate();
	}
	private class HeadPhoneJackIntentReceiver extends BroadcastReceiver {
		@Override public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
				int state = intent.getIntExtra("state", -1);
				switch (state) {
					case 0:
						Log.d("HEADPHONES", "Headset is unplugged");

						break;
					case 1:
						Log.d("HEADPHONES", "Headset is plugged");
						LinphoneManager.getInstance().routeAudioToReceiver();
						break;
					default:
						Log.d("HEADPHONES", "I have no idea what the headset state is");
				}
			}
		}
	}
}
