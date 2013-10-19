/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.sriramramani.droid.inspector.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class DroidClient {
    private static final String LOCAL_HOST = "127.0.0.1";
    public static final int DEFAULT_LOCAL_PORT = 5555;

    public static final String COMMAND_PRINT_HIERARCHY = "print";

    public void printData(File file) throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.connect(new InetSocketAddress(LOCAL_HOST, DEFAULT_LOCAL_PORT));
        channel.socket().setSoTimeout(15000);

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(channel.socket().getOutputStream()));
        writer.write(COMMAND_PRINT_HIERARCHY);
        writer.newLine();
        writer.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(channel.socket().getInputStream()), 8 * 1024);
        OutputStreamWriter output = new OutputStreamWriter(new FileOutputStream(file));

        String line = null;
        while ((line = reader.readLine()) != null) {
            output.write(line);
        }

        writer.close();
        output.close();
        reader.close();
        channel.close();
    }
}
