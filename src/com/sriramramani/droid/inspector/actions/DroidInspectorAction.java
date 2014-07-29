/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.sriramramani.droid.inspector.actions;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.ide.IDE;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.TimeoutException;
import com.android.ide.eclipse.ddms.IClientAction;
import com.sriramramani.droid.inspector.DroidInspectorPlugin;
import com.sriramramani.droid.inspector.client.DroidClient;

public class DroidInspectorAction extends Action implements IClientAction {
    private static final int DEFAULT_SERVER_PORT = 4545;
    private Client mClient;

    public DroidInspectorAction() {
        setText("Droid Inspector");
        setToolTipText("Inspect the view layers");
        setImageDescriptor(DroidInspectorPlugin.getImageDescriptor("icons/icon.gif"));
    }

    @Override
    public Action getAction() {
        return this;
    }

    @Override
    public void run() {
        if (mClient == null) {
            return;
        }

        final Job job = new DroidInspectorJob();
        job.schedule();
    }

    private final class DroidInspectorJob extends Job {
        public DroidInspectorJob() {
            super("Droid Inspector");
            setUser(true);
        }

        @Override
        protected IStatus run(IProgressMonitor monitor) {
            DroidClient client = new DroidClient();

            if (monitor == null) {
                monitor = new NullProgressMonitor();
            }

            monitor.beginTask("Collecting view dump.", 100);
            final IDevice device = mClient.getDevice();
            try {
                device.createForward(DroidClient.DEFAULT_LOCAL_PORT, DEFAULT_SERVER_PORT);
                monitor.worked(20);

                File file = File.createTempFile("dix_", ".dix");
                client.printData(file);
                monitor.worked(80);

                openFile(file);

            } catch (TimeoutException e) {
                showError("Timeout Error", "Seems like the window is out of focus. Waited 15 seconds to collect the view dump, but in vain.");
            } catch (AdbCommandRejectedException e) {
                showError("ADB Error", "ADB is unable to create port forwarding. Ensure ADBHOST is enabled in preferences");
            } catch (IOException e) {
                showError("File Error", "Unable to write file onto disk. Super encrypted file sytem huh?");
            } finally {
                monitor.done();
            }
            return Status.OK_STATUS;
        }
    }

    @Override
    public void selectedClientChanged(Client client) {
        if (client != null) {
            setEnabled(true);
            mClient = client;
        } else {
            setEnabled(false);
            if (mClient != null) {
                try {
                    final IDevice device = mClient.getDevice();
                    if (device != null) {
                        device.removeForward(DroidClient.DEFAULT_LOCAL_PORT, DEFAULT_SERVER_PORT);
                    }
                } catch (Exception e) {
                    // Don't care if forward can't be removed.
                }
            }
            mClient = null;
        }
    }

    private void showError(String title, String message) {
        MessageBox box = new MessageBox(new Shell(Display.getCurrent()), SWT.ERROR);
        box.setText(title);
        box.setMessage(message);
        box.open();
    }

    private void openFile(File file) {
        final IFileStore fileStore =  EFS.getLocalFileSystem().getStore(file.toURI());
        if (!fileStore.fetchInfo().exists()) {
            return;
        }

        final IWorkbench workbench = PlatformUI.getWorkbench();
        workbench.getDisplay().syncExec(new Runnable() {
            @Override
            public void run() {
                IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
                if (window == null) {
                    return;
                }

                IWorkbenchPage page = window.getActivePage();
                if (page == null) {
                    return;
                }

                // Try to switch perspectives if possible
                try {
                    workbench.showPerspective("org.eclipse.jdt.ui.JavaPerspective", window); //$NON-NLS-1$
                } catch (WorkbenchException e) {
                }

                try {
                    IDE.openEditorOnFileStore(page, fileStore);
                } catch (PartInitException e) {
                    return;
                }
            }
        });
    }
}
