package com.digreamon.android.widget;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class MainActivity extends FragmentActivity {

	private String[] mMenuTitles = {"Item 1", "Item 2", "Item 3", "Item 4", "Item 5"};
    private ListView leftMenuList;
    private ListView rightMenuList;
    private SlidingPaneLayout mSlidingPaneLayout;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.sliding_pane_layout);
		leftMenuList = (ListView)findViewById(R.id.left_drawer_list);
		rightMenuList = (ListView)findViewById(R.id.right_drawer_list);
		mSlidingPaneLayout = (SlidingPaneLayout)findViewById(R.id.sliding_pane_layout);
		
		leftMenuList.setAdapter(new ArrayAdapter<String>(this,
                R.layout.menu_item_layout, R.id.title, mMenuTitles));
		leftMenuList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapter, View view, int index, long id) {
				String title = "<left> "+adapter.getItemAtPosition(index).toString();
				Toast.makeText(MainActivity.this, title, Toast.LENGTH_LONG).show();
				setScreen(title);
				mSlidingPaneLayout.smoothSlideClosed();
			}
		});
        
		rightMenuList.setAdapter(new ArrayAdapter<String>(this,
                R.layout.menu_item_layout, R.id.title, mMenuTitles));
		rightMenuList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapter, View view, int index, long id) {
				String title = "<right> "+adapter.getItemAtPosition(index).toString();
				Toast.makeText(MainActivity.this, title, Toast.LENGTH_LONG).show();
				setScreen(title);
				mSlidingPaneLayout.smoothSlideClosed();
			}
		});
		
        mSlidingPaneLayout.setLeftShadowResource(R.drawable.pane_shadow_r2l);
        mSlidingPaneLayout.setRightShadowResource(R.drawable.pane_shadow_l2r);
        
        setScreen("test");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private void setScreen(String title){
		Fragment fragment = new ScreenFragment();
        Bundle args = new Bundle();
        args.putString(ScreenFragment.EXTRA_TITLE, title);
        fragment.setArguments(args);

        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_container, fragment).commit();
	}
}
