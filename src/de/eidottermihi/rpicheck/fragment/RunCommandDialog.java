package de.eidottermihi.rpicheck.fragment;

import java.io.File;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.common.base.Strings;

import de.eidottermihi.rpicheck.R;
import de.eidottermihi.rpicheck.activity.NewRaspiAuthActivity;
import de.eidottermihi.rpicheck.db.CommandBean;
import de.eidottermihi.rpicheck.db.RaspberryDeviceBean;
import de.eidottermihi.rpicheck.ssh.RaspiQuery;
import de.eidottermihi.rpicheck.ssh.RaspiQueryException;

public class RunCommandDialog extends DialogFragment {

	private boolean didRun = false;

	RaspberryDeviceBean device;
	CommandBean command;
	String passphrase;
	static TextView consoleOutput;

	// Need handler for callbacks to the UI thread
	final Handler mHandler = new Handler();

	// Create runnable for posting
	final Runnable mRunFinished = new Runnable() {
		public void run() {
			// gets called from AsyncTask when task is finished
		}
	};

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(
				getActivity());
		this.device = (RaspberryDeviceBean) this.getArguments()
				.getSerializable("pi");
		this.command = (CommandBean) this.getArguments().getSerializable("cmd");
		if (this.getArguments().getString("passphrase") != null) {
			this.passphrase = this.getArguments().getString("passphrase");
		}

		builder.setTitle("Running " + this.command.getName());
		builder.setIcon(R.drawable.device_access_accounts);
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				// just closing the dialog
			}
		});
		final LayoutInflater inflater = getActivity().getLayoutInflater();
		final View view = inflater.inflate(R.layout.dialog_command_run, null);
		builder.setView(view);
		consoleOutput = (TextView) view.findViewById(R.id.runCommandOutput);
		consoleOutput.setMovementMethod(new ScrollingMovementMethod());
		if (savedInstanceState != null) {
			this.didRun = savedInstanceState.getBoolean("didRun", false);
		}
		return builder.create();
	}

	@Override
	public void onStart() {
		super.onStart();
		if (this.didRun == false) {
			// run command
			this.runCommand();
			this.didRun = true;
		}
	}

	private void runCommand() {
		consoleOutput
				.setText("Connecting to host " + device.getHost() + " ...");
		// get connection settings from shared preferences
		final String host = device.getHost();
		final String user = device.getUser();
		final String port = device.getPort() + "";
		final String sudoPass = device.getSudoPass();
		if (device.getAuthMethod().equals(
				NewRaspiAuthActivity.SPINNER_AUTH_METHODS[0])) {
			// ssh password
			putLine("Authenticating with password ...");
			final String pass = device.getPass();
			new SSHCommandTask().execute(host, user, pass, port, sudoPass,
					null, null, command.getCommand());
		} else if (device.getAuthMethod().equals(
				NewRaspiAuthActivity.SPINNER_AUTH_METHODS[1])) {
			putLine("Authenticating with private key ...");
			// keyfile
			final String keyfilePath = device.getKeyfilePath();
			if (keyfilePath != null) {
				final File privateKey = new File(keyfilePath);
				if (privateKey.exists()) {
					new SSHCommandTask().execute(host, user, null, port,
							sudoPass, keyfilePath, null, command.getCommand());
				} else {
					putLine("ERROR - No keyfile was specified." + keyfilePath);
				}
			} else {
				putLine("ERROR - No keyfile was specified.");
			}
		} else if (device.getAuthMethod().equals(
				NewRaspiAuthActivity.SPINNER_AUTH_METHODS[2])) {
			putLine("Authenticating with private key and passphrase ...");
			// keyfile and passphrase
			final String keyfilePath = device.getKeyfilePath();
			if (keyfilePath != null) {
				final File privateKey = new File(keyfilePath);
				if (privateKey.exists()) {
					if (!Strings.isNullOrEmpty(this.passphrase)) {
						new SSHCommandTask().execute(host, user, null, port,
								sudoPass, keyfilePath, this.passphrase,
								command.getCommand());
					} else {
						putLine("ERROR - No passphrase specified.");
					}
				} else {
					putLine("ERROR - Cannot find keyfile at location: "
							+ keyfilePath);
				}
			} else {
				putLine("ERROR - No keyfile was specified.");
			}
		}
	}

	private static void putLine(String text) {
		consoleOutput.append("\n" + text);
	}

	private class SSHCommandTask extends AsyncTask<String, String, Boolean> {

		private RaspiQuery raspiQuery;

		@Override
		protected Boolean doInBackground(String... params) {
			// create and do query
			raspiQuery = new RaspiQuery((String) params[0], (String) params[1],
					Integer.parseInt(params[3]));
			final String pass = params[2];
			final String sudoPass = params[4];
			final String privateKeyPath = params[5];
			final String privateKeyPass = params[6];
			final String command = params[7];
			try {
				if (privateKeyPath != null) {
					File f = new File(privateKeyPath);
					if (privateKeyPass == null) {
						// connect with private key only
						raspiQuery.connectWithPubKeyAuth(f.getPath());
					} else {
						// connect with key and passphrase
						raspiQuery.connectWithPubKeyAuthAndPassphrase(
								f.getPath(), privateKeyPass);
					}
				} else {
					raspiQuery.connect(pass);
				}
				publishProgress("Connection established.");
				String output = raspiQuery.run(command);
				publishProgress(output);
				raspiQuery.disconnect();
				publishProgress("Connection closed.");
			} catch (RaspiQueryException e) {
				publishProgress("ERROR - " + e.getMessage());
				if (e.getCause() != null) {
					publishProgress("Reason: " + e.getCause().getMessage());
				}
				return false;
			}
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			// inform handler
			mHandler.post(mRunFinished);
		}

		@Override
		protected void onProgressUpdate(String... values) {
			final String feedback = values[0];
			putLine(feedback);
			super.onProgressUpdate(values);
		}

	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean("didRun", this.didRun);

	}

}
