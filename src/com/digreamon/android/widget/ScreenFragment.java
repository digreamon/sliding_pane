package com.digreamon.android.widget;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class ScreenFragment extends Fragment {
	public final static String EXTRA_TITLE = ScreenFragment.class.getName()+"EXTRA_TITLE";
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		String title = getArguments().getString(EXTRA_TITLE);
		View rootView = inflater.inflate(R.layout.screen_layout, container, false);
		((TextView)rootView.findViewById(R.id.title)).setText(title);
		return rootView;
	}
}
