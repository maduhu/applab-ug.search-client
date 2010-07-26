/**
 * Copyright (C) 2010 Grameen Foundation
Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
 */

package yo.applab.ckwinfo;

import java.util.ArrayList;
import java.util.Vector;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

/**
 *Displays a list view of the contents of the inbox.
 * 
 */
public class InboxListActivity extends ListActivity {
	private final String LOG_TAG = "InboxListActivity";
	private InboxAdapter inbox;
	private KeywordDownloader keywordDownloader;
	private KeywordParser keywordParser;
	public Storage searchDatabase;
	private AlertDialog alertDialog;
	private ListView listView;
	private String activityTitle;
	private Thread network;

	/** holds list view index - database row ID pairs */
	private Vector<Index> Indices = new Vector<Index>();

	/** dialog shown during database initialization */
	private static final int PARSE_DIALOG = 1;

	/** dialog shown when accessing network resources */
	private static final int CONNECT_DIALOG = 2;

	private ProgressDialog progressDialog;

	/** set true when the inbox is empty */
	private boolean empty = false;

	SynchronizeTask synchronizeTask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Cursor cursor;
		super.onCreate(savedInstanceState);
		Bundle extras = this.getIntent().getExtras();
		boolean block = true;
		if (extras != null) {
			block = extras.getBoolean("block", true);
		}
		this.synchronizeTask = new SynchronizeTask(this.connectHandle, this
				.getApplicationContext());
		this.inbox = new InboxAdapter(this);
		ArrayList<String> results = new ArrayList<String>();
		this.inbox.open();
		cursor = this.inbox.fetchAllRecords();
		startManagingCursor(cursor);
		// inbox.close();
		this.activityTitle = getString(R.string.inbox_title) + "("
				+ cursor.getCount() + ")";
		// If we're coming from the home screen do not ask for id confirmation
		if (block) {
			accessDialog().show();
		} else {
			showCurrentUser();
		}

		if (cursor.moveToFirst()) {
			String elipses = "";
			long rowId, Id = 0;
			while (!cursor.isAfterLast()) {
				int titleColumn = cursor
						.getColumnIndexOrThrow(InboxAdapter.KEY_TITLE);
				int idColumn = cursor
						.getColumnIndexOrThrow(InboxAdapter.KEY_ROWID);
				int statusColumn = cursor
						.getColumnIndexOrThrow(InboxAdapter.KEY_STATUS);

				String title = cursor.getString(titleColumn);
				String status = cursor.getString(statusColumn);
				rowId = cursor.getLong(idColumn);

				if (title.length() > 50) {
					elipses = "...";
					title = title.substring(0, 50);
				}
				if (status.contentEquals("Incomplete")) {
					results.add(title + elipses + "\n[Incomplete...]");
				} else {
					results.add(title + elipses);
				}
				cursor.moveToNext();
				Index myIndex = new Index();
				myIndex.rowId = rowId;
				myIndex.id = Id;
				Indices.addElement(myIndex);
				++Id;
			}

			cursor.close();
			setListAdapter(new ArrayAdapter<String>(this, R.layout.list_inbox,
					results));
		} else {
			results.add(getString(R.string.inbox_empty));
			setListAdapter(new ArrayAdapter<String>(this, R.layout.list_inbox,
					results));
			empty = true;
		}
		listView = getListView();

		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (!empty) {

					Intent i = new Intent(view.getContext(),
							DisplaySearchResultsActivity.class);

					for (int j = 0; j < Indices.size(); j++) {
						Index myIndex = Indices.elementAt(j);
						if (myIndex.id == id) {
							i.putExtra("rowId", myIndex.rowId);
						}

					}
					startActivity(i);
					finish();
				}

			}
		});

	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case CONNECT_DIALOG:
			progressDialog = new ProgressDialog(this);
			progressDialog.setMessage(getString(R.string.progress_msg));
			progressDialog.setIndeterminate(true);
			progressDialog.setCancelable(false);
			return progressDialog;
		case PARSE_DIALOG:
			progressDialog = new ProgressDialog(this);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setMessage(getString(R.string.parse_msg));
			progressDialog.setCancelable(false);
			return progressDialog;
		}
		return null;
	}

	/**
	 * updates database initialization progress dialog
	 */
	final Handler progressHandler = new Handler() {
		public void handleMessage(Message msg) {
			int level = msg.getData().getInt("node");
			progressDialog.setProgress(level);
		}
	};

	/**
	 * handles responses from network layer
	 */
	public Handler connectHandle = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Global.CONNECTION_ERROR:
				dismissDialog(CONNECT_DIALOG);
				errorDialog().show();
				break;
			case Global.CONNECTION_SUCCESS:
				dismissDialog(CONNECT_DIALOG);
				if (Global.data.trim().endsWith("</Keywords>")) {
					showDialog(PARSE_DIALOG);
					Thread parser = new Thread(keywordParser);
					parser.start();
				} else {
					errorDialog().show();
				}
				break;
			case Global.KEYWORD_PARSE_SUCCESS:
				// Release synchronization lock
				KeywordSynchronizer.completeSynchronization();
				dismissDialog(PARSE_DIALOG);
				Toast.makeText(getApplicationContext(),
						getString(R.string.refreshed), Toast.LENGTH_LONG)
						.show();
				break;
			case Global.KEYWORD_PARSE_ERROR:
				dismissDialog(PARSE_DIALOG);
				errorDialog().show();
				break;
			}
		}
	};

	/**
	 * Creates a dialog for connection error notifications with buttons to opt
	 * out or retry a failed connection
	 * 
	 * @return an error notification alert dialog
	 */
	public AlertDialog errorDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.connection_error).setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						// Release synchronization lock
						KeywordSynchronizer.completeSynchronization();
						dialog.cancel();
					}
				}).setNegativeButton("Retry",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
								synchronizeTask.updateKeywords();
							}
						});
		AlertDialog alert = builder.create();
		return alert;
	}

	/**
	 * creates a dialog that confirms user credentials before accessing the
	 * inbox
	 * 
	 * @return a confirmation alert dialog
	 */
	public AlertDialog accessDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		final EditText mEditText = new EditText(this);
		if (Global.intervieweeName != null)
			mEditText.setText(Global.intervieweeName);
		InputFilter filter = new InputFilter() {
			public CharSequence filter(CharSequence source, int start, int end,
					Spanned dest, int dstart, int dend) {
				for (int i = start; i < end; i++) {
					if (!(((Character.isWhitespace(source.charAt(i)))) || Character
							.isLetterOrDigit(source.charAt(i)))) {
						Toast.makeText(getApplicationContext(),
								getString(R.string.invalid_text),
								Toast.LENGTH_SHORT).show();
						return "";
					}
				}
				return null;
			}
		};
		mEditText.setFilters(new InputFilter[] { filter });
		builder.setMessage(getString(R.string.confirm_id)).setCancelable(false)
				.setPositiveButton(getString(R.string.confirm_button),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
								Global.intervieweeName = mEditText.getText()
										.toString().trim();
								showCurrentUser();
							}
						}).setNegativeButton(getString(R.string.cancel_button),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
								Intent l = new Intent(getApplicationContext(),
										MainMenuActivity.class);
								startActivity(l);
								finish();
							}
						}).setView(mEditText);
		AlertDialog alert = builder.create();
		return alert;
	}

	/**
	 * obtains and displays the currently set user ID in title bar
	 */
	private void showCurrentUser() {
		this.activityTitle = this.activityTitle.concat(" | ");
		if (Global.intervieweeName.length() > 30) {
			this.activityTitle = this.activityTitle
					.concat(Global.intervieweeName.substring(0, 30));
			this.activityTitle = this.activityTitle.concat("...");
		} else {
			this.activityTitle = this.activityTitle
					.concat(Global.intervieweeName);
		}
		setTitle(this.activityTitle);
	}

	/**
	 * keywordParser error alert dialog builder.
	 * 
	 * @return A dialog.
	 */
	public AlertDialog xmlDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Error: Malformed XML").setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		AlertDialog alert = builder.create();
		return alert;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(1, Global.RESET_ID, 1, getString(R.string.menu_reset))
				.setIcon(R.drawable.search);
		menu.add(1, Global.REFRESH_ID, 2, getString(R.string.menu_refresh))
				.setIcon(R.drawable.refresh);
		menu.add(0, Global.ABOUT_ID, 4, getString(R.string.menu_about))
				.setIcon(R.drawable.about);
		menu.add(0, Global.EXIT_ID, 5, getString(R.string.menu_exit)).setIcon(
				R.drawable.exit);
		menu.add(0, Global.DELETE_ID, 0, getString(R.string.menu_delete))
				.setIcon(R.drawable.delete);
		menu.add(0, Global.HOME_ID, 0, getString(R.string.menu_home)).setIcon(
				R.drawable.home);

		return result;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean result = super.onPrepareOptionsMenu(menu);
		// Disable keyword updates and new searches
		if (KeywordSynchronizer.isSynchronizing()) {
			menu.setGroupEnabled(1, false);
		}
		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case Global.RESET_ID:
			if (!KeywordSynchronizer.isSynchronizing()) {
				// Get synchronization lock
				if (KeywordSynchronizer.tryStartSynchronization()) {
					Intent i = new Intent(getApplicationContext(),
							SearchActivity.class);
					if (searchDatabase != null)
						searchDatabase.close();
					startActivity(i);
					finish();
				} else {
					Log.i(LOG_TAG, "Failed to get synchronization lock");
				}
			}
			return true;
		case Global.HOME_ID:
			Intent l = new Intent(getApplicationContext(),
					MainMenuActivity.class);

			if (searchDatabase != null)
				searchDatabase.close();
			startActivity(l);
			finish();
			return true;
		case Global.REFRESH_ID:
			if (!KeywordSynchronizer.isSynchronizing()) {
				// Get synchronization lock
				if (KeywordSynchronizer.tryStartSynchronization()) {
					keywordDownloader = new KeywordDownloader(
							this.connectHandle);
					keywordParser = new KeywordParser(this
							.getApplicationContext(), progressHandler,
							connectHandle);
					SynchronizeTask synchronizeTask = new SynchronizeTask(
							this.connectHandle, this.getApplicationContext());
					showDialog(CONNECT_DIALOG);
					synchronizeTask.updateKeywords();
				} else {
					Log.i(LOG_TAG, "Failed to get synchronization lock");
				}
			}
			return true;
		case Global.ABOUT_ID:
			Intent k = new Intent(getApplicationContext(), AboutActivity.class);

			if (searchDatabase != null)
				searchDatabase.close();
			startActivity(k);
			return true;
		case Global.EXIT_ID:
			if (searchDatabase != null)
				searchDatabase.close();
			this.finish();
			return true;
		case Global.DELETE_ID:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(getString(R.string.delete_alert)).setCancelable(
					false).setPositiveButton("Yes",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							inbox
									.deleteAllRecords(InboxAdapter.INBOX_DATABASE_TABLE);

							dialog.cancel();
							Intent j = new Intent(getApplicationContext(),
									InboxListActivity.class);

							if (searchDatabase != null)
								searchDatabase.close();
							startActivity(j);
							finish();

						}
					}).setNegativeButton("No",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
						}
					});
			alertDialog = builder.create();
			alertDialog.show();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/** Object for list view index - database row ID pairs */
	private class Index {
		public long id;
		public long rowId;
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		if (progressDialog != null) {
			progressDialog.dismiss();
		}
	}
}
