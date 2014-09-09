package com.odoo.addons.mail;

import java.util.ArrayList;
import java.util.List;

import odoo.Odoo;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;
import android.widgets.SwipeRefreshLayout.OnRefreshListener;

import com.odoo.App;
import com.odoo.addons.mail.models.MailGroup;
import com.odoo.addons.mail.providers.group.MailGroupProvider;
import com.odoo.support.AppScope;
import com.odoo.support.fragment.AsyncTaskListener;
import com.odoo.support.fragment.BaseFragment;
import com.odoo.support.fragment.SyncStatusObserverListener;
import com.odoo.support.listview.OCursorListAdapter;
import com.odoo.support.listview.OCursorListAdapter.OnRowViewClickListener;
import com.odoo.support.listview.OCursorListAdapter.OnViewBindListener;
import com.odoo.util.OControls;
import com.odoo.util.drawer.DrawerItem;
import com.openerp.R;

public class GroupsLoader extends BaseFragment implements
		LoaderCallbacks<Cursor>, OnRefreshListener, SyncStatusObserverListener,
		OnItemClickListener, OnRowViewClickListener, OnViewBindListener {
	public static final String TAG = "com.odoo.addons.mail.GroupsLoader";
	private App mApp = null;
	private Context mContext = null;
	private OCursorListAdapter mAdapter;
	private ListView groupList = null;
	public static final String KEY = "group_id";
	private Odoo odoo = null;

	// private JoinUnfollowGroup mJoinUnfollowGroup = null;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mContext = getActivity();
		setHasOptionsMenu(true);
		setHasSyncStatusObserver(TAG, this, db());
		mApp = (App) getActivity().getApplicationContext();
		if (mApp.inNetwork())
			odoo = mApp.getOdoo();
		scope = new AppScope(mContext);
		return inflater.inflate(R.layout.groups, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		setHasSwipeRefreshView(view, R.id.swipe_container, this);
		groupList = (ListView) view.findViewById(R.id.listGroups);
		mAdapter = new OCursorListAdapter(getActivity(), null,
				R.layout.groups_list_item);
		mAdapter.setOnViewBindListener(this);
		groupList.setOnItemClickListener(this);
		mAdapter.setOnRowViewClickListener(R.id.btnJoinGroup, this);
		mAdapter.setOnRowViewClickListener(R.id.btnUnJoinGroup, this);
		groupList.setAdapter(mAdapter);
		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public Object databaseHelper(Context context) {
		return new MailGroup(context);
	}

	@Override
	public List<DrawerItem> drawerMenus(Context context) {
		List<DrawerItem> menu = new ArrayList<DrawerItem>();
		menu.add(new DrawerItem(TAG, "My Groups", true));
		menu.add(new DrawerItem(TAG, "Groups", 0,
				R.drawable.ic_action_social_group, object("group")));
		return menu;
	}

	private Fragment object(String value) {
		GroupsLoader group = new GroupsLoader();
		Bundle bundle = new Bundle();
		bundle.putString("group", value);
		group.setArguments(bundle);
		return group;
	}

	@Override
	public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
		if (db().isEmptyTable()) {
			scope.main().requestSync(MailGroupProvider.AUTHORITY);
			setSwipeRefreshing(true);
		}
		return new CursorLoader(mContext, db().uri(), db().projection(), null,
				null, null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
		mAdapter.changeCursor(cursor);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> arg0) {
		mAdapter.changeCursor(null);
	}

	@Override
	public void onRefresh() {
		if (app().inNetwork()) {
			scope.main().requestSync(MailGroupProvider.AUTHORITY);
		} else {
			hideRefreshingProgress();
			Toast.makeText(getActivity(), _s(R.string.no_connection),
					Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void onStatusChange(Boolean refreshing) {
		if (!refreshing)
			hideRefreshingProgress();
		else
			setSwipeRefreshing(true);
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View view, int position,
			long arg3) {
		Cursor cr = mAdapter.getCursor();
		cr.moveToPosition(position);
		Integer has_joined = cr.getInt(cr.getColumnIndex("has_followed"));
		if (has_joined == 1) {
			Mail mail = new Mail();
			Bundle bundle = new Bundle();
			bundle.putInt(KEY, cr.getInt(cr.getColumnIndex("id")));
			bundle.putString(Mail.KEY, Mail.Type.Group.toString());
			mail.setArguments(bundle);
			startFragment(mail, true);
		} else {
			Toast.makeText(getActivity(), _s(R.string.first_join_group),
					Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void onRowViewClick(int position, Cursor cursor, View view,
			final View parent) {
		final int group_id = cursor.getInt(cursor.getColumnIndex("id"));
		final MailGroup group = new MailGroup(getActivity());
		if (inNetwork()) {
			switch (view.getId()) {
			case R.id.btnJoinGroup:
				newBackgroundTask(new AsyncTaskListener() {

					@Override
					public Object onPerformTask() {
						group.followUnfollowGroup(group_id, true);
						return null;
					}

					@Override
					public void onFinish(Object result) {
						Toast.makeText(getActivity(),
								_s(R.string.toast_group_followed),
								Toast.LENGTH_LONG).show();
						OControls.setGone(parent, R.id.btnJoinGroup);
						OControls.setVisible(parent, R.id.btnUnJoinGroup);
					}
				}).execute();

				break;
			case R.id.btnUnJoinGroup:
				newBackgroundTask(new AsyncTaskListener() {

					@Override
					public Object onPerformTask() {
						group.followUnfollowGroup(group_id, false);
						return null;
					}

					@Override
					public void onFinish(Object result) {
						Toast.makeText(getActivity(),
								_s(R.string.toast_group_unfollowed),
								Toast.LENGTH_LONG).show();
						OControls.setGone(parent, R.id.btnUnJoinGroup);
						OControls.setVisible(parent, R.id.btnJoinGroup);
					}
				}).execute();
				break;

			default:
				break;
			}
		} else {
			Toast.makeText(getActivity(), _s(R.string.no_connection),
					Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void onViewBind(View view, Cursor cursor) {
		int followed = cursor.getInt(cursor.getColumnIndex("has_followed"));
		view.findViewById(R.id.btnJoinGroup).setVisibility(
				(followed == 1) ? View.GONE : View.VISIBLE);
		view.findViewById(R.id.btnUnJoinGroup).setVisibility(
				(followed == 0) ? View.GONE : View.VISIBLE);
	}
}
